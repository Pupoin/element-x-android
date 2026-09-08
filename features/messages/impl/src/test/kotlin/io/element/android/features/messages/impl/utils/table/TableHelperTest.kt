/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils.table

import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.jsoup.Jsoup
import org.junit.Test

class TableHelperTest : RobolectricTest() {

    @Test
    fun `preprocessHtmlDocument - converts table with thead and tbody to markdown`() {
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
        TableHelper.preprocessHtmlDocument(document)

        assertThat(document.select("table")).isEmpty()
        val paragraphs = document.select("p")
        assertThat(paragraphs).hasSize(3)

        val tableParagraph = paragraphs[1].text()
        assertThat(tableParagraph).contains("| Key | Usage |")
        assertThat(tableParagraph).contains("| Ed25519 | Signature |")
        assertThat(tableParagraph).contains("| Curve25519 | ECDH |")
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

    @Test
    fun `splitByTables - separates text and table segments`() {
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
}
