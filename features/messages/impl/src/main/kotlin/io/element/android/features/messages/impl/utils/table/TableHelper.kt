/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils.table

import org.jsoup.nodes.Document

object TableHelper {
    /**
     * Regex matching standard GitHub Flavored Markdown (GFM) tables:
     * - Header row with pipe-delimited columns
     * - Delimiter row containing dashes and optional colons
     * - One or more data rows with pipe-delimited columns
     */
    val TABLE_REGEX = Regex(
        """(?m)(?:^[ \t]*\|[^\n]+\|[ \t]*\r?\n[ \t]*\|[ \t]*:?[-]+:?[ \t]*(?:\|[ \t]*:?[-]+:?[ \t]*)+\|[ \t]*(?:\r?\n[ \t]*\|[^\n]+\|[ \t]*)+)"""
    )

    sealed interface TableSegment {
        data class Text(val text: CharSequence) : TableSegment
        data class Table(val tableData: TableData, val rawMarkdown: String) : TableSegment
    }

    /**
     * Preprocesses HTML `<table>` elements in [document] into standard Markdown tables
     * wrapped in `<p>` tags so that [HtmlToSpansParser] will preserve the tabular content
     * without smashing cell texts together.
     */
    fun preprocessHtmlDocument(document: Document) {
        val tables = document.select("table")
        for (table in tables) {
            val headers = mutableListOf<String>()
            val rows = mutableListOf<List<String>>()

            // Extract <th> headers
            val thElements = table.select("th")
            if (thElements.isNotEmpty()) {
                headers.addAll(thElements.map { it.text().trim() })
            }

            // Extract <tr> data rows
            val trElements = table.select("tr")
            for (tr in trElements) {
                val tdElements = tr.select("td")
                if (tdElements.isNotEmpty()) {
                    val row = tdElements.map { it.text().trim() }
                    rows.add(row)
                }
            }

            // Fallback: If no <th> tags existed, use the first row as headers
            if (headers.isEmpty() && rows.isNotEmpty()) {
                headers.addAll(rows.removeAt(0))
            }

            if (headers.isNotEmpty() || rows.isNotEmpty()) {
                val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
                if (colCount > 0) {
                    while (headers.size < colCount) {
                        headers.add("")
                    }
                    val tableData = TableData(headers = headers, rows = rows)
                    val md = toMarkdown(tableData)
                    val replacement = document.createElement("p")
                    replacement.text("\n$md\n")
                    table.replaceWith(replacement)
                }
            }
        }
    }

    /**
     * Parses a raw Markdown table text into a [TableData] structure.
     */
    fun parseMarkdownTable(markdown: String): TableData? {
        val lines = markdown.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return null

        val headerLine = lines[0]
        val separatorLine = lines[1]
        if (!separatorLine.contains("-")) return null

        fun parseRow(line: String): List<String> {
            val stripped = line.removePrefix("|").removeSuffix("|")
            return stripped.split("|").map { it.trim() }
        }

        val rawHeaders = parseRow(headerLine)
        val rawRows = lines.drop(2).map { parseRow(it) }
        val colCount = maxOf(rawHeaders.size, rawRows.maxOfOrNull { it.size } ?: 0)
        if (colCount == 0) return null

        val headers = rawHeaders.toMutableList()
        while (headers.size < colCount) {
            headers.add("")
        }

        val rows = rawRows.map { r ->
            val mutable = r.toMutableList()
            while (mutable.size < colCount) {
                mutable.add("")
            }
            mutable.take(colCount)
        }

        return TableData(headers = headers, rows = rows)
    }

    /**
     * Converts a [TableData] object into a GitHub Flavored Markdown table string.
     */
    fun toMarkdown(tableData: TableData): String {
        val colCount = tableData.columnCount
        if (colCount == 0) return ""

        val headers = tableData.headers.toMutableList()
        while (headers.size < colCount) {
            headers.add("")
        }

        return buildString {
            append("| ")
            append(headers.joinToString(" | "))
            append(" |\n| ")
            append(List(colCount) { "---" }.joinToString(" | "))
            append(" |\n")
            for (row in tableData.rows) {
                val padded = row.toMutableList()
                while (padded.size < colCount) {
                    padded.add("")
                }
                append("| ")
                append(padded.take(colCount).joinToString(" | "))
                append(" |\n")
            }
        }.trimEnd()
    }

    /**
     * Splits [text] into alternating [TableSegment.Text] and [TableSegment.Table] segments.
     */
    fun splitByTables(text: CharSequence): List<TableSegment> {
        val str = text.toString()
        val matches = TABLE_REGEX.findAll(str).toList()
        if (matches.isEmpty()) {
            return listOf(TableSegment.Text(text))
        }

        val segments = mutableListOf<TableSegment>()
        var lastEnd = 0

        for (match in matches) {
            if (match.range.first > lastEnd) {
                val sub = text.subSequence(lastEnd, match.range.first)
                if (sub.isNotEmpty()) {
                    segments.add(TableSegment.Text(sub))
                }
            }

            val tableMarkdown = match.value
            val tableData = parseMarkdownTable(tableMarkdown)
            if (tableData != null && !tableData.isEmpty) {
                segments.add(TableSegment.Table(tableData = tableData, rawMarkdown = tableMarkdown))
            } else {
                segments.add(TableSegment.Text(text.subSequence(match.range.first, match.range.last + 1)))
            }

            lastEnd = match.range.last + 1
        }

        if (lastEnd < text.length) {
            val trailing = text.subSequence(lastEnd, text.length)
            if (trailing.isNotEmpty()) {
                segments.add(TableSegment.Text(trailing))
            }
        }

        return segments
    }
}
