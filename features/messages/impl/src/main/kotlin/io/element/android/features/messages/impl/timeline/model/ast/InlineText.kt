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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import io.element.android.compound.theme.ElementTheme

@Composable
fun InlineText(
    nodes: List<InlineNode>,
    modifier: Modifier = Modifier,
    style: TextStyle = ElementTheme.typography.fontBodyLgRegular,
    color: Color = ElementTheme.colors.textPrimary,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val linkColor = ElementTheme.colors.textActionAccent
    val codeBgColor = Color(0x18808080)
    val mathColor = ElementTheme.colors.textActionAccent

    val annotatedString = buildAnnotatedString {
        appendInlines(
            nodes = nodes,
            linkColor = linkColor,
            codeBgColor = codeBgColor,
            mathColor = mathColor,
        )
    }

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = style.copy(color = color),
        onClick = { offset ->
            annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { annotation ->
                val url = annotation.item
                if (onLinkClick != null) {
                    onLinkClick(url)
                } else {
                    try {
                        uriHandler.openUri(url)
                    } catch (e: Exception) {
                        // ignore malformed uri
                    }
                }
            }
            annotatedString.getStringAnnotations("MATH", offset, offset).firstOrNull()?.let { annotation ->
                val formula = annotation.item
                val clipboard = context.getSystemService<ClipboardManager>()
                clipboard?.setPrimaryClip(ClipData.newPlainText("LaTeX Formula", formula))
                Toast.makeText(context, "已复制公式: $formula", Toast.LENGTH_SHORT).show()
            }
        }
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
            is InlineNode.Strong -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInlines(node.children, linkColor, codeBgColor, mathColor)
                }
            }
            is InlineNode.Emphasis -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInlines(node.children, linkColor, codeBgColor, mathColor)
                }
            }
            is InlineNode.Underline -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    appendInlines(node.children, linkColor, codeBgColor, mathColor)
                }
            }
            is InlineNode.Strikethrough -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendInlines(node.children, linkColor, codeBgColor, mathColor)
                }
            }
            is InlineNode.InlineCode -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBgColor,
                    )
                ) {
                    append(" ${node.value} ")
                }
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
                    append("$${node.formula}$")
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
