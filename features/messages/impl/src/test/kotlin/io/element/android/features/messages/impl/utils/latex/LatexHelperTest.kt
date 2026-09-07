/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils.latex

import android.text.SpannableString
import android.text.Spanned
import androidx.core.text.getSpans
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.wysiwyg.view.spans.InlineCodeSpan
import org.jsoup.Jsoup
import org.junit.Test

class LatexHelperTest : RobolectricTest() {
    @Test
    fun `renderLatexSpans - parses inline formula correctly and preserves raw text`() {
        val text = "Mass-energy equivalence is \$E=mc^2\$."
        val result = LatexHelper.renderLatexSpans(text)
        val spanned = result as Spanned
        val spans = spanned.getSpans<LatexFormulaSpan>(0, spanned.length)

        assertThat(spans).hasLength(1)
        val span = spans.first()
        assertThat(span.rawLatex).isEqualTo("E=mc^2")
        assertThat(span.isBlock).isFalse()

        val start = spanned.getSpanStart(span)
        val end = spanned.getSpanEnd(span)
        assertThat(start).isEqualTo(text.indexOf("\$E=mc^2\$"))
        assertThat(end).isEqualTo(start + "\$E=mc^2\$".length)

        // Verify that copying the selected range yields the exact LaTeX string
        val copiedSlice = spanned.subSequence(start, end).toString()
        assertThat(copiedSlice).isEqualTo("\$E=mc^2\$")
    }

    @Test
    fun `renderLatexSpans - parses block formula correctly`() {
        val text = "Formula:\n\$\$\\frac{a}{b} = c\$\$\nDone."
        val result = LatexHelper.renderLatexSpans(text)
        val spanned = result as Spanned
        val spans = spanned.getSpans<LatexFormulaSpan>(0, spanned.length)

        assertThat(spans).hasLength(1)
        val span = spans.first()
        assertThat(span.rawLatex).isEqualTo("\\frac{a}{b} = c")
        assertThat(span.isBlock).isTrue()
    }

    @Test
    fun `renderLatexSpans - ignores currency like $100`() {
        val text = "Price is \$100 and not a formula."
        val result = LatexHelper.renderLatexSpans(text)
        val spanned = result as Spanned
        val spans = spanned.getSpans<LatexFormulaSpan>(0, spanned.length)

        assertThat(spans).isEmpty()
    }

    @Test
    fun `renderLatexSpans - does not apply formula span inside inline code`() {
        val text = SpannableString("Here is `val s = \"\$foo\"` code")
        val codeStart = text.indexOf("val")
        val codeEnd = text.indexOf("code") - 1
        text.setSpan(InlineCodeSpan(), codeStart, codeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val result = LatexHelper.renderLatexSpans(text)
        val spanned = result as Spanned
        val spans = spanned.getSpans<LatexFormulaSpan>(0, spanned.length)

        assertThat(spans).isEmpty()
    }

    @Test
    fun `preprocessHtmlDocument - normalizes data-mx-maths attributes`() {
        val html = """<p>Text <span data-mx-maths="x^2 + y^2 = z^2">math</span> and <div data-mx-maths="\int_0^1 x dx">block</div></p>"""
        val doc = Jsoup.parse(html)
        LatexHelper.preprocessHtmlDocument(doc)

        assertThat(doc.body().text()).contains("\$x^2 + y^2 = z^2\$")
        assertThat(doc.body().text()).contains("\$\$\\int_0^1 x dx\$\$")
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun `test html conversion with LatexHelper for both span and div`() = androidx.compose.ui.test.v2.runComposeUiTest {
        val provider = io.element.android.features.messages.impl.timeline.DefaultHtmlConverterProvider(
            mentionSpanProvider = io.element.android.libraries.textcomposer.mentions.MentionSpanProvider(
                permalinkParser = io.element.android.libraries.matrix.test.permalink.FakePermalinkParser(),
                mentionSpanFormatter = io.element.android.features.messages.impl.utils.FakeMentionSpanFormatter(),
                mentionSpanTheme = io.element.android.libraries.textcomposer.mentions.MentionSpanTheme(io.element.android.libraries.matrix.test.A_USER_ID)
            )
        )
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                provider.Update()
            }
        }
        val converter = provider.provide()

        // 1. Test inline formula from Element Web: <span data-mx-maths="\sigma">...</span>
        val spanHtml = """<p>Hello <span data-mx-maths="\sigma"><code>\sigma</code></span> world</p>"""
        val spanDoc = Jsoup.parse(spanHtml)
        LatexHelper.preprocessHtmlDocument(spanDoc)
        val spanSpans = converter.fromDocumentToSpans(spanDoc)
        val spanWithLatex = LatexHelper.renderLatexSpans(spanSpans)
        val spanFormulaSpans = (spanWithLatex as Spanned).getSpans<LatexFormulaSpan>(0, spanWithLatex.length)
        assertThat(spanFormulaSpans).hasLength(1)
        assertThat(spanFormulaSpans.first().rawLatex).isEqualTo("\\sigma")
        assertThat(spanFormulaSpans.first().isBlock).isFalse()

        // 2. Test block formula: <div data-mx-maths="E=mc^2">E=mc²</div>
        val divHtml = """<div data-mx-maths="E=mc^2">E=mc²</div>"""
        val divDoc = Jsoup.parse(divHtml)
        LatexHelper.preprocessHtmlDocument(divDoc)
        val divSpans = converter.fromDocumentToSpans(divDoc)
        val divWithLatex = LatexHelper.renderLatexSpans(divSpans)
        val divFormulaSpans = (divWithLatex as Spanned).getSpans<LatexFormulaSpan>(0, divWithLatex.length)
        assertThat(divFormulaSpans).hasLength(1)
        assertThat(divFormulaSpans.first().rawLatex).isEqualTo("E=mc^2")
        assertThat(divFormulaSpans.first().isBlock).isTrue()
    }

