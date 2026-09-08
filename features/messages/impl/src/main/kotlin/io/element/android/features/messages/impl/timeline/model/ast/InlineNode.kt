/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast

import androidx.compose.runtime.Immutable

/**
 * Represents an inline formatting node within a paragraph, heading, or table cell.
 */
@Immutable
sealed interface InlineNode {
    data class Text(val value: String) : InlineNode
    data class Strong(val children: List<InlineNode>) : InlineNode
    data class Emphasis(val children: List<InlineNode>) : InlineNode
    data class Underline(val children: List<InlineNode>) : InlineNode
    data class Strikethrough(val children: List<InlineNode>) : InlineNode
    data class InlineCode(val value: String) : InlineNode
    data class InlineMath(val formula: String) : InlineNode
    data class Link(
        val url: String,
        val children: List<InlineNode>,
    ) : InlineNode
    data class Mention(
        val text: String,
        val url: String,
    ) : InlineNode
    data object LineBreak : InlineNode
}
