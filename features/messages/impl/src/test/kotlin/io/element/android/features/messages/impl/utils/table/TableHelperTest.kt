/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils.table

import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.core.text.getSpans
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.ui.messages.toHtmlDocument
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.jsoup.Jsoup
import org.junit.Test

class TableHelperTest : RobolectricTest() {
    @Test
    fun `preprocessHtmlDocument - replaces table with placeholder token and returns ProcessedTable`() {
        val html = """
            <p>Intro text</p>
            <table>
                <thead>
                    <tr><th>Key</th><th>Usage</th></tr>
                </thead>
                <tbody>
                    <tr><td>Ed25519</td><td>Signature</td></tr>
                    <tr><td>Curve25519</td><td>ECDH</td></tr>
                </tbody>
            </table>
            <p>Outro text</p>
        """.trimIndent()

        val document = Jsoup.parseBodyFragment(html)
        val processedTables = TableHelper.preprocessHtmlDocument(document)

        assertThat(document.select("table")).isEmpty()
        assertThat(processedTables).hasSize(1)

        val pt = processedTables[0]
        assertThat(pt.token).startsWith("MATRIX_TABLE_TOKEN_")
        assertThat(pt.tableData.headers).containsExactly("Key", "Usage").inOrder()
        assertThat(pt.tableData.rows).hasSize(2)
        assertThat(pt.rawMarkdown).contains("| Key | Usage |")
        assertThat(pt.rawMarkdown).contains("| Ed25519 | Signature |")

        val paragraphs = document.select("p")
        assertThat(paragraphs).hasSize(3)
        assertThat(paragraphs[1].text()).isEqualTo(pt.token)
    }

    @Test
    fun `attachTableSpans - replaces token with markdown and attaches TableSpan`() {
        val tableData = TableData(headers = listOf("A", "B"), rows = listOf(listOf("1", "2")))
        val rawMarkdown = TableHelper.toMarkdown(tableData)
        val token = "MATRIX_TABLE_TOKEN_TEST"
        val processed = listOf(TableHelper.ProcessedTable(token, tableData, rawMarkdown))

        val spannable = SpannableStringBuilder("Prefix $token Suffix")
        val result = TableHelper.attachTableSpans(spannable, processed)

        val resultStr = result.toString()
        assertThat(resultStr).doesNotContain(token)
        assertThat(resultStr).contains(rawMarkdown)
        assertThat(resultStr).startsWith("Prefix | A | B |")

        val spanned = result as Spanned
        val spans = spanned.getSpans<TableSpan>(0, result.length)
        assertThat(spans).hasLength(1)
        assertThat(spans[0].tableData.headers).containsExactly("A", "B")
        assertThat(spans[0].tableData.rows[0]).containsExactly("1", "2")
    }

    @Test
    fun `splitByTables - splits text with TableSpan into segments`() {
        val tableData = TableData(headers = listOf("Col A", "Col B"), rows = listOf(listOf("1", "2")))
        val rawMarkdown = TableHelper.toMarkdown(tableData)
        val token = "MATRIX_TABLE_TOKEN_SPLIT"
        val processed = listOf(TableHelper.ProcessedTable(token, tableData, rawMarkdown))

        val spannable = SpannableStringBuilder("Intro message\n\n$token\n\nOutro message")
        val withSpans = TableHelper.attachTableSpans(spannable, processed)

        val segments = TableHelper.splitByTables(withSpans)
        assertThat(segments).hasSize(3)

        assertThat(segments[0]).isInstanceOf(TableHelper.TableSegment.Text::class.java)
        assertThat((segments[0] as TableHelper.TableSegment.Text).text.toString()).contains("Intro message")

        assertThat(segments[1]).isInstanceOf(TableHelper.TableSegment.Table::class.java)
        val tableSeg = segments[1] as TableHelper.TableSegment.Table
        assertThat(tableSeg.tableData.headers).containsExactly("Col A", "Col B")
        assertThat(tableSeg.tableData.rows[0]).containsExactly("1", "2")

        assertThat(segments[2]).isInstanceOf(TableHelper.TableSegment.Text::class.java)
        assertThat((segments[2] as TableHelper.TableSegment.Text).text.toString()).contains("Outro message")
    }

