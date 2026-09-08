/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object HtmlToMessageAstParser {
    private const val MAX_DEPTH = 8
    private const val MAX_TABLE_ROWS = 60
    private const val MAX_TABLE_COLS = 25

    private val ALLOWED_SCHEMES = setOf("https", "http", "matrix", "mailto")

    // Match inline $formula$ that is not a currency sign
    private val INLINE_MATH_REGEX = Regex("""(?<!\$)\$(?!\$)(?!\d+[\s.,])([^\$\n]+?)(?<!\$)\$(?!\$)""")

    fun parse(document: Document): List<MessageBlock> {
        val clone = document.clone()
        // Strip <mx-reply> so replies don't display twice in the timeline
        clone.select("mx-reply").remove()

        val body = clone.body() ?: return emptyList()
        return parseBlockNodes(body.childNodes(), depth = 0)
    }

    fun parse(html: String): List<MessageBlock> {
        val doc = Jsoup.parseBodyFragment(html)
        return parse(doc)
    }

    private fun parseBlockNodes(nodes: List<Node>, depth: Int): List<MessageBlock> {
        if (depth > MAX_DEPTH) {
            // Flatten to simple paragraph if recursion limit is exceeded
            val inlineNodes = nodes.flatMap { parseInlineNode(it) }
            return if (inlineNodes.isNotEmpty()) listOf(MessageBlock.Paragraph(inlineNodes)) else emptyList()
        }

        val blocks = mutableListOf<MessageBlock>()
        val pendingInlines = mutableListOf<InlineNode>()

        fun flushPendingInlines() {
            if (pendingInlines.isNotEmpty()) {
                blocks.add(MessageBlock.Paragraph(pendingInlines.toList()))
                pendingInlines.clear()
            }
        }

        for (node in nodes) {
            if (node is Element) {
                val tag = node.tagName().lowercase()

                // Check for block math via data-mx-maths (MSC2191)
                if (node.hasAttr("data-mx-maths") && (tag == "div" || tag == "math")) {
                    flushPendingInlines()
                    val formula = node.attr("data-mx-maths").trim()
                    if (formula.isNotEmpty()) {
                        blocks.add(MessageBlock.BlockMath(formula))
                    }
                    continue
                }

                when (tag) {
                    "p" -> {
                        flushPendingInlines()
                        val children = parseInlineChildren(node)
                        if (children.isNotEmpty()) {
                            blocks.add(MessageBlock.Paragraph(children))
                        }
                    }
                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        flushPendingInlines()
                        val level = tag.removePrefix("h").toIntOrNull() ?: 1
                        val children = parseInlineChildren(node)
                        if (children.isNotEmpty()) {
                            blocks.add(MessageBlock.Heading(level, children))
                        }
                    }
                    "pre" -> {
                        flushPendingInlines()
                        val codeEl = node.selectFirst("code")
                        val codeText = (codeEl?.wholeText() ?: node.wholeText()).trimEnd('\r', '\n')
                        val rawLang = codeEl?.className()?.split(" ")?.firstOrNull { it.startsWith("language-") }
                        val lang = rawLang?.removePrefix("language-")?.takeIf { it.isNotBlank() }
                        blocks.add(MessageBlock.CodeBlock(language = lang, code = codeText))
                    }
                    "table" -> {
                        flushPendingInlines()
                        val tableBlock = parseTable(node)
                        if (tableBlock != null) {
                            blocks.add(tableBlock)
                        }
                    }
                    "blockquote" -> {
                        flushPendingInlines()
                        val quoteChildren = parseBlockNodes(node.childNodes(), depth + 1)
                        if (quoteChildren.isNotEmpty()) {
                            blocks.add(MessageBlock.Quote(quoteChildren))
                        }
                    }
                    "ul", "ol" -> {
                        flushPendingInlines()
                        val ordered = tag == "ol"
                        val listItems = mutableListOf<List<MessageBlock>>()
                        for (child in node.children()) {
                            if (child.tagName().lowercase() == "li") {
                                val itemBlocks = parseBlockNodes(child.childNodes(), depth + 1)
                                if (itemBlocks.isNotEmpty()) {
                                    listItems.add(itemBlocks)
                                }
                            }
                        }
                        if (listItems.isNotEmpty()) {
                            blocks.add(MessageBlock.ListBlock(ordered = ordered, items = listItems))
                        }
                    }
                    "hr" -> {
                        flushPendingInlines()
                        blocks.add(MessageBlock.HorizontalRule)
                    }
                    "div" -> {
                        flushPendingInlines()
                        val divBlocks = parseBlockNodes(node.childNodes(), depth + 1)
                        blocks.addAll(divBlocks)
                    }
                    else -> {
                        // Unknown or inline element in block position
                        val inlines = parseInlineNode(node)
                        pendingInlines.addAll(inlines)
                    }
                }
            } else if (node is TextNode) {
                if (node.text().isNotBlank()) {
                    pendingInlines.addAll(parseInlineNode(node))
                }
            }
        }

        flushPendingInlines()
        return blocks
    }

    private fun parseTable(tableEl: Element): MessageBlock.Table? {
        val rows = mutableListOf<List<List<InlineNode>>>()

        // 1. Process <thead> rows if any
        val thead = tableEl.selectFirst("thead")
        val theadRows = thead?.select("tr") ?: emptyList()
        for (tr in theadRows.take(MAX_TABLE_ROWS)) {
            val cellList = mutableListOf<List<InlineNode>>()
            for (th in tr.children().take(MAX_TABLE_COLS)) {
                if (th.tagName().lowercase() in listOf("th", "td")) {
                    cellList.add(parseInlineChildren(th))
                }
            }
            if (cellList.isNotEmpty()) {
                rows.add(cellList)
            }
        }

        // 2. Process <tbody> (or direct <tr>) rows
        val directTrs = tableEl.select("tbody > tr, tr")
        val dataTrs = directTrs.filter { tr ->
            // Exclude trs that were already in thead
            thead == null || tr.parent() != thead
        }

        for (tr in dataTrs.take(MAX_TABLE_ROWS - rows.size)) {
            val cellList = mutableListOf<List<InlineNode>>()
            for (td in tr.children().take(MAX_TABLE_COLS)) {
                if (td.tagName().lowercase() in listOf("th", "td")) {
                    cellList.add(parseInlineChildren(td))
                }
            }
            if (cellList.isNotEmpty()) {
                rows.add(cellList)
            }
        }

        if (rows.isEmpty()) return null
        return MessageBlock.Table(rows)
    }

    fun parseInlineChildren(parent: Element): List<InlineNode> {
        val results = mutableListOf<InlineNode>()
        for (child in parent.childNodes()) {
            results.addAll(parseInlineNode(child))
        }
        return results
    }

    private fun parseInlineNode(node: Node): List<InlineNode> {
        return when (node) {
            is TextNode -> parseTextWithMath(node.text())
            is Element -> {
                val tag = node.tagName().lowercase()

                // Check for inline math via data-mx-maths
                if (node.hasAttr("data-mx-maths")) {
                    val formula = node.attr("data-mx-maths").trim()
                    if (formula.isNotEmpty()) {
                        return listOf(InlineNode.InlineMath(formula))
                    }
                }

                when (tag) {
                    "b", "strong" -> listOf(InlineNode.Strong(parseInlineChildren(node)))
                    "i", "em" -> listOf(InlineNode.Emphasis(parseInlineChildren(node)))
                    "u" -> listOf(InlineNode.Underline(parseInlineChildren(node)))
                    "del", "s", "strike" -> listOf(InlineNode.Strikethrough(parseInlineChildren(node)))
                    "code" -> listOf(InlineNode.InlineCode(node.text()))
                    "a" -> {
                        val rawUrl = node.attr("href").trim()
                        val safeUrl = if (isAllowedScheme(rawUrl)) rawUrl else ""
                        val text = node.text()
                        if (node.hasAttr("data-mention-type") || rawUrl.startsWith("https://matrix.to/#/@")) {
                            listOf(InlineNode.Mention(text = text, url = safeUrl))
                        } else {
                            listOf(InlineNode.Link(url = safeUrl, children = parseInlineChildren(node)))
                        }
                    }
                    "br" -> listOf(InlineNode.LineBreak)
                    "math" -> {
                        val formula = node.attr("data-mx-maths").ifEmpty { node.text() }.trim()
                        if (formula.isNotEmpty()) listOf(InlineNode.InlineMath(formula)) else emptyList()
                    }
                    else -> {
                        // Unknown or span tag: preserve children
                        parseInlineChildren(node)
                    }
                }
            }
            else -> emptyList()
        }
    }

    private fun parseTextWithMath(text: String): List<InlineNode> {
        val matches = INLINE_MATH_REGEX.findAll(text).toList()
        if (matches.isEmpty()) {
            return listOf(InlineNode.Text(text))
        }

        val nodes = mutableListOf<InlineNode>()
        var lastIndex = 0

        for (match in matches) {
            if (match.range.first > lastIndex) {
                nodes.add(InlineNode.Text(text.substring(lastIndex, match.range.first)))
            }
            val formula = match.groupValues[1].trim()
            if (formula.isNotEmpty()) {
                nodes.add(InlineNode.InlineMath(formula))
            }
            lastIndex = match.range.last + 1
        }

        if (lastIndex < text.length) {
            nodes.add(InlineNode.Text(text.substring(lastIndex)))
        }

        return nodes
    }

    private fun isAllowedScheme(url: String): Boolean {
        if (url.startsWith("#") || url.startsWith("/")) return true
        val scheme = url.substringBefore(":", "").lowercase()
        return scheme in ALLOWED_SCHEMES
    }
}
