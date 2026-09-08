/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.components.event.LatexBlockMathView

@Composable
fun MessageBlocksView(
    blocks: List<MessageBlock>,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEachIndexed { index, block ->
            key(index) {
                when (block) {
                    is MessageBlock.Paragraph -> {
                        InlineText(
                            nodes = block.children,
                            onLinkClick = onLinkClick,
                            onLongClick = onLongClick,
                        )
                    }
                    is MessageBlock.Heading -> {
                        HeadingView(
                            level = block.level,
                            nodes = block.children,
                            onLinkClick = onLinkClick,
                            onLongClick = onLongClick,
                        )
                    }
                    is MessageBlock.CodeBlock -> {
                        InteractiveCodeCard(
                            code = block.code,
                            language = block.language,
                            onLongClick = onLongClick,
                        )
                    }
                    is MessageBlock.Table -> {
                        HtmlTableView(
                            rows = block.rows,
                            onLinkClick = onLinkClick,
                            onLongClick = onLongClick,
                        )
                    }
                    is MessageBlock.Quote -> {
                        QuoteCard {
                            MessageBlocksView(
                                blocks = block.children,
                                onLinkClick = onLinkClick,
                                onLongClick = onLongClick,
                            )
                        }
                    }
                    is MessageBlock.ListBlock -> {
                        MessageListView(
                            listBlock = block,
                            onLinkClick = onLinkClick,
                            onLongClick = onLongClick,
                        )
                    }
                    is MessageBlock.BlockMath -> {
                        LatexBlockMathView(
                            rawFormula = block.formula,
                            modifier = Modifier.fillMaxWidth(),
                            onLongClick = onLongClick,
                        )
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

@Composable
private fun HeadingView(
    level: Int,
    nodes: List<InlineNode>,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
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
        onLongClick = onLongClick,
    )
}

@Composable
private fun MessageListView(
    listBlock: MessageBlock.ListBlock,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
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
                        onLongClick = onLongClick,
                    )
                }
            }
        }
    }
}
