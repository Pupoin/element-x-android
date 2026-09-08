/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.model.ast.viewer.FormulaFullscreenViewer
import io.element.android.features.messages.impl.utils.latex.LatexHelper
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable
import timber.log.Timber

/**
 * A horizontally-scrollable Compose card that renders large block LaTeX math formulas
 * at their natural size without down-scaling or clipping.
 */
@Composable
fun LatexBlockMathView(
    rawFormula: String,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val textColor = ElementTheme.colors.textPrimary
    val textSize = with(LocalDensity.current) { 18.sp.toPx() }

    var isFullscreen by rememberSaveable { mutableStateOf(false) }

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
            Timber.e(t, "LatexBlockMathView failed to render formula: %s", clean)
            null
        }
    }

    val scrollState = rememberScrollState()

    val copyAction = {
        val full = "\$\$$rawFormula\$\$"
        val clipboard = context.getSystemService<ClipboardManager>()
        clipboard?.setPrimaryClip(ClipData.newPlainText("LaTeX Formula", full))
        Toast.makeText(context, "已复制 LaTeX 公式", Toast.LENGTH_SHORT).show()
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
                        text = "LATEX",
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
                            text = "复制 LaTeX",
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
                            text = "全屏",
                            style = ElementTheme.typography.fontBodyXsMedium,
                            color = ElementTheme.colors.textActionAccent,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (drawable != null) {
                    val widthDp = with(LocalDensity.current) { drawable.intrinsicWidth.toDp() }
                    val heightDp = with(LocalDensity.current) { drawable.intrinsicHeight.toDp() }
                    Canvas(
                        modifier = Modifier.size(width = widthDp, height = heightDp)
                    ) {
                        drawIntoCanvas { canvas ->
                            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                            drawable.draw(canvas.nativeCanvas)
                        }
                    }
                } else {
                    Text(
                        text = rawFormula,
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textPrimary,
                    )
                }
            }
        }
    }

    if (isFullscreen) {
        FormulaFullscreenViewer(
            rawFormula = rawFormula,
            onDismiss = { isFullscreen = false }
        )
    }
}
