/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MessageAstParserTest {
    @Test
    fun `HtmlToMessageAstParser - parses heading, table, codeblock, and lists`() {
        val html = """
            <h2>一、设备首次登录：生成身份密钥</h2>
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
            <p>然后设备调用类似下面的接口：</p>
            <pre><code class="language-text">POST /_matrix/client/v3/keys/upload</code></pre>
            <ol>
            <li>找到对应的一次性私钥；</li>
            <li>建立入站 Olm 会话；</li>
            </ol>
        """.trimIndent()

        val blocks = HtmlToMessageAstParser.parse(html)
        assertThat(blocks).hasSize(6)

        // 1. Heading
        assertThat(blocks[0]).isInstanceOf(MessageBlock.Heading::class.java)
        val heading = blocks[0] as MessageBlock.Heading
        assertThat(heading.level).isEqualTo(2)
        assertThat((heading.children.first() as InlineNode.Text).value).isEqualTo("一、设备首次登录：生成身份密钥")

        // 2. Paragraph
        assertThat(blocks[1]).isInstanceOf(MessageBlock.Paragraph::class.java)

        // 3. Table
        assertThat(blocks[2]).isInstanceOf(MessageBlock.Table::class.java)
        val table = blocks[2] as MessageBlock.Table
        assertThat(table.rows).hasSize(3) // 1 header + 2 body rows
        val headerTexts = table.rows[0].map { cell -> (cell.first() as InlineNode.Text).value }
        assertThat(headerTexts).containsExactly("密钥", "用途", "私钥在哪里").inOrder()
        val row1Texts = table.rows[1].map { cell -> (cell.first() as InlineNode.Text).value }
        assertThat(row1Texts).containsExactly("Ed25519 设备密钥", "给设备公钥和数据签名", "只在手机 A").inOrder()

        // 4. Paragraph
        assertThat(blocks[3]).isInstanceOf(MessageBlock.Paragraph::class.java)

        // 5. CodeBlock
        assertThat(blocks[4]).isInstanceOf(MessageBlock.CodeBlock::class.java)
        val codeBlock = blocks[4] as MessageBlock.CodeBlock
        assertThat(codeBlock.language).isEqualTo("text")
        assertThat(codeBlock.code).isEqualTo("POST /_matrix/client/v3/keys/upload")

        // 6. ListBlock
        assertThat(blocks[5]).isInstanceOf(MessageBlock.ListBlock::class.java)
        val listBlock = blocks[5] as MessageBlock.ListBlock
        assertThat(listBlock.ordered).isTrue()
        assertThat(listBlock.items).hasSize(2)
    }

    @Test
    fun `MarkdownToMessageAstParser - parses markdown heading, table and code block`() {
        val markdown = """
            ## Heading 2

            | Col A | Col B |
            |---|---|
            | val 1 | val 2 |

            ```kotlin
            val x = 42
            ```

            > This is a quote
        """.trimIndent()

        val blocks = MarkdownToMessageAstParser.parse(markdown)
        assertThat(blocks).hasSize(4)

        assertThat(blocks[0]).isInstanceOf(MessageBlock.Heading::class.java)
        assertThat((blocks[0] as MessageBlock.Heading).level).isEqualTo(2)

        assertThat(blocks[1]).isInstanceOf(MessageBlock.Table::class.java)
        val table = blocks[1] as MessageBlock.Table
        assertThat(table.rows).hasSize(2)

        assertThat(blocks[2]).isInstanceOf(MessageBlock.CodeBlock::class.java)
        val code = blocks[2] as MessageBlock.CodeBlock
        assertThat(code.language).isEqualTo("kotlin")
        assertThat(code.code).isEqualTo("val x = 42")

        assertThat(blocks[3]).isInstanceOf(MessageBlock.Quote::class.java)
    }

    @Test
    fun `HtmlToMessageAstParser - parses inline math and block math via MSC2191 data-mx-maths`() {
        val html = """
            <p>Hello <span data-mx-maths="\alpha">\alpha</span> world</p>
            <div data-mx-maths="E=mc^2"><pre><code>E=mc^2</code></pre></div>
        """.trimIndent()

        val blocks = HtmlToMessageAstParser.parse(html)
        assertThat(blocks).hasSize(2)

        assertThat(blocks[0]).isInstanceOf(MessageBlock.Paragraph::class.java)
        val p = blocks[0] as MessageBlock.Paragraph
        assertThat(p.children.any { it is InlineNode.InlineMath && it.formula == "\\alpha" }).isTrue()

        assertThat(blocks[1]).isInstanceOf(MessageBlock.BlockMath::class.java)
        val math = blocks[1] as MessageBlock.BlockMath
        assertThat(math.formula).isEqualTo("E=mc^2")
    }

    @Test
    fun `HtmlToMessageAstParser - strips mx-reply tag completely`() {
        val html = """
            <mx-reply><blockquote>Reply content</blockquote></mx-reply>
            <p>Actual message</p>
        """.trimIndent()

        val blocks = HtmlToMessageAstParser.parse(html)
        assertThat(blocks).hasSize(1)
        val p = blocks[0] as MessageBlock.Paragraph
        assertThat((p.children[0] as InlineNode.Text).value).isEqualTo("Actual message")
    }

    @Test
    fun `MessageAstParser - elevates tall fraction formula to block math and preserves short inline formula`() {
        val raw = "设分式 $\\frac{a+b}{c+d}$ 与短公式 \$x\$ 是成立的。"
        val blocks = MessageAstParser.parse(formattedBody = null, rawBody = raw)

        assertThat(blocks).hasSize(3)

        // 1. First paragraph: "设分式"
        assertThat(blocks[0]).isInstanceOf(MessageBlock.Paragraph::class.java)
        val p1 = blocks[0] as MessageBlock.Paragraph
        assertThat((p1.children[0] as InlineNode.Text).value).isEqualTo("设分式")

        // 2. Elevated block math: \frac{a+b}{c+d}
        assertThat(blocks[1]).isInstanceOf(MessageBlock.BlockMath::class.java)
        val mathBlock = blocks[1] as MessageBlock.BlockMath
        assertThat(mathBlock.formula).isEqualTo("\\frac{a+b}{c+d}")

        // 3. Second paragraph: "与短公式 $x$ 是成立的。" (with $x$ remaining inline!)
        assertThat(blocks[2]).isInstanceOf(MessageBlock.Paragraph::class.java)
        val p2 = blocks[2] as MessageBlock.Paragraph
        assertThat(p2.children.any { it is InlineNode.InlineMath && it.formula == "x" }).isTrue()
    }
}
