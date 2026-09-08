/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils.heading

import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import androidx.core.text.getSpans
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.jsoup.Jsoup
import org.junit.Test

class HeadingHelperTest : RobolectricTest() {

    @Test
    fun `enrichHeadings - applies RelativeSizeSpan to h1 and h2 from HTML document`() {
        val html = """
            <h1>Main Title</h1>
            <p>Some paragraph</p>
            <h2>Subtitle Section</h2>
        """.trimIndent()

        val text = "Main Title\nSome paragraph\nSubtitle Section"
        val document = Jsoup.parseBodyFragment(html)
        val result = HeadingHelper.enrichHeadings(text, document)

        val spanned = result as Spanned
        val spans = spanned.getSpans<RelativeSizeSpan>(0, spanned.length)
        assertThat(spans).hasLength(2)

        val h1Start = spanned.getSpanStart(spans[0])
        val h1End = spanned.getSpanEnd(spans[0])
        assertThat(spanned.subSequence(h1Start, h1End).toString()).isEqualTo("Main Title")
        assertThat(spans[0].sizeChange).isGreaterThan(1.3f)

        val h2Start = spanned.getSpanStart(spans[1])
        val h2End = spanned.getSpanEnd(spans[1])
        assertThat(spanned.subSequence(h2Start, h2End).toString()).isEqualTo("Subtitle Section")
        assertThat(spans[1].sizeChange).isGreaterThan(1.2f)
    }

    @Test
    fun `enrichHeadings - applies RelativeSizeSpan to markdown headings in plain text`() {
        val markdown = "## 一、设备首次登录：生成身份密钥\n这是正文内容。"
        val result = HeadingHelper.enrichHeadings(markdown, null)

        val spanned = result as Spanned
        val spans = spanned.getSpans<RelativeSizeSpan>(0, spanned.length)
        assertThat(spans).hasLength(1)

        val start = spanned.getSpanStart(spans[0])
        val end = spanned.getSpanEnd(spans[0])
        assertThat(spanned.subSequence(start, end).toString()).isEqualTo("一、设备首次登录：生成身份密钥")
    }
}
