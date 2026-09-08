/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast

import android.util.LruCache
import io.element.android.features.messages.impl.utils.latex.LatexHelper
import io.element.android.libraries.matrix.api.timeline.item.event.FormattedBody
import io.element.android.libraries.matrix.api.timeline.item.event.MessageFormat
import org.jsoup.nodes.Document

object MessageAstParser {
    // Cache the parsed AST by the hash of its input content to avoid recomputing on Compose recompositions
    private val astCache = LruCache<Int, List<MessageBlock>>(128)

    fun parse(formattedBody: FormattedBody?, rawBody: String): List<MessageBlock> {
        val htmlBody = if (formattedBody?.format == MessageFormat.HTML && formattedBody.body.isNotBlank()) {
            formattedBody.body
        } else {
            null
        }
        val contentKey = htmlBody ?: rawBody
        val cacheKey = contentKey.hashCode()

        val cached = astCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val parsed = if (htmlBody != null) {
            HtmlToMessageAstParser.parse(htmlBody)
        } else {
            MarkdownToMessageAstParser.parse(rawBody)
        }

        val promoted = elevateTallFormulas(parsed)
        astCache.put(cacheKey, promoted)
        return promoted
    }

    fun parse(htmlDocument: Document?, rawBody: String): List<MessageBlock> {
        val cacheKey = if (htmlDocument != null) htmlDocument.outerHtml().hashCode() else rawBody.hashCode()
        val cached = astCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val parsed = if (htmlDocument != null) {
            HtmlToMessageAstParser.parse(htmlDocument)
        } else {
            MarkdownToMessageAstParser.parse(rawBody)
        }

        val promoted = elevateTallFormulas(parsed)
        astCache.put(cacheKey, promoted)
        return promoted
    }

    /**
     * Inspects paragraph blocks and promotes tall or complex inline formulas (such as \frac,
     * \matrix, or high vertical expressions) into standalone centered [MessageBlock.BlockMath] blocks.
     * Short formulas (\alpha, x, \sin\theta) remain inline.
     */
    fun elevateTallFormulas(blocks: List<MessageBlock>): List<MessageBlock> {
        val result = mutableListOf<MessageBlock>()
        for (block in blocks) {
            when (block) {
                is MessageBlock.Paragraph -> {
                    result.addAll(splitParagraphByTallFormulas(block.children))
                }
                is MessageBlock.Quote -> {
                    result.add(MessageBlock.Quote(elevateTallFormulas(block.children)))
                }
                is MessageBlock.ListBlock -> {
                    result.add(
                        MessageBlock.ListBlock(
                            ordered = block.ordered,
                            items = block.items.map { elevateTallFormulas(it) }
                        )
                    )
                }
                else -> {
                    result.add(block)
                }
            }
        }
        return result
    }

    private fun splitParagraphByTallFormulas(children: List<InlineNode>): List<MessageBlock> {
        val hasTallFormula = children.any { getTallFormulaIfAny(it) != null }
        if (!hasTallFormula) {
            return listOf(MessageBlock.Paragraph(children))
        }

        val result = mutableListOf<MessageBlock>()
        val currentInlines = mutableListOf<InlineNode>()

        fun flushCurrentInlines() {
            val trimmed = trimInlines(currentInlines)
            if (trimmed.isNotEmpty()) {
                result.add(MessageBlock.Paragraph(trimmed))
            }
            currentInlines.clear()
        }

        for (node in children) {
            val tallFormula = getTallFormulaIfAny(node)
            if (tallFormula != null) {
                flushCurrentInlines()
                result.add(MessageBlock.BlockMath(tallFormula))
            } else {
                currentInlines.add(node)
            }
        }

        flushCurrentInlines()
        return result
    }

    private fun getTallFormulaIfAny(node: InlineNode): String? {
        return when (node) {
            is InlineNode.InlineMath -> if (LatexHelper.isComplexOrTallFormula(node.formula)) node.formula else null
            is InlineNode.Strong -> if (node.children.size == 1) getTallFormulaIfAny(node.children[0]) else null
            is InlineNode.Emphasis -> if (node.children.size == 1) getTallFormulaIfAny(node.children[0]) else null
            else -> null
        }
    }

    private fun trimInlines(inlines: List<InlineNode>): List<InlineNode> {
        var start = 0
        var end = inlines.size

        while (start < end) {
            val node = inlines[start]
            if (node is InlineNode.Text && node.value.isBlank()) {
                start++
            } else if (node is InlineNode.LineBreak) {
                start++
            } else {
                break
            }
        }

        while (end > start) {
            val node = inlines[end - 1]
            if (node is InlineNode.Text && node.value.isBlank()) {
                end--
            } else if (node is InlineNode.LineBreak) {
                end--
            } else {
                break
            }
        }

        if (start >= end) return emptyList()

        val sub = inlines.subList(start, end).toMutableList()
        val first = sub.first()
        if (first is InlineNode.Text) {
            sub[0] = InlineNode.Text(first.value.trimStart())
        }
        val last = sub.last()
        if (last is InlineNode.Text) {
            sub[sub.lastIndex] = InlineNode.Text(last.value.trimEnd())
        }
        return sub.filterNot { it is InlineNode.Text && it.value.isEmpty() }
    }
}
