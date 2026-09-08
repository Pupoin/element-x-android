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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.getSystemService
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.utils.latex.LatexHelper
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.ui.strings.CommonStrings
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormulaFullscreenViewer(
    rawFormula: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val textColor = ElementTheme.colors.textPrimary
    val textSize = with(LocalDensity.current) { 22.sp.toPx() }

    val clean = remember(rawFormula) {
        LatexHelper.cleanFormula(rawFormula)
    }

    val drawable = remember(clean, textColor, textSize) {
        try {
            JLatexMathAndroid.init(context.applicationContext)
            JLatexMathDrawable.builder(clean)
                .textSize(textSize)
                .color(textColor.toArgb())
                .align(JLatexMathDrawable.ALIGN_CENTER)
                .build()
        } catch (t: Throwable) {
            Timber.e(t, "FormulaFullscreenViewer failed to render formula: %s", clean)
            null
        }
    }

    val copyAction = {
        val clipboard = context.getSystemService<ClipboardManager>()
        clipboard?.setPrimaryClip(ClipData.newPlainText("LaTeX Formula", clean))
        Toast.makeText(context, context.getString(CommonStrings.common_copied_latex), Toast.LENGTH_SHORT).show()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var fitScale by remember { mutableFloatStateOf(1f) }
    var hasInitializedFit by remember(clean) { mutableStateOf(false) }

    val formulaWidthPx = drawable?.intrinsicWidth?.toFloat() ?: 0f
    val formulaHeightPx = drawable?.intrinsicHeight?.toFloat() ?: 0f

    fun clampOffset(proposed: Offset, currentScale: Float, viewportW: Float, viewportH: Float): Offset {
        val scaledW = formulaWidthPx * currentScale
        val scaledH = formulaHeightPx * currentScale
        val maxOffsetX = maxOf(0f, (scaledW - viewportW) / 2f)
        val maxOffsetY = maxOf(0f, (scaledH - viewportH) / 2f)
        return Offset(
            x = proposed.x.coerceIn(-maxOffsetX, maxOffsetX),
            y = proposed.y.coerceIn(-maxOffsetY, maxOffsetY),
        )
    }

    fun updateScale(targetScale: Float, viewportW: Float, viewportH: Float) {
        val newScale = targetScale.coerceIn(0.15f, 5.0f)
        scale = newScale
        offset = clampOffset(offset, newScale, viewportW, viewportH)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val viewportWidthPx = constraints.maxWidth.toFloat()
            val viewportHeightPx = constraints.maxHeight.toFloat()

            LaunchedEffect(formulaWidthPx, formulaHeightPx, viewportWidthPx, viewportHeightPx) {
                if (!hasInitializedFit && formulaWidthPx > 0f && formulaHeightPx > 0f && viewportWidthPx > 0f && viewportHeightPx > 0f) {
                    val marginPx = 48f
                    val availW = (viewportWidthPx - marginPx).coerceAtLeast(100f)
                    val availH = (viewportHeightPx - marginPx).coerceAtLeast(100f)
                    val sX = availW / formulaWidthPx
                    val sY = availH / formulaHeightPx
                    val computedFit = minOf(1f, sX, sY).coerceIn(0.15f, 1f)
                    fitScale = computedFit
                    scale = computedFit
                    offset = Offset.Zero
                    hasInitializedFit = true
                }
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(CommonStrings.screen_formula_viewer_title),
                                style = ElementTheme.typography.fontHeadingSmMedium,
                                color = ElementTheme.colors.textPrimary,
                            )
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
                                onClick = { updateScale(scale - 0.25f, viewportWidthPx, viewportHeightPx) },
                                enabled = scale > 0.15f,
                            ) {
                                Icon(
                                    imageVector = CompoundIcons.Minus(),
                                    contentDescription = stringResource(CommonStrings.a11y_zoom_out),
                                    tint = if (scale > 0.15f) ElementTheme.colors.iconPrimary else ElementTheme.colors.iconDisabled,
                                )
                            }

                            TextButton(
                                onClick = {
                                    if (abs(scale - 1f) > 0.05f) {
                                        scale = 1f
                                        offset = clampOffset(Offset.Zero, 1f, viewportWidthPx, viewportHeightPx)
                                    } else if (fitScale < 0.95f) {
                                        scale = fitScale
                                        offset = Offset.Zero
                                    } else {
                                        scale = 1f
                                        offset = Offset.Zero
                                    }
                                }
                            ) {
                                Text(
                                    text = "${(scale * 100).roundToInt()}%",
                                    style = ElementTheme.typography.fontBodyMdMedium,
                                    color = ElementTheme.colors.textActionAccent,
                                )
                            }

                            IconButton(
                                onClick = { updateScale(scale + 0.25f, viewportWidthPx, viewportHeightPx) },
                                enabled = scale < 5.0f,
                            ) {
                                Icon(
                                    imageVector = CompoundIcons.Plus(),
                                    contentDescription = stringResource(CommonStrings.a11y_zoom_in),
                                    tint = if (scale < 5.0f) ElementTheme.colors.iconPrimary else ElementTheme.colors.iconDisabled,
                                )
                            }

                            IconButton(onClick = copyAction) {
                                Icon(
                                    imageVector = CompoundIcons.Copy(),
                                    contentDescription = stringResource(CommonStrings.action_copy_latex),
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
                        .pointerInput(viewportWidthPx, viewportHeightPx, formulaWidthPx, formulaHeightPx) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(0.15f, 5.0f)
                                scale = nextScale
                                offset = clampOffset(offset + pan, nextScale, viewportWidthPx, viewportHeightPx)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (drawable != null) {
                        Canvas(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val canvasW = size.width
                            val canvasH = size.height
                            if (canvasW <= 0f || canvasH <= 0f || formulaWidthPx <= 0f || formulaHeightPx <= 0f) return@Canvas

                            drawIntoCanvas { canvas ->
                                val nativeCanvas = canvas.nativeCanvas
                                nativeCanvas.save()
                                nativeCanvas.translate(canvasW / 2f + offset.x, canvasH / 2f + offset.y)
                                nativeCanvas.scale(scale, scale)
                                nativeCanvas.translate(-formulaWidthPx / 2f, -formulaHeightPx / 2f)
                                drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                                drawable.draw(nativeCanvas)
                                nativeCanvas.restore()
                            }
                        }
                    } else {
                        Text(
                            text = rawFormula,
                            style = ElementTheme.typography.fontBodyLgRegular,
                            color = ElementTheme.colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}