    @Test
    fun `formatLatexToHtml - converts inline formula to MSC2191 HTML format`() {
        val markdown = "Hello \$\\sigma\$ world"
        val html = LatexHelper.formatLatexToHtml(markdown)
        assertThat(html).isEqualTo("Hello <span data-mx-maths=\"\\sigma\"><code>\\sigma</code></span> world")
    }

    @Test
    fun `formatLatexToHtml - converts block formula to MSC2191 HTML format`() {
        val markdown = "\$\$\\int_0^1 x dx\$\$"
        val html = LatexHelper.formatLatexToHtml(markdown)
        assertThat(html).isEqualTo("<div data-mx-maths=\"\\int_0^1 x dx\"><pre><code>\\int_0^1 x dx</code></pre></div>")
    }

    @Test
    fun `formatLatexToHtml - ignores formulas inside code blocks`() {
        val markdown = "Here is `\$foo\$` code"
        val html = LatexHelper.formatLatexToHtml(markdown)
        assertThat(html).isNull()
    }

    @Test
    fun `formatLatexToHtml - ignores currency like $100`() {
        val markdown = "Price is \$100"
        val html = LatexHelper.formatLatexToHtml(markdown)
        assertThat(html).isNull()
    }

    @Test
    fun `renderLatexSpans - parses align star block formula correctly`() {
        ru.noties.jlatexmath.JLatexMathAndroid.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        val formula = """
            \begin{align*}
            \mathcal{L}_{SM} &= -\frac{1}{2}\partial_\nu g_\mu^a g^{\mu\nu}_a - g_s f^{abc} \partial_\mu g_\nu^a g_b^\mu g_c^\nu - \frac{1}{4} g_s^2 f^{abc} f^{ade} g_b^\mu g_c^\nu g_{d\mu} g_{e\nu} \\
            &+ \frac{1}{2} i g_s^2 (\bar{q}_i^\sigma \gamma^\mu q_j^\sigma) g_\mu^a + \bar{G}^a \partial^2 G^a + g_s f^{abc} \partial_\mu \bar{G}^a G^b g_c^\mu
            \end{align*}
        """.trimIndent()
        val text = "$$$formula$$"
        val result = LatexHelper.renderLatexSpans(text)
        val spanned = result as Spanned
        val spans = spanned.getSpans<LatexFormulaSpan>(0, spanned.length)
        assertThat(spans).hasLength(1)
        assertThat(spans.first().isBlock).isTrue()
    }

    @Test
    fun `splitByBlockMath - splits text and block math correctly`() {
        val text = "Hello\n\n\$\$\\frac{a}{b}\$\$\n\nWorld"
        val segments = LatexHelper.splitByBlockMath(text)
        assertThat(segments).hasSize(3)
        assertThat((segments[0] as LatexHelper.TextSegment.Text).text.toString()).isEqualTo("Hello")
        assertThat((segments[1] as LatexHelper.TextSegment.BlockMath).formula).isEqualTo("\\frac{a}{b}")
        assertThat((segments[2] as LatexHelper.TextSegment.Text).text.toString()).isEqualTo("World")
    }

    @Test
    fun `splitByBlockMath - returns single BlockMath for pure block formula`() {
        val text = "\$\$\\int_0^1 x dx\$\$"
        val segments = LatexHelper.splitByBlockMath(text)
        assertThat(segments).hasSize(1)
        assertThat((segments[0] as LatexHelper.TextSegment.BlockMath).formula).isEqualTo("\\int_0^1 x dx")
    }

    @Test
    fun `splitByBlockMath - returns single Text for message without block math`() {
        val text = "Hello with \$inline\$ math"
        val segments = LatexHelper.splitByBlockMath(text)
        assertThat(segments).hasSize(1)
        assertThat((segments[0] as LatexHelper.TextSegment.Text).text.toString()).isEqualTo(text)
    }
}
