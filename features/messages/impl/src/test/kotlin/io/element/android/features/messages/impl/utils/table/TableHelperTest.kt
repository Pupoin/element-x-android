/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils.table

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.ui.messages.toHtmlDocument
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class TableHelperTest : RobolectricTest() {
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
    fun `toMarkdown - converts TableData to valid GFM markdown table`() {
        val tableData = TableData(
            headers = listOf("Col 1", "Col 2"),
            rows = listOf(
                listOf("Val 1", "Val 2"),
                listOf("Val 3", "Val 4"),
            )
        )
        val md = TableHelper.toMarkdown(tableData)
        assertThat(md).contains("| Col 1 | Col 2 |")
        assertThat(md).contains("| --- | --- |")
        assertThat(md).contains("| Val 1 | Val 2 |")
        assertThat(md).contains("| Val 3 | Val 4 |")
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
