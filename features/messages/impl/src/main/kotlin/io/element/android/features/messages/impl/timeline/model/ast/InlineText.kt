/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.utils.latex.LatexHelper
import ru.noties.jlatexmath.JLatexMathDrawable

@Composable
fun InlineText(
    nodes: List<InlineNode>,
    modifier: Modifier = Modifier,
    style: TextStyle = ElementTheme.typography.fontBodyLgRegular,
    color: Color = ElementTheme.colors.textPrimary,
    onLinkClick: ((String) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current

    val linkColor = ElementTheme.colors.textActionAccent
    val codeBgColor = Color(0x18808080)
    val mathColor = ElementTheme.colors.textActionAccent

    val (annotatedString, inlineContent) = remember(nodes, color, style, density) {
        val inlineMap = mutableMapOf<String, InlineTextContent>()
        val textSizePx = with(density) { style.fontSize.toPx() }
        val textColorInt = color.toArgb()
        var mathCounter = 0

        val text = buildAnnotatedString {
            fun appendInlinesInternal(currentNodes: List<InlineNode>) {
                for (node in currentNodes) {
                    when (node) {
                        is InlineNode.Text -> append(node.value)
                        is InlineNode.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            appendInlinesInternal(node.children)
                        }
                        is InlineNode.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            appendInlinesInternal(node.children)
                        }
                        is InlineNode.Underline -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                            appendInlinesInternal(node.children)
                        }
                        is InlineNode.Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            appendInlinesInternal(node.children)
                        }
                        is InlineNode.InlineCode -> withStyle(
                            SpanStyle(fontFamily = FontFamily.Monospace, background = codeBgColor)
                        ) {
                            append(" ${node.value} ")
                        }
                        is InlineNode.InlineMath -> {
                            val mathId = "math_${mathCounter++}"
                            val clean = LatexHelper.cleanFormula(node.formula)
                            val drawable = try {
                                JLatexMathDrawable.builder(clean)
                                    .textSize(textSizePx)
                                    .color(textColorInt)
                                    .align(JLatexMathDrawable.ALIGN_LEFT)
                                    .build()
                            } catch (_: Throwable) {
                                null
                            }

                            if (drawable != null && drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                                val bitmap = Bitmap.createBitmap(
                                    drawable.intrinsicWidth,
                                    drawable.intrinsicHeight,
                                    Bitmap.Config.ARGB_8888
                                )
                                val canvas = android.graphics.Canvas(bitmap)
                                drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                                drawable.draw(canvas)

                                appendInlineContent(mathId, alternateText = "\$${node.formula}\$")
                                inlineMap[mathId] = InlineTextContent(
                                    placeholder = Placeholder(
                                        width = with(density) { drawable.intrinsicWidth.toSp() },
                                        height = with(density) { drawable.intrinsicHeight.toSp() },
                                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                    )
                                ) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = node.formula,
                                    )
                                }
                            } else {
                                pushStringAnnotation(tag = "MATH", annotation = node.formula)
                                withStyle(
                                    SpanStyle(
                                        color = mathColor,
                                        fontFamily = FontFamily.Serif,
                                        fontStyle = FontStyle.Italic,
                                    )
                                ) {
                                    append("\$${node.formula}\$")
                                }
                                pop()
                            }
                        }
                        is InlineNode.Link -> {
                            pushStringAnnotation(tag = "URL", annotation = node.url)
                            withStyle(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Normal,
                                )
                            ) {
                                appendInlinesInternal(node.children)
                            }
                            pop()
                        }
                        is InlineNode.Mention -> {
                            pushStringAnnotation(tag = "URL", annotation = node.url)
                            withStyle(
                                SpanStyle(
                                    color = linkColor,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            ) {
                                append(node.text)
                            }
                            pop()
                        }
                        is InlineNode.LineBreak -> append("\n")
                    }
                }
            }
            appendInlinesInternal(nodes)
        }
        Pair(text, inlineMap)
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val hasLinks = remember(annotatedString) {
        annotatedString.getStringAnnotations("URL", 0, annotatedString.length).isNotEmpty()
    }

    val gestureModifier = if (hasLinks) {
        Modifier.pointerInput(annotatedString, onLinkClick, onLongClick) {
            detectTapGestures(
                onTap = { offset ->
                    textLayoutResult?.let { layoutResult ->
                        val position = layoutResult.getOffsetForPosition(offset)
                        annotatedString.getStringAnnotations("URL", position, position).firstOrNull()?.let { annotation ->
                            val url = annotation.item
                            if (onLinkClick != null) {
                                onLinkClick(url)
                            } else {
                                try {
                                    uriHandler.openUri(url)
                                } catch (_: Exception) {
                                    // ignore malformed uri
                                }
                            }
                        }
                    }
                },
                onLongPress = {
                    onLongClick?.invoke()
                }
            )
        }
    } else {
        Modifier
    }

    Text(
        text = annotatedString,
        modifier = modifier.then(gestureModifier),
        style = style.copy(color = color),
        inlineContent = inlineContent,
        onTextLayout = { textLayoutResult = it },
    )
}

fun AnnotatedString.Builder.appendInlines(
    nodes: List<InlineNode>,
    linkColor: Color,
    codeBgColor: Color,
    mathColor: Color,
) {
    for (node in nodes) {
        when (node) {
            is InlineNode.Text -> append(node.value)
            is InlineNode.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInlines(node.children, linkColor, codeBgColor, mathColor)
            }
            is InlineNode.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlines(node.children, linkColor, codeBgColor, mathColor)
            }
            is InlineNode.Underline -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                appendInlines(node.children, linkColor, codeBgColor, mathColor)
            }
            is InlineNode.Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendInlines(node.children, linkColor, codeBgColor, mathColor)
            }
            is InlineNode.InlineCode -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBgColor)
            ) {
                append(" ${node.value} ")
            }
            is InlineNode.InlineMath -> {
                pushStringAnnotation(tag = "MATH", annotation = node.formula)
                withStyle(
                    SpanStyle(
                        color = mathColor,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                    )
                ) {
                    append("\$${node.formula}\$")
                }
                pop()
            }
            is InlineNode.Link -> {
                pushStringAnnotation(tag = "URL", annotation = node.url)
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    )
                ) {
                    appendInlines(node.children, linkColor, codeBgColor, mathColor)
                }
                pop()
            }
            is InlineNode.Mention -> {
                pushStringAnnotation(tag = "URL", annotation = node.url)
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                ) {
                    append(node.text)
                }
                pop()
            }
            is InlineNode.LineBreak -> append("\n")
        }
    }
}
