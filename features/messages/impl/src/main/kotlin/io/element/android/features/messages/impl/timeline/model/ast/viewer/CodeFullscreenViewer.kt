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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.getSystemService
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.model.ast.code.DefaultCodeSyntaxHighlighter
import io.element.android.features.messages.impl.timeline.model.ast.code.HighlightState
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.ui.strings.CommonStrings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeFullscreenViewer(
    code: String,
    language: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = !ElementTheme.isLightTheme
    val lines = remember(code) { code.lines() }
    val displayLang = language?.takeIf { it.isNotBlank() }?.uppercase() ?: "CODE"

    var fontScale by remember { mutableFloatStateOf(1f) }
    val currentFontSize = (13f * fontScale).sp
    val currentLineHeight = (18f * fontScale).sp

    var highlightState by remember(code, language, isDark) {
        mutableStateOf<HighlightState>(HighlightState.Loading)
    }

    LaunchedEffect(code, language, isDark) {
        if (code.length <= 100_000 && lines.size <= 2_000) {
            try {
                val highlighted = DefaultCodeSyntaxHighlighter.highlight(code, language, isDark)
                highlightState = HighlightState.Ready(highlighted)
            } catch (_: Throwable) {
                highlightState = HighlightState.Failed(code)
            }
        } else {
            highlightState = HighlightState.Failed(code)
        }
    }

    val copyAction = {
        val clipboard = context.getSystemService<ClipboardManager>()
        clipboard?.setPrimaryClip(ClipData.newPlainText("Code Block", code))
        Toast.makeText(context, context.getString(CommonStrings.common_copied_code), Toast.LENGTH_SHORT).show()
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
                        Column {
                            Text(
                                text = displayLang,
                                style = ElementTheme.typography.fontHeadingSmMedium,
                                color = ElementTheme.colors.textPrimary,
                            )
                            Text(
                                text = stringResource(CommonStrings.screen_code_viewer_lines, lines.size),
                                style = ElementTheme.typography.fontBodyXsRegular,
                                color = ElementTheme.colors.textSecondary,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = CompoundIcons.ArrowLeft(),
                                contentDescription = stringResource(CommonStrings.action_back),
                                tint = ElementTheme.colors.iconPrimary,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                fontScale = (fontScale - 0.15f).coerceIn(0.75f, 2.5f)
                            },
                            enabled = fontScale > 0.75f,
                        ) {
                            Icon(
                                imageVector = CompoundIcons.Minus(),
                                contentDescription = stringResource(CommonStrings.a11y_zoom_out),
                                tint = if (fontScale > 0.75f) ElementTheme.colors.iconPrimary else ElementTheme.colors.iconDisabled,
                            )
                        }

                        TextButton(
                            onClick = { fontScale = 1.0f }
                        ) {
                            Text(
                                text = "${(fontScale * 100).roundToInt()}%",
                                style = ElementTheme.typography.fontBodyMdMedium,
                                color = ElementTheme.colors.textActionAccent,
                            )
                        }

                        IconButton(
                            onClick = {
                                fontScale = (fontScale + 0.15f).coerceIn(0.75f, 2.5f)
                            },
                            enabled = fontScale < 2.5f,
                        ) {
                            Icon(
                                imageVector = CompoundIcons.Plus(),
                                contentDescription = stringResource(CommonStrings.a11y_zoom_in),
                                tint = if (fontScale < 2.5f) ElementTheme.colors.iconPrimary else ElementTheme.colors.iconDisabled,
                            )
                        }

                        IconButton(onClick = copyAction) {
                            Icon(
                                imageVector = CompoundIcons.Copy(),
                                contentDescription = stringResource(CommonStrings.action_copy_code),
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
            val verticalScrollState = rememberScrollState()
            val horizontalScrollState = rememberScrollState()

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val contentMinHeight = (maxHeight - 32.dp).coerceAtLeast(0.dp)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                do {
                                    val event = awaitPointerEvent()
                                    val pressedChanges = event.changes.filter { it.pressed }
                                    if (pressedChanges.size >= 2) {
                                        val zoom = event.calculateZoom()
                                        if (zoom != 1f) {
                                            fontScale = (fontScale * zoom).coerceIn(0.75f, 2.5f)
                                            pressedChanges.forEach { it.consume() }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .verticalScroll(verticalScrollState)
                        .padding(16.dp)
                ) {
                    val lineCount = lines.size
                    val lineNumbersText = remember(lineCount) { (1..lineCount).joinToString("\n") }
                    val textStyle = ElementTheme.typography.fontBodyMdRegular.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = currentFontSize,
                        lineHeight = currentLineHeight,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = contentMinHeight)
                    ) {
                        DisableSelection {
                            Text(
                                text = lineNumbersText,
                                style = textStyle,
                                color = ElementTheme.colors.textSecondary.copy(alpha = 0.5f),
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .widthIn(min = 24.dp),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = contentMinHeight)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            SelectionContainer {
                                when (val state = highlightState) {
                                    is HighlightState.Ready -> {
                                        Text(
                                            text = state.text,
                                            style = textStyle,
                                            color = ElementTheme.colors.textPrimary,
                                            softWrap = false,
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = code,
                                            style = textStyle,
                                            color = ElementTheme.colors.textPrimary,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
