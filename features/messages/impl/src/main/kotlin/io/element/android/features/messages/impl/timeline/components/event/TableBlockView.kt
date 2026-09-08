/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Text
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
import io.element.android.features.messages.impl.utils.table.TableData
import io.element.android.features.messages.impl.utils.table.TableHelper

/**
 * A horizontally-scrollable Compose card that renders structured tables with
 * header styling, alternating row backgrounds, and clean borders.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TableBlockView(
    tableData: TableData,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val columnCount = tableData.columnCount
    if (columnCount == 0) return

    val columnWidths = remember(tableData) {
        (0 until columnCount).map { colIndex ->
            val headerLen = tableData.headers.getOrNull(colIndex)?.length ?: 0
            val maxCellLen = tableData.rows.maxOfOrNull { it.getOrNull(colIndex)?.length ?: 0 } ?: 0
            val maxLen = maxOf(headerLen, maxCellLen)
            maxOf(88, minOf(260, maxLen * 14 + 28)).dp
        }
    }

    val scrollState = rememberScrollState()

    val copyAction = {
        val markdown = TableHelper.toMarkdown(tableData)
        val clipboard = context.getSystemService<ClipboardManager>()
        clipboard?.setPrimaryClip(ClipData.newPlainText("Markdown Table", markdown))
        Toast.makeText(context, "已复制表格内容", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x10808080), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x2D808080), RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = copyAction,
                onLongClick = onLongClick,
            )
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
                // Header row
                if (tableData.headers.isNotEmpty()) {
                    Row(
                        modifier = Modifier.background(Color(0x1E808080))
                    ) {
                        tableData.headers.forEachIndexed { colIndex, header ->
                            val colWidth = columnWidths.getOrElse(colIndex) { 100.dp }
                            Box(
                                modifier = Modifier
                                    .width(colWidth)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = header,
                                    style = ElementTheme.typography.fontBodyMdMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ElementTheme.colors.textPrimary,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0x2D808080), thickness = 1.dp)
                }

                // Data rows
                tableData.rows.forEachIndexed { rowIndex, row ->
                    val bg = if (rowIndex % 2 == 1) Color(0x0C808080) else Color.Transparent
                    Row(
                        modifier = Modifier.background(bg)
                    ) {
                        for (colIndex in 0 until columnCount) {
                            val cellText = row.getOrNull(colIndex).orEmpty()
                            val colWidth = columnWidths.getOrElse(colIndex) { 100.dp }
                            Box(
                                modifier = Modifier
                                    .width(colWidth)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = cellText,
                                    style = ElementTheme.typography.fontBodyMdRegular,
                                    color = ElementTheme.colors.textPrimary,
                                )
                            }
                        }
                    }
                    if (rowIndex < tableData.rows.lastIndex) {
                        HorizontalDivider(color = Color(0x14808080), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
