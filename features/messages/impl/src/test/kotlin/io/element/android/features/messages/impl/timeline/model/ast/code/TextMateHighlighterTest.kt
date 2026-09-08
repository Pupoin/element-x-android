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

class TextMateHighlighterTest {
    @Test
    fun `test TextMate tokenizes complex bash script with variables and options`() {
        val script = """
            #!/bin/bash
            # Deploy script
            export APP_ENV="production"
            echo "Building ${'$'}{APP_NAME:-MyApp}..."
            if [ -d "./dist" ]; then
                rm -rf ./dist
                mkdir -p ./dist
                curl -s -X POST https://api.example.com/deploy \
                    --header "Content-Type: application/json" \
                    -d "{\"status\": \"ok\"}"
            fi
            exit 0
        """.trimIndent()

        val highlighted = TextMateHighlighter.highlight(script, "bash", isDark = true)
        assertThat(highlighted).isNotNull()
        assertThat(highlighted!!.text).isEqualTo(script)
        assertThat(highlighted.spanStyles).isNotEmpty()

        // Verify that styles were applied to comments, strings, and commands
        val totalStyledChars = highlighted.spanStyles.sumOf { it.end - it.start }
        assertThat(totalStyledChars).isGreaterThan(50)
    }

    @Test
    fun `test TextMate tokenizes python and json`() {
        val pythonCode = """
            @dataclass
            class User:
                name: str
                age: int = 18

                def greet(self) -> str:
                    # Return greeting
                    return f"Hello, {self.name}"
        """.trimIndent()

        val pyHighlighted = TextMateHighlighter.highlight(pythonCode, "python", isDark = true)
        assertThat(pyHighlighted).isNotNull()
        assertThat(pyHighlighted!!.spanStyles).isNotEmpty()

        val jsonCode = """
            {
                "name": "Element",
                "version": 1.0,
                "is_active": true,
                "null_val": null
            }
        """.trimIndent()

        val jsonHighlighted = TextMateHighlighter.highlight(jsonCode, "json", isDark = false)
        assertThat(jsonHighlighted).isNotNull()
        assertThat(jsonHighlighted!!.spanStyles).isNotEmpty()
    }

    @Test
    fun `test auto detection of shell commands without explicit language tag`() = runTest {
        val shellCommands = """
            sudo apt-get update && sudo apt-get install -y git curl
            export PATH=${'$'}PATH:/usr/local/bin
            git clone https://github.com/element-hq/element-x-android.git
            cd element-x-android
            echo "Done"
        """.trimIndent()

        // Passed with null language tag
        val highlighted = DefaultCodeSyntaxHighlighter.highlight(shellCommands, null, isDark = true)
        assertThat(highlighted.text).isEqualTo(shellCommands)
        assertThat(highlighted.spanStyles).isNotEmpty()
    }
}
