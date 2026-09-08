/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast

import io.element.android.features.messages.impl.utils.table.TableHelper

object MarkdownToMessageAstParser {
    private val BLOCK_MATH_REGEX = Regex("""\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)
    private val CODE_BLOCK_REGEX = Regex("""```(\w+)?\r?\n([\s\S]*?)```""")
    private val INLINE_MATH_REGEX = Regex("""(?<!\$)\$(?!\$)(?!\d+[\s.,])([^\$\n]+?)(?<!\$)\$(?!\$)""")

    fun parse(markdown: String): List<MessageBlock> {
        val trimmed = markdown.trim()
        if (trimmed.isEmpty()) return emptyList()

        val blocks = mutableListOf<MessageBlock>()
        val lines = trimmed.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmedLine = line.trim()

            // 1. Check Code Block
            if (trimmedLine.startsWith("```")) {
                val lang = trimmedLine.removePrefix("```").trim().takeIf { it.isNotEmpty() }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // consume closing ```
                val codeContent = codeLines.joinToString("\n")
                val finalLang = lang ?: io.element.android.features.messages.impl.timeline.model.ast.code.DefaultCodeSyntaxHighlighter
                    .detectLanguage(codeContent)
                    .takeIf { it.isNotBlank() }
                blocks.add(MessageBlock.CodeBlock(language = finalLang, code = codeContent))
                continue
            }

            // 2. Check Block Math ($$...$$ or \begin{...})
            if (trimmedLine.startsWith("$$") || trimmedLine.startsWith("\\begin{")) {
                val mathLines = mutableListOf<String>()
                if (trimmedLine.startsWith("$$")) {
                    if (trimmedLine.length > 2 && trimmedLine.endsWith("$$")) {
                        blocks.add(MessageBlock.BlockMath(trimmedLine.removePrefix("$$").removeSuffix("$$").trim()))
                        i++
                        continue
                    }
                    mathLines.add(trimmedLine.removePrefix("$$"))
                    i++
                    while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                        mathLines.add(lines[i])
                        i++
                    }
                    if (i < lines.size) {
                        mathLines.add(lines[i].trim().removeSuffix("$$"))
                        i++
                    }
                    blocks.add(MessageBlock.BlockMath(mathLines.joinToString("\n").trim()))
                    continue
                } else {
                    val envMatch = Regex("""^\\begin\{([^}]+)\}""").find(trimmedLine)
                    val envName = envMatch?.groupValues?.get(1)
                    if (envName != null) {
                        val endTag = "\\end{$envName}"
                        while (i < lines.size) {
                            mathLines.add(lines[i])
                            if (lines[i].contains(endTag)) {
                                i++
                                break
                            }
                            i++
                        }
                        blocks.add(MessageBlock.BlockMath(mathLines.joinToString("\n").trim()))
                        continue
                    }
                }
            }

            // 3. Check Headings (# to ######)
            val headingMatch = Regex("""^(#{1,6})\s+(.+)$""").matchEntire(trimmedLine)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val content = headingMatch.groupValues[2]
                blocks.add(MessageBlock.Heading(level, parseInline(content)))
                i++
                continue
            }

