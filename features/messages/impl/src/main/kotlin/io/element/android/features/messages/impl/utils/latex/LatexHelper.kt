/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils.latex

import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.URLSpan
import androidx.core.text.getSpans
import io.element.android.wysiwyg.view.spans.CodeBlockSpan
import io.element.android.wysiwyg.view.spans.InlineCodeSpan
import org.jsoup.nodes.Document

object LatexHelper {
    private val BLOCK_MATH_REGEX = Regex(
        """(?:\$\$(.+?)\$\$|(\\begin\{(?:align\*?|aligned|equation\*?|gather\*?)\}[\s\S]*?\\end\{(?:align\*?|aligned|equation\*?|gather\*?)\}))""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Match $...$ where it doesn't look like currency ($ followed by digit) and has content
    private val INLINE_MATH_REGEX = Regex("""(?<!\$)\$(?!\$)(?!\d+[\s.,])([^\$\n]+?)(?<!\$)\$(?!\$)""")

    sealed interface TextSegment {
        data class Text(val text: CharSequence) : TextSegment
        data class BlockMath(val formula: String) : TextSegment
    }

    /**
     * Normalizes LaTeX environment syntax for JLatexMath compatibility
     * (e.g. mapping document-level \begin{align*} and \begin{align} to \begin{aligned}).
     */
    fun cleanFormula(raw: String): String {
        var clean = raw.trim()
        clean = clean
            .replace(Regex("""\\begin\{\s*align\*?\s*\}"""), """\\begin{aligned}""")
            .replace(Regex("""\\end\{\s*align\*?\s*\}"""), """\\end{aligned}""")
            .replace(Regex("""\\begin\{\s*gather\*?\s*\}"""), """\\begin{gathered}""")
            .replace(Regex("""\\end\{\s*gather\*?\s*\}"""), """\\end{gathered}""")
            .replace(Regex("""\\begin\{\s*equation\*?\s*\}"""), "")
            .replace(Regex("""\\end\{\s*equation\*?\s*\}"""), "")
        return clean.trim()
    }

    /**
     * Checks whether an inline LaTeX formula contains structures with high vertical dimension
     * (such as fractions, matrices, cases, multi-level limits/sums/integrals) that should
     * be promoted to a standalone block formula rather than squashed in inline text.
     */
    fun isComplexOrTallFormula(rawFormula: String): Boolean {
        val trimmed = rawFormula.trim()
        if (trimmed.contains("\\frac") || trimmed.contains("\\dfrac") ||
            trimmed.contains("\\cfrac") || trimmed.contains("\\over") ||
            trimmed.contains("\\matrix") || trimmed.contains("\\pmatrix") ||
            trimmed.contains("\\bmatrix") || trimmed.contains("\\vmatrix") ||
            trimmed.contains("\\cases") || trimmed.contains("\\begin") ||
            Regex("""\\(sum|prod|coprod|int|iint|iiint|oint)\s*[_^]""").containsMatchIn(trimmed)
        ) {
            return true
        }
        return false
    }

    /**
     * Splits [text] into sequential [TextSegment]s separating text and block formulas ($$...$$).
     * This allows rendering block formulas inside Compose horizontal-scrolling containers
     * while keeping standard text inside [EditorStyledText].
     */
    fun splitByBlockMath(text: CharSequence): List<TextSegment> {
        val matches = BLOCK_MATH_REGEX.findAll(text).toList()
        if (matches.isEmpty()) {
            return listOf(TextSegment.Text(text))
        }

        val segments = mutableListOf<TextSegment>()
        var lastIndex = 0

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > lastIndex) {
                val sub = text.subSequence(lastIndex, start).trimEndNewlines()
                if (sub.isNotBlank()) {
                    segments.add(TextSegment.Text(sub))
                }
            }

            val formula = (match.groups[1]?.value ?: match.groups[2]?.value).orEmpty().trim()
            if (formula.isNotEmpty()) {
                segments.add(TextSegment.BlockMath(formula))
            }

            lastIndex = end
        }

        if (lastIndex < text.length) {
            val sub = text.subSequence(lastIndex, text.length).trimStartNewlines()
            if (sub.isNotBlank()) {
                segments.add(TextSegment.Text(sub))
            }
        }

        return segments.ifEmpty { listOf(TextSegment.Text(text)) }
    }

    private fun CharSequence.trimEndNewlines(): CharSequence {
        var end = length
        while (end > 0 && (this[end - 1] == '\n' || this[end - 1] == '\r' || this[end - 1] == ' ')) {
            end--
        }
        return if (end == length) this else subSequence(0, end)
    }

    private fun CharSequence.trimStartNewlines(): CharSequence {
        var start = 0
        while (start < length && (this[start] == '\n' || this[start] == '\r' || this[start] == ' ')) {
            start++
        }
        return if (start == 0) this else subSequence(start, length)
    }

    /**
     * Preprocesses HTML Document to extract `data-mx-maths` attributes (MSC2191 / Element Web format)
     * and normalize them into standard LaTeX delimiters for downstream span processing.
     * Replaces span/div elements directly with text nodes or paragraphs so that parsers that drop
     * unknown tags (like wysiwyg HtmlToSpansParser) do not discard formula content.
     */
    fun preprocessHtmlDocument(document: Document) {
        val mathElements = document.select("[data-mx-maths]")
        for (el in mathElements) {
            val rawFormula = el.attr("data-mx-maths").trim()
            val formula = org.jsoup.parser.Parser.unescapeEntities(rawFormula, false)
            if (formula.isNotEmpty()) {
                val isBlock = el.tagName().equals("div", ignoreCase = true) ||
                    el.tagName().equals("math", ignoreCase = true)
                val replacementText = if (isBlock) "\$\$$formula\$\$" else "\$$formula\$"
                el.replaceWith(org.jsoup.nodes.TextNode(replacementText))
            }
        }
    }

    /**
     * Finds LaTeX math blocks and inline formulas in [text] and applies [LatexFormulaSpan].
     * Preserves raw LaTeX in underlying text so that range selection and copying returns the exact LaTeX code.
     */
    fun renderLatexSpans(text: CharSequence): CharSequence {
        val spannable = if (text is Spannable) text else SpannableStringBuilder(text)

        // 1. Process Block Math ($$...$$)
        for (match in BLOCK_MATH_REGEX.findAll(spannable)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (!spannable.canApplyMathSpan(start, end)) continue

            val formula = (match.groups[1]?.value ?: match.groups[2]?.value)?.trim() ?: continue
            val span = LatexFormulaSpan(rawLatex = formula, isBlock = true)
            spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val fullFormula = if (match.groups[1] != null) "\$\$$formula\$\$" else formula
            spannable.setSpan(URLSpan("latex://${Uri.encode(fullFormula)}"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // 2. Process Inline Math ($...$)
        for (match in INLINE_MATH_REGEX.findAll(spannable)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (!spannable.canApplyMathSpan(start, end)) continue

            val formula = match.groupValues[1]
            val span = LatexFormulaSpan(rawLatex = formula, isBlock = false)
            spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val fullFormula = "\$$formula\$"
            spannable.setSpan(URLSpan("latex://${Uri.encode(fullFormula)}"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return spannable
    }

    /**
     * Removes [LatexFormulaSpan] and formula [URLSpan] instances from [text] if present,
     * reverting the text to its raw text representation without custom formula rendering.
     */
    fun removeLatexSpans(text: CharSequence): CharSequence {
        if (text !is Spanned) return text
        val spannable = SpannableStringBuilder(text)
        val formulaSpans = spannable.getSpans<LatexFormulaSpan>()
        for (span in formulaSpans) {
            spannable.removeSpan(span)
        }
        val urlSpans = spannable.getSpans<URLSpan>()
        for (span in urlSpans) {
            if (span.url?.startsWith("latex://") == true) {
                spannable.removeSpan(span)
            }
        }
        return spannable
    }

    private fun Spanned.canApplyMathSpan(start: Int, end: Int): Boolean {
        if (getSpans<CodeBlockSpan>(start, end).isNotEmpty()) return false
        if (getSpans<InlineCodeSpan>(start, end).isNotEmpty()) return false
        if (getSpans<LatexFormulaSpan>(start, end).isNotEmpty()) return false
        return true
    }

    private val COMPOSITE_PARSER_REGEX = Regex(
        """(```[\s\S]*?```)|(`[^`\n]+`)|(\$\$(.+?)\$\$)|((?<!\$)\$(?!\$)(?!\d+[\s.,])([^\$\n]+?)(?<!\$)\$(?!\$))"""
    )

    /**
     * Checks whether [text] contains any unescaped LaTeX formula outside code blocks.
     */
    fun hasLatexFormulas(text: String): Boolean {
        return COMPOSITE_PARSER_REGEX.findAll(text).any { match ->
            match.groups[3] != null || match.groups[5] != null
        }
    }

    /**
     * Converts LaTeX math formulas ($...$ or $$...$$) in [markdown] into MSC2191 compliant HTML
     * format (<span data-mx-maths="..."><code>...</code></span> or <div data-mx-maths="...">...).
     * This ensures that Element Web, Element Desktop, and other Matrix clients correctly render the LaTeX math.
     *
     * @param markdown The raw markdown or plain text of the composer message.
     * @param existingHtml Pre-existing HTML (e.g. from rich text editor), or null if plain text.
     * @return Formatted HTML string containing MSC2191 tags, or [existingHtml] if no formula found,
     *         or null if neither [existingHtml] nor formulas exist.
     */
    fun formatLatexToHtml(markdown: String, existingHtml: String? = null): String? {
        if (!hasLatexFormulas(markdown)) {
            return existingHtml
        }

        return if (existingHtml != null) {
            enrichHtmlWithLatex(existingHtml)
        } else {
            convertMarkdownLatexToHtml(markdown)
        }
    }

    private fun convertMarkdownLatexToHtml(markdown: String): String {
        val sb = java.lang.StringBuilder()
        var lastIndex = 0

        for (match in COMPOSITE_PARSER_REGEX.findAll(markdown)) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > lastIndex) {
                val plain = markdown.substring(lastIndex, start)
                sb.append(escapeHtmlText(plain).replace("\n", "<br>"))
            }

            when {
                match.groups[1] != null -> {
                    // Code block
                    val raw = match.value.removePrefix("```").removeSuffix("```").trim('\n')
                    sb.append("<pre><code>").append(escapeHtmlText(raw)).append("</code></pre>")
                }
                match.groups[2] != null -> {
                    // Inline code
                    val raw = match.value.removePrefix("`").removeSuffix("`")
                    sb.append("<code>").append(escapeHtmlText(raw)).append("</code>")
                }
                match.groups[3] != null -> {
                    // Block math: $$formula$$
                    val formula = match.groups[4]?.value.orEmpty()
                    sb.append("<div data-mx-maths=\"${escapeHtmlAttr(formula)}\"><pre><code>${escapeHtmlText(formula)}</code></pre></div>")
                }
                match.groups[5] != null -> {
                    // Inline math: $formula$
                    val formula = match.groups[6]?.value.orEmpty()
                    sb.append("<span data-mx-maths=\"${escapeHtmlAttr(formula)}\"><code>${escapeHtmlText(formula)}</code></span>")
                }
            }

            lastIndex = end
        }

        if (lastIndex < markdown.length) {
            val plain = markdown.substring(lastIndex)
            sb.append(escapeHtmlText(plain).replace("\n", "<br>"))
        }

        return sb.toString()
    }

    private fun enrichHtmlWithLatex(existingHtml: String): String {
        val doc = org.jsoup.Jsoup.parseBodyFragment(existingHtml)
        doc.outputSettings().prettyPrint(false).indentAmount(0)
        val textNodes = mutableListOf<org.jsoup.nodes.TextNode>()
        doc.traverse(object : org.jsoup.select.NodeVisitor {
            override fun head(node: org.jsoup.nodes.Node, depth: Int) {
                if (node is org.jsoup.nodes.TextNode) {
                    val parentName = node.parent()?.tagName()?.lowercase()
                    if (parentName != "code" && parentName != "pre" && parentName != "math" && parentName != "span") {
                        textNodes.add(node)
                    }
                }
            }
            override fun tail(node: org.jsoup.nodes.Node, depth: Int) {}
        })

        var modified = false
        for (node in textNodes) {
            val text = node.wholeText
            if (!hasLatexFormulas(text)) continue

            val fragmentHtml = convertMarkdownLatexToHtml(text)
            val tempDoc = org.jsoup.Jsoup.parseBodyFragment(fragmentHtml)
            val replacementNodes = tempDoc.body().childNodes().toList()
            if (replacementNodes.isNotEmpty()) {
                val parent = node.parent() ?: continue
                val index = node.siblingIndex()
                parent.insertChildren(index, replacementNodes)
                node.remove()
                modified = true
            }
        }

        return if (modified) doc.body().html() else existingHtml
    }

    private fun escapeHtmlText(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun escapeHtmlAttr(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
