/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import io.element.android.compound.theme.ElementTheme

@Composable
fun HtmlTableView(
    rows: List<List<List<InlineNode>>>,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
) {
    if (rows.isEmpty()) return
    val context = LocalContext.current
    val columnCount = rows.maxOfOrNull { it.size } ?: 0
    if (columnCount == 0) return

    // Calculate balanced column widths across all rows
    val columnWidths = remember(rows) {
        (0 until columnCount).map { colIndex ->
            val maxLen = rows.maxOfOrNull { row ->
                val cellNodes = row.getOrNull(colIndex) ?: emptyList()
                cellNodes.sumOf { it.plainTextLength() }
            } ?: 0
            maxOf(88, minOf(280, maxLen * 14 + 32)).dp
        }
    }

    val copyTableAction = {
        val markdown = buildMarkdownTable(rows)
        val clipboard = context.getSystemService<ClipboardManager>()
        clipboard?.setPrimaryClip(ClipData.newPlainText("Markdown Table", markdown))
        Toast.makeText(context, "已复制表格内容", Toast.LENGTH_SHORT).show()
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x10808080), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x2D808080), RoundedCornerShape(8.dp))
            .clickable(onClick = copyTableAction)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .background(Color(0x08808080), RoundedCornerShape(6.dp))
                    .border(0.5.dp, Color(0x20808080), RoundedCornerShape(6.dp))
            ) {
                rows.forEachIndexed { rowIndex, row ->
                    val isHeader = rowIndex == 0
                    val rowBg = when {
                        isHeader -> Color(0x1E808080)
                        rowIndex % 2 == 1 -> Color(0x0A808080)
                        else -> Color.Transparent
                    }

                    Row(modifier = Modifier.background(rowBg)) {
                        for (colIndex in 0 until columnCount) {
                            val cellNodes = row.getOrNull(colIndex) ?: emptyList()
                            val colWidth = columnWidths.getOrElse(colIndex) { 100.dp }

                            Box(
                                modifier = Modifier
                                    .width(colWidth)
                                    .padding(horizontal = 10.dp, vertical = if (isHeader) 8.dp else 7.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                InlineText(
                                    nodes = cellNodes,
                                    style = if (isHeader) {
                                        ElementTheme.typography.fontBodyMdMedium.copy(fontWeight = FontWeight.Bold)
                                    } else {
                                        ElementTheme.typography.fontBodyMdRegular
                                    },
                                    onLinkClick = onLinkClick,
                                )
                            }
                        }
                    }

                    if (isHeader) {
                        HorizontalDivider(color = Color(0x2D808080), thickness = 1.dp)
                    } else if (rowIndex < rows.lastIndex) {
                        HorizontalDivider(color = Color(0x14808080), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

private fun InlineNode.plainTextLength(): Int {
    return when (this) {
        is InlineNode.Text -> value.length
        is InlineNode.InlineCode -> value.length
        is InlineNode.InlineMath -> formula.length
        is InlineNode.Mention -> text.length
        is InlineNode.Strong -> children.sumOf { it.plainTextLength() }
        is InlineNode.Emphasis -> children.sumOf { it.plainTextLength() }
        is InlineNode.Underline -> children.sumOf { it.plainTextLength() }
        is InlineNode.Strikethrough -> children.sumOf { it.plainTextLength() }
        is InlineNode.Link -> children.sumOf { it.plainTextLength() }
        is InlineNode.LineBreak -> 1
    }
}

private fun buildMarkdownTable(rows: List<List<List<InlineNode>>>): String {
    val colCount = rows.maxOfOrNull { it.size } ?: 0
    if (colCount == 0) return ""

    fun rowToText(row: List<List<InlineNode>>): List<String> {
        return (0 until colCount).map { col ->
            val cell = row.getOrNull(col) ?: emptyList()
            cell.joinToString("") { it.toPlainText() }.replace("|", "\\|")
        }
    }

    return buildString {
        if (rows.isNotEmpty()) {
            val headerCells = rowToText(rows[0])
            append("| ")
            append(headerCells.joinToString(" | "))
            append(" |\n| ")
            append(List(colCount) { "---" }.joinToString(" | "))
            append(" |\n")

            for (rowIndex in 1 until rows.size) {
                val dataCells = rowToText(rows[rowIndex])
                append("| ")
                append(dataCells.joinToString(" | "))
                append(" |\n")
            }
        }
    }.trimEnd()
}

private fun InlineNode.toPlainText(): String {
    return when (this) {
        is InlineNode.Text -> value
        is InlineNode.InlineCode -> "`$value`"
        is InlineNode.InlineMath -> "$$formula$"
        is InlineNode.Mention -> text
        is InlineNode.Strong -> "**${children.joinToString("") { it.toPlainText() }}**"
        is InlineNode.Emphasis -> "*${children.joinToString("") { it.toPlainText() }}*"
        is InlineNode.Underline -> children.joinToString("") { it.toPlainText() }
        is InlineNode.Strikethrough -> "~~${children.joinToString("") { it.toPlainText() }}~~"
        is InlineNode.Link -> "[${children.joinToString("") { it.toPlainText() }}]($url)"
        is InlineNode.LineBreak -> " "
    }
}
