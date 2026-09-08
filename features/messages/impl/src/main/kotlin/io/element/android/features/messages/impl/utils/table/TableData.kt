/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils.table

/**
 * Structured model representing a tabular dataset for display in a Compose table card.
 *
 * @param headers Column headers, typically extracted from <th> elements or Markdown header row.
 * @param rows Data rows, where each row contains the cell values in column order.
 */
data class TableData(
    val headers: List<String>,
    val rows: List<List<String>>,
) {
    val columnCount: Int
        get() = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)

    val rowCount: Int
        get() = rows.size

    val isEmpty: Boolean
        get() = headers.isEmpty() && rows.isEmpty()
}

/**
 * Span marking a structured table block within a Spanned text.
 */
class TableSpan(
    val tableData: TableData,
    val rawMarkdown: String,
)
