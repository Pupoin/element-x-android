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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.model.ast.code.DefaultCodeSyntaxHighlighter
import io.element.android.features.messages.impl.timeline.model.ast.code.HighlightState
import io.element.android.features.messages.impl.timeline.model.ast.viewer.CodeFullscreenViewer
import io.element.android.libraries.ui.strings.CommonStrings

private const val MAX_COLLAPSED_LINES = 18

@Composable
fun InteractiveCodeCard(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isDark = !ElementTheme.isLightTheme
    val scrollState = rememberScrollState()

    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    val allLines = remember(code) { code.lines() }
    val isTruncated = allLines.size > MAX_COLLAPSED_LINES
    val displayCode = remember(code, isTruncated) {
        if (isTruncated) {
            allLines.take(MAX_COLLAPSED_LINES).joinToString("\n")
        } else {
            code
        }
    }

    var highlightState by remember(displayCode, language, isDark) {
        mutableStateOf<HighlightState>(HighlightState.Loading)
    }

    LaunchedEffect(displayCode, language, isDark) {
        try {
            val highlighted = DefaultCodeSyntaxHighlighter.highlight(displayCode, language, isDark)
            highlightState = HighlightState.Ready(highlighted)
        } catch (_: Throwable) {
            highlightState = HighlightState.Failed(displayCode)
        }
    }

    val copyAction = {
        val clipboard = context.getSystemService<ClipboardManager>()
        clipboard?.setPrimaryClip(ClipData.newPlainText("Code", code))
        Toast.makeText(context, context.getString(CommonStrings.common_copied_code), Toast.LENGTH_SHORT).show()
    }

    val gestureModifier = if (onLongClick != null) {
        Modifier.pointerInput(onLongClick) {
            detectTapGestures(
                onLongPress = { onLongClick() }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(gestureModifier)
            .background(Color(0x10808080), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x2D808080), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0x22808080), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = (language ?: "CODE").uppercase(),
                        style = ElementTheme.typography.fontBodyXsMedium,
                        color = ElementTheme.colors.textSecondary,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0x18808080), RoundedCornerShape(4.dp))
                            .clickable(onClick = copyAction)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(CommonStrings.action_copy),
                            style = ElementTheme.typography.fontBodyXsMedium,
                            color = ElementTheme.colors.textActionAccent,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0x18808080), RoundedCornerShape(4.dp))
                            .clickable { isFullscreen = true }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(CommonStrings.action_fullscreen),
                            style = ElementTheme.typography.fontBodyXsMedium,
                            color = ElementTheme.colors.textActionAccent,
                        )
                    }
                }
            }

            val lineCount = remember(displayCode) { displayCode.lines().size }
            val lineNumbersText = remember(lineCount) { (1..lineCount).joinToString("\n") }
            val codeTextStyle = ElementTheme.typography.fontBodyMdRegular.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                DisableSelection {
                    Text(
                        text = lineNumbersText,
                        style = codeTextStyle,
                        color = ElementTheme.colors.textSecondary.copy(alpha = 0.5f),
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .widthIn(min = 20.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState)
                ) {
                    when (val state = highlightState) {
                        is HighlightState.Ready -> {
                            Text(
                                text = state.text,
                                style = codeTextStyle,
                                color = ElementTheme.colors.textPrimary,
                                softWrap = false,
                            )
                        }
                        else -> {
                            Text(
                                text = displayCode,
                                style = codeTextStyle,
                                color = ElementTheme.colors.textPrimary,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            if (isTruncated) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(Color(0x14808080), RoundedCornerShape(4.dp))
                        .clickable { isFullscreen = true }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(CommonStrings.action_code_expand_all, allLines.size),
                        style = ElementTheme.typography.fontBodySmMedium,
                        color = ElementTheme.colors.textActionAccent,
                    )
                }
            }
        }
    }

    if (isFullscreen) {
        CodeFullscreenViewer(
            code = code,
            language = language,
            onDismiss = { isFullscreen = false }
        )
    }
}
