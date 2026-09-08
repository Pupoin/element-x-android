/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils.heading

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import org.jsoup.nodes.Document

object HeadingHelper {
    private const val H1_SCALE = 1.35f
    private const val H2_SCALE = 1.22f
    private const val H3_SCALE = 1.15f
    private const val H4_SCALE = 1.08f
    private const val H5_SCALE = 1.04f

    /**
     * Enhances headings (h1..h6) in [charSequence] with proportional [RelativeSizeSpan]s
     * based on the structure of [document].
     */
    fun enrichHeadings(charSequence: CharSequence, document: Document?): CharSequence {
        if (document == null) {
            return enrichMarkdownHeadings(charSequence)
        }

        val headings = document.select("h1, h2, h3, h4, h5, h6")
        if (headings.isEmpty()) {
            return enrichMarkdownHeadings(charSequence)
        }

        val spannable = charSequence as? Spannable ?: SpannableStringBuilder(charSequence)
        val text = spannable.toString()
        var searchStart = 0

        for (heading in headings) {
            val headingText = heading.text().trim()
            if (headingText.isEmpty()) continue

            val scale = when (heading.tagName().lowercase()) {
                "h1" -> H1_SCALE
                "h2" -> H2_SCALE
                "h3" -> H3_SCALE
                "h4" -> H4_SCALE
                "h5" -> H5_SCALE
                else -> 1.02f
            }

            val index = text.indexOf(headingText, searchStart)
            if (index != -1) {
                spannable.setSpan(
                    RelativeSizeSpan(scale),
                    index,
                    index + headingText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                searchStart = index + headingText.length
            }
        }

        return spannable
    }

    /**
     * Fallback for plain text messages containing Markdown headings (# Heading, ## Heading).
     */
    private fun enrichMarkdownHeadings(charSequence: CharSequence): CharSequence {
        val markdownHeadingRegex = Regex("""(?m)^(#{1,6})\s+(.+)$""")
        val matches = markdownHeadingRegex.findAll(charSequence).toList()
        if (matches.isEmpty()) return charSequence

        val spannable = charSequence as? Spannable ?: SpannableStringBuilder(charSequence)
        for (match in matches) {
            val hashes = match.groupValues[1]
            val contentGroup = match.groups[2] ?: continue
            val scale = when (hashes.length) {
                1 -> H1_SCALE
                2 -> H2_SCALE
                3 -> H3_SCALE
                4 -> H4_SCALE
                5 -> H5_SCALE
                else -> 1.02f
            }
            spannable.setSpan(
                RelativeSizeSpan(scale),
                contentGroup.range.first,
                contentGroup.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                contentGroup.range.first,
                contentGroup.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }
}
