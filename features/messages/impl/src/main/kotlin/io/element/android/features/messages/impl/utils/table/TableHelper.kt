/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils.table

import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.core.text.getSpans
import org.jsoup.nodes.Document
import java.util.UUID

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

    data class ProcessedTable(
        val token: String,
        val tableData: TableData,
        val rawMarkdown: String,
    )

    sealed interface TableSegment {
        data class Text(val text: CharSequence) : TableSegment
        data class Table(val tableData: TableData, val rawMarkdown: String) : TableSegment
    }

    /**
     * Preprocesses HTML `<table>` elements in [document].
     * Replaces each `<table>` with a paragraph containing a unique alphanumeric placeholder token.
     * Returns the list of extracted [ProcessedTable] metadata.
     */
    fun preprocessHtmlDocument(document: Document): List<ProcessedTable> {
        val tables = document.select("table")
        if (tables.isEmpty()) return emptyList()

        val processed = mutableListOf<ProcessedTable>()

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
                    val normalizedRows = rows.map { r ->
                        val m = r.toMutableList()
                        while (m.size < colCount) {
                            m.add("")
                        }
                        m.take(colCount)
                    }
                    val tableData = TableData(headers = headers, rows = normalizedRows)
                    val md = toMarkdown(tableData)
                    val token = "MATRIX_TABLE_TOKEN_${UUID.randomUUID().toString().replace("-", "")}"
                    val replacement = document.createElement("p")
                    replacement.text(token)
                    table.replaceWith(replacement)
                    processed.add(ProcessedTable(token = token, tableData = tableData, rawMarkdown = md))
                }
            }
        }
        return processed
    }

    /**
     * Finds placeholder tokens in [charSequence] and attaches [TableSpan]s over the converted Markdown text.
     */
    fun attachTableSpans(charSequence: CharSequence, tables: List<ProcessedTable>): CharSequence {
        if (tables.isEmpty()) return charSequence

        val builder = (charSequence as? SpannableStringBuilder) ?: SpannableStringBuilder(charSequence)
        val textStr = builder.toString()

        val found = tables.mapNotNull { pt ->
            val idx = textStr.indexOf(pt.token)
            if (idx >= 0) Triple(idx, pt.token.length, pt) else null
        }.sortedByDescending { it.first }

        for ((start, length, pt) in found) {
            builder.replace(start, start + length, pt.rawMarkdown)
            val span = TableSpan(tableData = pt.tableData, rawMarkdown = pt.rawMarkdown)
            builder.setSpan(span, start, start + pt.rawMarkdown.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return builder
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
     * Supports both [TableSpan] (from HTML tables) and [TABLE_REGEX] (from plain Markdown text).
     */
    fun splitByTables(text: CharSequence): List<TableSegment> {
        // 1. Check for TableSpan
        if (text is Spanned) {
            val spans = text.getSpans<TableSpan>(0, text.length)
            if (spans.isNotEmpty()) {
                val segments = mutableListOf<TableSegment>()
                var lastEnd = 0
                val sorted = spans.map { span ->
                    Triple(text.getSpanStart(span), text.getSpanEnd(span), span)
                }.sortedBy { it.first }

                for ((start, end, span) in sorted) {
                    if (start > lastEnd) {
                        val sub = text.subSequence(lastEnd, start).trimNewlines()
                        if (sub.isNotBlank()) {
                            segments.add(TableSegment.Text(sub))
                        }
                    }
                    segments.add(TableSegment.Table(span.tableData, span.rawMarkdown))
                    lastEnd = end
                }

                if (lastEnd < text.length) {
                    val sub = text.subSequence(lastEnd, text.length).trimNewlines()
                    if (sub.isNotBlank()) {
                        segments.add(TableSegment.Text(sub))
                    }
                }
                return segments.ifEmpty { listOf(TableSegment.Text(text)) }
            }
        }

        // 2. Fallback: plain text Markdown tables
        val str = text.toString()
        val matches = TABLE_REGEX.findAll(str).toList()
        if (matches.isEmpty()) {
            return listOf(TableSegment.Text(text))
        }

        val segments = mutableListOf<TableSegment>()
        var lastEnd = 0

        for (match in matches) {
            if (match.range.first > lastEnd) {
                val sub = text.subSequence(lastEnd, match.range.first).trimNewlines()
                if (sub.isNotBlank()) {
                    segments.add(TableSegment.Text(sub))
                }
            }

            val tableMarkdown = match.value
            val tableData = parseMarkdownTable(tableMarkdown)
            if (tableData != null && !tableData.isEmpty) {
                segments.add(TableSegment.Table(tableData = tableData, rawMarkdown = tableMarkdown))
            } else {
                val fallbackSub = text.subSequence(match.range.first, match.range.last + 1).trimNewlines()
                if (fallbackSub.isNotBlank()) {
                    segments.add(TableSegment.Text(fallbackSub))
                }
            }

            lastEnd = match.range.last + 1
        }

        if (lastEnd < text.length) {
            val trailing = text.subSequence(lastEnd, text.length).trimNewlines()
            if (trailing.isNotBlank()) {
                segments.add(TableSegment.Text(trailing))
            }
        }

        return segments.ifEmpty { listOf(TableSegment.Text(text)) }
    }

    private fun CharSequence.trimNewlines(): CharSequence {
        var start = 0
        var end = length
        while (start < end && (this[start] == '\n' || this[start] == '\r' || this[start] == ' ')) {
            start++
        }
        while (end > start && (this[end - 1] == '\n' || this[end - 1] == '\r' || this[end - 1] == ' ')) {
            end--
        }
        return if (start == 0 && end == length) this else subSequence(start, end)
    }
}