            // 4. Check Blockquote (> text)
            if (trimmedLine.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                    i++
                }
                val quoteBlocks = parse(quoteLines.joinToString("\n"))
                blocks.add(MessageBlock.Quote(quoteBlocks))
                continue
            }

            // 5. Check Table (| header |)
            if (trimmedLine.startsWith("|") && trimmedLine.endsWith("|") && i + 1 < lines.size && lines[i + 1].contains("-")) {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                val tableBlock = parseTableFromMarkdownLines(tableLines)
                if (tableBlock != null) {
                    blocks.add(tableBlock)
                    continue
                }
            }

            // 6. Horizontal rule
            if (trimmedLine == "---" || trimmedLine == "***" || trimmedLine == "___") {
                blocks.add(MessageBlock.HorizontalRule)
                i++
                continue
            }

            // 7. Regular paragraph lines
            val paragraphLines = mutableListOf<String>()
            while (i < lines.size) {
                val current = lines[i].trim()
                if (current.isEmpty() ||
                    current.startsWith("```") ||
                    current.startsWith("$$") ||
                    current.startsWith("#") ||
                    current.startsWith(">") ||
                    (current.startsWith("|") && current.endsWith("|")) ||
                    current == "---"
                ) {
                    break
                }
                paragraphLines.add(lines[i])
                i++
            }
            if (paragraphLines.isNotEmpty()) {
                val text = paragraphLines.joinToString("\n")
                blocks.add(MessageBlock.Paragraph(parseInline(text)))
            } else {
                i++
            }
        }

        return blocks
    }

    private fun parseTableFromMarkdownLines(lines: List<String>): MessageBlock.Table? {
        val tableData = TableHelper.parseMarkdownTable(lines.joinToString("\n")) ?: return null
        val rows = mutableListOf<List<List<InlineNode>>>()

        if (tableData.headers.isNotEmpty()) {
            val headerRow = tableData.headers.map { parseInline(it) }
            rows.add(headerRow)
        }

        for (row in tableData.rows) {
            val dataRow = row.map { parseInline(it) }
            rows.add(dataRow)
        }

        return MessageBlock.Table(rows)
    }

    fun parseInline(text: String): List<InlineNode> {
        // Fast path for simple plain text
        if (!text.contains('$') && !text.contains('*') && !text.contains('`') && !text.contains('[') && !text.contains('\n')) {
            return listOf(InlineNode.Text(text))
        }

        // Inline Math ($...$)
        val mathMatches = INLINE_MATH_REGEX.findAll(text).toList()
        if (mathMatches.isNotEmpty()) {
            val nodes = mutableListOf<InlineNode>()
            var lastIdx = 0
            for (match in mathMatches) {
                if (match.range.first > lastIdx) {
                    nodes.addAll(parseInlineFormatting(text.substring(lastIdx, match.range.first)))
                }
                nodes.add(InlineNode.InlineMath(match.groupValues[1].trim()))
                lastIdx = match.range.last + 1
            }
            if (lastIdx < text.length) {
                nodes.addAll(parseInlineFormatting(text.substring(lastIdx)))
            }
            return nodes
        }

        return parseInlineFormatting(text)
    }

    private fun parseInlineFormatting(text: String): List<InlineNode> {
        // Handle LineBreaks and basic formatting
        val lines = text.split("\n")
        val nodes = mutableListOf<InlineNode>()
        for (idx in lines.indices) {
            val line = lines[idx]
            if (line.isNotEmpty()) {
                nodes.addAll(parseLinksAndStyles(line))
            }
            if (idx < lines.lastIndex) {
                nodes.add(InlineNode.LineBreak)
            }
        }
        return nodes
    }

    private fun parseLinksAndStyles(text: String): List<InlineNode> {
        // Basic parser for [text](url) and `code` and **bold**
        val linkRegex = Regex("""\[([^\]]+)\]\(([^)]+)\)""")
        val linkMatches = linkRegex.findAll(text).toList()
        if (linkMatches.isNotEmpty()) {
            val nodes = mutableListOf<InlineNode>()
            var lastIdx = 0
            for (m in linkMatches) {
                if (m.range.first > lastIdx) {
                    nodes.addAll(parseStyles(text.substring(lastIdx, m.range.first)))
                }
                val label = m.groupValues[1]
                val url = m.groupValues[2]
                nodes.add(InlineNode.Link(url = url, children = parseStyles(label)))
                lastIdx = m.range.last + 1
            }
            if (lastIdx < text.length) {
                nodes.addAll(parseStyles(text.substring(lastIdx)))
            }
            return nodes
        }
        return parseStyles(text)
    }

    private fun parseStyles(text: String): List<InlineNode> {
        // Code spans `code`
        val codeRegex = Regex("""`([^`]+)`""")
        val codeMatches = codeRegex.findAll(text).toList()
        if (codeMatches.isNotEmpty()) {
            val nodes = mutableListOf<InlineNode>()
            var lastIdx = 0
            for (m in codeMatches) {
                if (m.range.first > lastIdx) {
                    nodes.addAll(parseBoldItalic(text.substring(lastIdx, m.range.first)))
                }
                nodes.add(InlineNode.InlineCode(m.groupValues[1]))
                lastIdx = m.range.last + 1
            }
            if (lastIdx < text.length) {
                nodes.addAll(parseBoldItalic(text.substring(lastIdx)))
            }
            return nodes
        }
        return parseBoldItalic(text)
    }

    private fun parseBoldItalic(text: String): List<InlineNode> {
        // **bold**
        val boldRegex = Regex("""\*\*([^*]+)\*\*""")
        val boldMatches = boldRegex.findAll(text).toList()
        if (boldMatches.isNotEmpty()) {
            val nodes = mutableListOf<InlineNode>()
            var lastIdx = 0
            for (m in boldMatches) {
                if (m.range.first > lastIdx) {
                    nodes.add(InlineNode.Text(text.substring(lastIdx, m.range.first)))
                }
                nodes.add(InlineNode.Strong(listOf(InlineNode.Text(m.groupValues[1]))))
                lastIdx = m.range.last + 1
            }
            if (lastIdx < text.length) {
                nodes.add(InlineNode.Text(text.substring(lastIdx)))
            }
            return nodes
        }
        return listOf(InlineNode.Text(text))
    }
}
