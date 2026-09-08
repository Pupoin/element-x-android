/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils.table

object TableHelper {
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
}
