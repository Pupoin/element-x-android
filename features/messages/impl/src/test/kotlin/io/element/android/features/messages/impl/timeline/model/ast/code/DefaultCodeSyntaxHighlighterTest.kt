/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast.code

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultCodeSyntaxHighlighterTest {
    @Test
    fun `test normalize language names`() {
        assertThat(DefaultCodeSyntaxHighlighter.normalizeLanguage("bash")).isEqualTo("shell")
        assertThat(DefaultCodeSyntaxHighlighter.normalizeLanguage("sh")).isEqualTo("shell")
        assertThat(DefaultCodeSyntaxHighlighter.normalizeLanguage("zsh")).isEqualTo("shell")
        assertThat(DefaultCodeSyntaxHighlighter.normalizeLanguage("console")).isEqualTo("shell")
        assertThat(DefaultCodeSyntaxHighlighter.normalizeLanguage("kt")).isEqualTo("kotlin")
        assertThat(DefaultCodeSyntaxHighlighter.normalizeLanguage("py")).isEqualTo("python")
        assertThat(DefaultCodeSyntaxHighlighter.normalizeLanguage("js")).isEqualTo("javascript")
        assertThat(DefaultCodeSyntaxHighlighter.normalizeLanguage("rs")).isEqualTo("rust")
        assertThat(DefaultCodeSyntaxHighlighter.normalizeLanguage("yml")).isEqualTo("yaml")
    }

    @Test
    fun `test bash syntax highlighting produces styled spans`() = runTest {
        val bashScript = """
            #!/bin/bash
            # Install dependencies
            echo "Starting build..."
            VAR="hello world"
            if [ -z "${'$'}VAR" ]; then
                sudo apt-get install -y curl git
                curl -s https://example.com | grep -E "success" > output.log
            fi
        """.trimIndent()

        val highlightedDark = DefaultCodeSyntaxHighlighter.highlight(bashScript, "bash", isDark = true)
        assertThat(highlightedDark.text).isEqualTo(bashScript)
        assertThat(highlightedDark.spanStyles).isNotEmpty()

        val highlightedLight = DefaultCodeSyntaxHighlighter.highlight(bashScript, "bash", isDark = false)
        assertThat(highlightedLight.text).isEqualTo(bashScript)
        assertThat(highlightedLight.spanStyles).isNotEmpty()
    }

    @Test
    fun `test python syntax highlighting produces styled spans`() = runTest {
        val pythonCode = """
            # A simple function
            @decorator
            def compute(x: int) -> int:
                print(f"Calculating {x}")
                return x * 42
        """.trimIndent()

        val highlighted = DefaultCodeSyntaxHighlighter.highlight(pythonCode, "python", isDark = true)
        assertThat(highlighted.text).isEqualTo(pythonCode)
        assertThat(highlighted.spanStyles).isNotEmpty()
    }

    @Test
    fun `test unknown language returns unstyled text without errors`() = runTest {
        val unknownCode = "some random plain text"
        val highlighted = DefaultCodeSyntaxHighlighter.highlight(unknownCode, "unknown_lang", isDark = true)
        assertThat(highlighted.text).isEqualTo(unknownCode)
    }
}
