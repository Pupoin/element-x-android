/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast

import androidx.compose.runtime.Immutable

/**
 * Represents a block-level node in the message document AST.
 */
@Immutable
sealed interface MessageBlock {
    data class Paragraph(
        val children: List<InlineNode>,
    ) : MessageBlock

    data class Heading(
        val level: Int,
        val children: List<InlineNode>,
    ) : MessageBlock

    data class CodeBlock(
        val language: String?,
        val code: String,
    ) : MessageBlock

    data class Table(
        val rows: List<List<List<InlineNode>>>,
    ) : MessageBlock

    data class Quote(
        val children: List<MessageBlock>,
    ) : MessageBlock

    data class ListBlock(
        val ordered: Boolean,
        val items: List<List<MessageBlock>>,
    ) : MessageBlock

    data class BlockMath(
        val formula: String,
    ) : MessageBlock

    data object HorizontalRule : MessageBlock
}
