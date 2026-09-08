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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import io.element.android.compound.theme.ElementTheme

@Composable
fun MessageBlocksView(
    blocks: List<MessageBlock>,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
) {
    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            blocks.forEachIndexed { index, block ->
                key(index) {
                    when (block) {
                        is MessageBlock.Paragraph -> {
                            InlineText(
                                nodes = block.children,
                                onLinkClick = onLinkClick,
                            )
                        }
                        is MessageBlock.Heading -> {
                            HeadingView(
                                level = block.level,
                                nodes = block.children,
                                onLinkClick = onLinkClick,
                            )
                        }
                        is MessageBlock.CodeBlock -> {
                            InteractiveCodeCard(
                                code = block.code,
                                language = block.language,
                            )
                        }
                        is MessageBlock.Table -> {
                            HtmlTableView(
                                rows = block.rows,
                                onLinkClick = onLinkClick,
                            )
                        }
                        is MessageBlock.Quote -> {
                            QuoteCard {
                                MessageBlocksView(
                                    blocks = block.children,
                                    onLinkClick = onLinkClick,
                                )
                            }
                        }
                        is MessageBlock.ListBlock -> {
                            MessageListView(
                                listBlock = block,
                                onLinkClick = onLinkClick,
                            )
                        }
                        is MessageBlock.BlockMath -> {
                            BlockMathCard(formula = block.formula)
                        }
                        is MessageBlock.HorizontalRule -> {
                            HorizontalDivider(
                                color = Color(0x20808080),
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadingView(
    level: Int,
    nodes: List<InlineNode>,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val style = when (level) {
        1 -> ElementTheme.typography.fontHeadingXlBold
        2 -> ElementTheme.typography.fontHeadingLgBold
        3 -> ElementTheme.typography.fontHeadingMdBold
        4 -> ElementTheme.typography.fontHeadingSmMedium.copy(fontWeight = FontWeight.Bold)
        else -> ElementTheme.typography.fontBodyLgMedium.copy(fontWeight = FontWeight.Bold)
    }

    InlineText(
        nodes = nodes,
        modifier = modifier.padding(vertical = 2.dp),
        style = style,
        color = ElementTheme.colors.textPrimary,
        onLinkClick = onLinkClick,
    )
}

@Composable
private fun MessageListView(
    listBlock: MessageBlock.ListBlock,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listBlock.items.forEachIndexed { itemIndex, itemBlocks ->
            Row(modifier = Modifier.fillMaxWidth()) {
                val bullet = if (listBlock.ordered) "${itemIndex + 1}." else "•"
                Text(
                    text = bullet,
                    style = ElementTheme.typography.fontBodyLgRegular,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    MessageBlocksView(
                        blocks = itemBlocks,
                        onLinkClick = onLinkClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockMathCard(
    formula: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val copyAction = {
        val clipboard = context.getSystemService<ClipboardManager>()
        clipboard?.setPrimaryClip(ClipData.newPlainText("LaTeX Formula", formula))
        Toast.makeText(context, "已复制 LaTeX 公式: $formula", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x10808080), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x2D808080), RoundedCornerShape(8.dp))
            .clickable(onClick = copyAction)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = formula,
                style = ElementTheme.typography.fontBodyLgRegular.copy(
                    fontFamily = FontFamily.Serif,
                    color = ElementTheme.colors.textActionAccent,
                ),
            )
        }
    }
}
