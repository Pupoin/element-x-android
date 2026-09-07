/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils.latex

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * A [ReplacementSpan] that renders a LaTeX formula using [JLatexMathDrawable].
 * The underlying CharSequence retains the original LaTeX string (e.g. "$E=mc^2$" or "$$...$$"),
 * ensuring that any range-selection or copy action accurately extracts the original LaTeX code.
 */
class LatexFormulaSpan(
    val rawLatex: String,
    val isBlock: Boolean = false,
) : ReplacementSpan() {
    private var cachedDrawable: JLatexMathDrawable? = null
    private var cachedTextSize: Float = 0f
    private var cachedColor: Int = 0

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(20, 128, 128, 128)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.argb(45, 128, 128, 128)
    }

    private fun getDrawable(paint: Paint): JLatexMathDrawable? {
        val textSize = if (isBlock) paint.textSize * 1.15f else paint.textSize
        val color = paint.color
        if (cachedDrawable == null || cachedTextSize != textSize || cachedColor != color) {
            try {
                val cleanLatex = LatexHelper.cleanFormula(rawLatex)
                cachedDrawable = JLatexMathDrawable.builder(cleanLatex)
                    .textSize(textSize)
                    .color(color)
                    .align(if (isBlock) JLatexMathDrawable.ALIGN_CENTER else JLatexMathDrawable.ALIGN_LEFT)
                    .build()
                cachedTextSize = textSize
                cachedColor = color
            } catch (_: Throwable) {
                cachedDrawable = null
            }
        }
        return cachedDrawable
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val drawable = getDrawable(paint)
        if (drawable == null) {
            return paint.measureText(text, start, end).toInt()
        }

        val paddingH = if (isBlock) 16 else 6
        val paddingV = if (isBlock) 6 else 2

        val w = drawable.intrinsicWidth + paddingH * 2
        val h = drawable.intrinsicHeight + paddingV * 2

        if (fm != null) {
            val fontMetrics = paint.fontMetricsInt
            val fontHeight = fontMetrics.descent - fontMetrics.ascent
            val fontCenter = fontMetrics.ascent + fontHeight / 2
            val halfH = h / 2
            val ascent = fontCenter - halfH
            val descent = fontCenter + halfH
            fm.ascent = minOf(fm.ascent, ascent)
            fm.top = minOf(fm.top, ascent)
            fm.descent = maxOf(fm.descent, descent)
            fm.bottom = maxOf(fm.bottom, descent)
        }
        return w
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val drawable = getDrawable(paint)
        if (drawable == null) {
            canvas.drawText(text ?: "", start, end, x, y.toFloat(), paint)
            return
        }

        val paddingH = if (isBlock) 16 else 6
        val paddingV = if (isBlock) 6 else 2

        val w = drawable.intrinsicWidth + paddingH * 2
        val h = drawable.intrinsicHeight + paddingV * 2

        // Standard Android DynamicDrawableSpan paradigm:
        // 1. Vertically center the box within the line bounds [top, bottom]
        val drawY = top + (bottom - top - h) / 2
        val rect = RectF(x, drawY.toFloat(), x + w, (drawY + h).toFloat())

        val cornerRadius = 8f
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        // 2. Translate canvas directly to the target origin, ensuring JLatexMath
        // (which paints at 0, 0) draws precisely within the rounded box.
        val left = x + paddingH
        val topPos = (drawY + paddingV).toFloat()

        canvas.save()
        canvas.translate(left, topPos)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(canvas)
        canvas.restore()
    }
}
