/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.getSystemService
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.model.ast.InlineNode
import io.element.android.features.messages.impl.timeline.model.ast.InlineText
import io.element.android.libraries.designsystem.theme.components.Icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableFullscreenViewer(
    rows: List<List<List<InlineNode>>>,
    onDismiss: () -> Unit,
    onLinkClick: ((String) -> Unit)? = null,
) {
    if (rows.isEmpty()) return
    val context = LocalContext.current
    val columnCount = rows.maxOfOrNull { it.size } ?: 0
    if (columnCount == 0) return

    val columnWidths = remember(rows) {
        (0 until columnCount).map { colIndex ->
            val maxLen = rows.maxOfOrNull { row ->
                val cellNodes = row.getOrNull(colIndex) ?: emptyList()
                cellNodes.sumOf { it.plainTextLength() }
            } ?: 0
            maxOf(100, minOf(360, maxLen * 15 + 40)).dp
        }
    }

    val copyAction = {
        val markdown = buildMarkdownTable(rows)
        val clipboard = context.getSystemService<ClipboardManager>()
        clipboard?.setPrimaryClip(ClipData.newPlainText("Markdown Table", markdown))
        Toast.makeText(context, "已复制 Markdown 表格", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "表格 (${rows.size} 行 $columnCount 列)",
                            style = ElementTheme.typography.fontHeadingSmMedium,
                            color = ElementTheme.colors.textPrimary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = CompoundIcons.ArrowLeft(),
                                contentDescription = "返回",
                                tint = ElementTheme.colors.iconPrimary,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = copyAction) {
                            Icon(
                                imageVector = CompoundIcons.Copy(),
                                contentDescription = "复制 Markdown",
                                tint = ElementTheme.colors.iconPrimary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ElementTheme.colors.bgCanvasDefault
                    )
                )
            },
            containerColor = ElementTheme.colors.bgCanvasDefault,
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(Color(0x08808080), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0x2D808080), RoundedCornerShape(8.dp))
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
                                val colWidth = columnWidths.getOrElse(colIndex) { 120.dp }

                                Box(
                                    modifier = Modifier
                                        .width(colWidth)
                                        .padding(horizontal = 12.dp, vertical = if (isHeader) 10.dp else 9.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    InlineText(
                                        nodes = cellNodes,
                                        style = if (isHeader) {
                                            ElementTheme.typography.fontBodyLgMedium.copy(fontWeight = FontWeight.Bold)
                                        } else {
                                            ElementTheme.typography.fontBodyLgRegular
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

    val sb = StringBuilder()

    fun renderCell(cell: List<InlineNode>): String {
        return cell.joinToString("") { node ->
            when (node) {
                is InlineNode.Text -> node.value
                is InlineNode.InlineCode -> "`${node.value}`"
                is InlineNode.InlineMath -> "\$${node.formula}\$"
                is InlineNode.Mention -> node.text
                is InlineNode.Strong -> "**${renderCell(node.children)}**"
                is InlineNode.Emphasis -> "*${renderCell(node.children)}*"
                is InlineNode.Underline -> "<u>${renderCell(node.children)}</u>"
                is InlineNode.Strikethrough -> "~~${renderCell(node.children)}~~"
                is InlineNode.Link -> "[${renderCell(node.children)}](${node.url})"
                is InlineNode.LineBreak -> " "
            }
        }.replace("|", "\\|").replace("\n", " ").trim()
    }

    rows.forEachIndexed { rowIndex, row ->
        val cells = (0 until colCount).map { col ->
            renderCell(row.getOrNull(col) ?: emptyList())
        }
        sb.append("| ").append(cells.joinToString(" | ")).append(" |\n")

        if (rowIndex == 0) {
            val divider = (0 until colCount).joinToString(" | ") { "---" }
            sb.append("| ").append(divider).append(" |\n")
        }
    }

    return sb.toString().trimEnd()
}