    @Test
    fun `splitByTables - fallback for plain text Markdown tables`() {
        val content = """
            Before table text.

            | Col A | Col B |
            | --- | --- |
            | 1 | 2 |

            After table text.
        """.trimIndent()

        val segments = TableHelper.splitByTables(content)
        assertThat(segments).hasSize(3)

        assertThat(segments[0]).isInstanceOf(TableHelper.TableSegment.Text::class.java)
        assertThat((segments[0] as TableHelper.TableSegment.Text).text.toString()).contains("Before table text.")

        assertThat(segments[1]).isInstanceOf(TableHelper.TableSegment.Table::class.java)
        val tableSeg = segments[1] as TableHelper.TableSegment.Table
        assertThat(tableSeg.tableData.headers).containsExactly("Col A", "Col B")
        assertThat(tableSeg.tableData.rows[0]).containsExactly("1", "2")

        assertThat(segments[2]).isInstanceOf(TableHelper.TableSegment.Text::class.java)
        assertThat((segments[2] as TableHelper.TableSegment.Text).text.toString()).contains("After table text.")
    }

    @Test
    fun `parseMarkdownTable - parses valid markdown table correctly`() {
        val md = """
            | 密钥 | 用途 | 私钥在哪里 |
            |---|---|---|
            | Ed25519 设备密钥 | 给设备公钥和数据签名 | 只在手机 A |
            | Curve25519 身份密钥 | 与其他设备建立加密通道 | 只在手机 A |
        """.trimIndent()

        val tableData = TableHelper.parseMarkdownTable(md)
        assertThat(tableData).isNotNull()
        assertThat(tableData!!.headers).containsExactly("密钥", "用途", "私钥在哪里").inOrder()
        assertThat(tableData.rows).hasSize(2)
        assertThat(tableData.rows[0]).containsExactly("Ed25519 设备密钥", "给设备公钥和数据签名", "只在手机 A").inOrder()
        assertThat(tableData.rows[1]).containsExactly("Curve25519 身份密钥", "与其他设备建立加密通道", "只在手机 A").inOrder()
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun `e2e - full html pipeline preserves table through StyledHtmlConverter`() = androidx.compose.ui.test.v2.runComposeUiTest {
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

        val html = """
            <p>下面用一个具体例子说明：</p>
            <p>Alice 在手机 A 登录 Matrix 时，手机本地生成：</p>
            <table>
            <thead>
            <tr>
            <th>密钥</th>
            <th>用途</th>
            <th>私钥在哪里</th>
            </tr>
            </thead>
            <tbody>
            <tr>
            <td>Ed25519 设备密钥</td>
            <td>给设备公钥和数据签名</td>
            <td>只在手机 A</td>
            </tr>
            <tr>
            <td>Curve25519 身份密钥</td>
            <td>与其他设备建立加密通道</td>
            <td>只在手机 A</td>
            </tr>
            </tbody>
            </table>
            <p>Bob 的手机 B 也生成自己的一套，二者完全不同。</p>
        """.trimIndent()

        val document = Jsoup.parse(html)
        val tables = TableHelper.preprocessHtmlDocument(document)
        val parsedSpans = converter.fromDocumentToSpans(document)
        val spansWithTable = TableHelper.attachTableSpans(parsedSpans, tables)

        val segments = TableHelper.splitByTables(spansWithTable)
        assertThat(segments.any { it is TableHelper.TableSegment.Table }).isTrue()

        val tableSeg = segments.filterIsInstance<TableHelper.TableSegment.Table>().first()
        assertThat(tableSeg.tableData.headers).containsExactly("密钥", "用途", "私钥在哪里")
        assertThat(tableSeg.tableData.rows).hasSize(2)
        assertThat(tableSeg.tableData.rows[0][0]).isEqualTo("Ed25519 设备密钥")
        assertThat(tableSeg.tableData.rows[1][0]).isEqualTo("Curve25519 身份密钥")
    }

    @Test
    fun `toHtmlDocument - preserves table and h2 tags without stripping`() {
        val html = """
            <h2>一、设备首次登录：生成身份密钥</h2>
            <table>
            <thead><tr><th>Key</th><th>Value</th></tr></thead>
            <tbody><tr><td>A</td><td>B</td></tr></tbody>
            </table>
        """.trimIndent()
        val formattedBody = io.element.android.libraries.matrix.api.timeline.item.event.FormattedBody(
            format = io.element.android.libraries.matrix.api.timeline.item.event.MessageFormat.HTML,
            body = html
        )
        val permalinkParser = io.element.android.libraries.matrix.test.permalink.FakePermalinkParser()
        val doc = with(formattedBody) {
            toHtmlDocument(permalinkParser)
        }
        assertThat(doc).isNotNull()
        assertThat(doc!!.select("h2")).hasSize(1)
        assertThat(doc.select("table")).hasSize(1)
        assertThat(doc.select("th")).hasSize(2)
        assertThat(doc.select("td")).hasSize(2)
    }
}
