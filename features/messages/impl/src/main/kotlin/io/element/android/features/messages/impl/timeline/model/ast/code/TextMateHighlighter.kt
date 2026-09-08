/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast.code

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.grammar.IStateStack
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.Registry
import timber.log.Timber
import java.time.Duration

object TextMateHighlighter {
    private val SHELL_GRAMMAR_JSON = """
    {
        "scopeName": "source.shell",
        "patterns": [
            {
                "name": "comment.line.number-sign.shell",
                "match": "#.*$"
            },
            {
                "name": "string.quoted.single.shell",
                "begin": "'",
                "end": "'"
            },
            {
                "name": "string.quoted.double.shell",
                "begin": "\"",
                "end": "\"",
                "patterns": [
                    {
                        "name": "constant.character.escape.shell",
                        "match": "\\\\."
                    },
                    {
                        "name": "variable.other.normal.shell",
                        "match": "\\$\\{[^}]+\\}|\\$[a-zA-Z_0-9@*#?${'$'}!_-]+"
                    }
                ]
            },
            {
                "name": "string.interpolated.backtick.shell",
                "begin": "`",
                "end": "`"
            },
            {
                "name": "variable.other.normal.shell",
                "match": "\\$\\{[^}]+\\}|\\$[a-zA-Z_0-9@*#?${'$'}!_-]+"
            },
            {
                "name": "keyword.operator.redirection.shell",
                "match": "(&&|\\|\\||\\||>>|>|<|<<|2>&1|&>|;)"
            },
            {
                "name": "keyword.control.shell",
                "match": "\\b(if|then|else|elif|fi|case|esac|for|select|while|until|do|done|in|function|time|return|exit|export|local|declare|typeset|readonly|alias|unalias|source|eval|exec|set|unset|shift|trap|test)\\b"
            },
            {
                "name": "support.function.builtin.shell",
                "match": "\\b(sudo|su|curl|wget|git|docker|docker-compose|podman|kubectl|helm|npm|npx|yarn|pnpm|bun|pip|pip3|python|python3|node|deno|go|cargo|rustc|java|javac|mvn|gradle|gradlew|make|cmake|ninja|gcc|g\\+\\+|clang|apt|apt-get|dpkg|yum|dnf|pacman|brew|apk|zypper|tar|gzip|gunzip|zip|unzip|xz|cat|grep|egrep|fgrep|rg|sed|awk|find|fd|ls|ll|la|mkdir|rm|cp|mv|chmod|chown|touch|ln|ssh|scp|rsync|systemctl|journalctl|service|top|htop|btop|ps|kill|killall|pkill|df|du|free|uname|uptime|whoami|echo|printf|read|cd|pwd|pushd|popd|history|jobs|fg|bg|wait|disown|tee|xargs|head|tail|sort|uniq|wc|diff|ping|ip|ifconfig|netstat|ss|clear|fastboot|adb|ffmpeg|tree|ollama|uv|poetry)\\b"
            },
            {
                "name": "variable.parameter.option.shell",
                "match": "(?<=\\s|^)(--[a-zA-Z0-9_-]+|-[a-zA-Z0-9]+)"
            },
            {
                "name": "constant.numeric.integer.shell",
                "match": "\\b\\d+(\\.\\d+)?\\b"
            }
        ]
    }
    """.trimIndent()

    private val PYTHON_GRAMMAR_JSON = """
    {
        "scopeName": "source.python",
        "patterns": [
            {
                "name": "comment.line.number-sign.python",
                "match": "#.*$"
            },
            {
                "name": "string.quoted.triple.python",
                "begin": "\"\"\"|'''",
                "end": "\"\"\"|'''"
            },
            {
                "name": "string.quoted.double.python",
                "begin": "[rfRF]?\"",
                "end": "\""
            },
            {
                "name": "string.quoted.single.python",
                "begin": "[rfRF]?'",
                "end": "'"
            },
            {
                "name": "variable.parameter.decorator.python",
                "match": "@[a-zA-Z_][a-zA-Z0-9_.]*"
            },
            {
                "name": "keyword.control.python",
                "match": "\\b(def|class|return|if|elif|else|for|while|break|continue|try|except|finally|raise|import|from|as|with|lambda|yield|global|nonlocal|pass|assert|in|is|not|and|or|async|await)\\b"
            },
            {
                "name": "constant.language.python",
                "match": "\\b(True|False|None)\\b"
            },
            {
                "name": "support.type.python",
                "match": "\\b(int|float|str|bool|list|dict|set|tuple|object|bytes)\\b"
            },
            {
                "name": "support.function.builtin.python",
                "match": "\\b(print|len|range|enumerate|zip|isinstance|type|sum|min|max|sorted|map|filter|super|open|input)\\b"
            },
            {
                "name": "variable.language.python",
                "match": "\\b(self|cls|__name__|__file__|__init__|__main__|__doc__)\\b"
            },
            {
                "name": "constant.numeric.python",
                "match": "\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?([eE][+-]?\\d+)?)\\b"
            }
        ]
    }
    """.trimIndent()

    private val JSON_GRAMMAR_JSON = """
    {
        "scopeName": "source.json",
        "patterns": [
            {
                "name": "string.quoted.double.json",
                "begin": "\"",
                "end": "\"",
                "patterns": [
                    {
                        "name": "constant.character.escape.json",
                        "match": "\\\\."
                    }
                ]
            },
            {
                "name": "constant.language.json",
                "match": "\\b(true|false|null)\\b"
            },
            {
                "name": "constant.numeric.json",
                "match": "\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b"
            }
        ]
    }
    """.trimIndent()

    private val JS_GRAMMAR_JSON = """
    {
        "scopeName": "source.js",
        "patterns": [
            {
                "name": "comment.line.double-slash.js",
                "match": "//.*$"
            },
            {
                "name": "comment.block.js",
                "begin": "/\\*",
                "end": "\\*/"
            },
            {
                "name": "string.quoted.double.js",
                "begin": "\"",
                "end": "\""
            },
            {
                "name": "string.quoted.single.js",
                "begin": "'",
                "end": "'"
            },
            {
                "name": "string.template.js",
                "begin": "`",
                "end": "`"
            },
            {
                "name": "keyword.control.js",
                "match": "\\b(function|const|let|var|return|if|else|for|while|do|switch|case|break|continue|default|try|catch|finally|throw|class|extends|new|this|super|import|export|from|async|await|yield|typeof|instanceof|of|type|interface|as)\\b"
            },
            {
                "name": "constant.language.js",
                "match": "\\b(true|false|null|undefined)\\b"
            },
            {
                "name": "support.type.js",
                "match": "\\b(string|number|boolean|any|void|never|unknown|Promise|Array|Record|Object)\\b"
            },
            {
                "name": "support.function.js",
                "match": "\\b(console|document|window|Math|JSON|Promise|setTimeout|setInterval|require)\\b"
            },
            {
                "name": "constant.numeric.js",
                "match": "\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?)\\b"
            }
        ]
    }
    """.trimIndent()

    private val KOTLIN_GRAMMAR_JSON = """
    {
        "scopeName": "source.kotlin",
        "patterns": [
            {
                "name": "comment.line.double-slash.kotlin",
                "match": "//.*$"
            },
            {
                "name": "comment.block.kotlin",
                "begin": "/\\*",
                "end": "\\*/"
            },
            {
                "name": "string.quoted.triple.kotlin",
                "begin": "\"\"\"",
                "end": "\"\"\""
            },
            {
                "name": "string.quoted.double.kotlin",
                "begin": "\"",
                "end": "\""
            },
            {
                "name": "variable.parameter.annotation.kotlin",
                "match": "@[a-zA-Z_][a-zA-Z0-9_.]*"
            },
            {
                "name": "keyword.control.kotlin",
                "match": "\\b(package|import|fun|val|var|class|interface|object|sealed|data|enum|override|public|private|protected|internal|return|if|else|when|for|while|do|try|catch|finally|throw|new|this|super|is|as|in|abstract|companion|open|lateinit|suspend|inline|reified|operator|infix|const|by)\\b"
            },
            {
                "name": "constant.language.kotlin",
                "match": "\\b(true|false|null)\\b"
            },
            {
                "name": "support.type.kotlin",
                "match": "\\b(String|Int|Long|Float|Double|Boolean|Byte|Short|Char|Unit|Any|List|Map|Set|Array|void)\\b"
            },
            {
                "name": "constant.numeric.kotlin",
                "match": "\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?([fFL])?)\\b"
            }
        ]
    }
    """.trimIndent()

    private val registry = Registry()
    private val scopeMap = mutableMapOf<String, String>() // lang -> scopeName
    private val loadedGrammars = mutableMapOf<String, IGrammar>()

    init {
        registerBuiltinGrammars()
    }

    private fun registerBuiltinGrammars() {
        try {
            // 1. Shell / Bash
            loadJsonGrammar(SHELL_GRAMMAR_JSON, "source.shell")
            listOf("bash", "sh", "zsh", "shell", "console", "terminal", "env").forEach {
                scopeMap[it] = "source.shell"
            }

            // 2. Python
            loadJsonGrammar(PYTHON_GRAMMAR_JSON, "source.python")
            listOf("python", "py", "python3", "py3").forEach {
                scopeMap[it] = "source.python"
            }

            // 3. JSON
            loadJsonGrammar(JSON_GRAMMAR_JSON, "source.json")
            listOf("json").forEach {
                scopeMap[it] = "source.json"
            }

            // 4. JavaScript / TypeScript
            loadJsonGrammar(JS_GRAMMAR_JSON, "source.js")
            listOf("javascript", "js", "typescript", "ts", "node", "mjs", "cjs").forEach {
                scopeMap[it] = "source.js"
            }

            // 5. Kotlin / Java
            loadJsonGrammar(KOTLIN_GRAMMAR_JSON, "source.kotlin")
            listOf("kotlin", "kt", "kts", "java").forEach {
                scopeMap[it] = "source.kotlin"
            }
        } catch (t: Throwable) {
            Timber.e(t, "Failed to initialize TextMate grammars")
        }
    }

    private fun loadJsonGrammar(json: String, scopeName: String) {
        val source = IGrammarSource.fromString(IGrammarSource.ContentType.JSON, json)
        val grammar = registry.addGrammar(source)
        loadedGrammars[scopeName] = grammar
    }

    fun isSupported(lang: String): Boolean {
        return scopeMap.containsKey(lang.lowercase().trim())
    }

    fun highlight(code: String, lang: String, isDark: Boolean): AnnotatedString? {
        val scopeName = scopeMap[lang.lowercase().trim()] ?: return null
        val grammar = loadedGrammars[scopeName] ?: registry.grammarForScopeName(scopeName) ?: return null

        val colors = if (isDark) DarkThemeColors else LightThemeColors
        var stateStack: IStateStack? = null
        val lines = code.lines()

        return buildAnnotatedString {
            append(code)
            var currentLineStart = 0

            for (line in lines) {
                try {
                    val result = grammar.tokenizeLine(line, stateStack, Duration.ofMillis(200))
                    stateStack = result.ruleStack

                    for (token in result.tokens) {
                        val tokenStart = currentLineStart + token.startIndex
                        val tokenEnd = currentLineStart + token.endIndex
                        if (tokenStart < tokenEnd && tokenEnd <= code.length) {
                            val style = resolveSpanStyle(token.scopes, colors)
                            if (style != null) {
                                addStyle(style, tokenStart, tokenEnd)
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // Fail-safe for individual lines
                }
                currentLineStart += line.length + 1 // +1 for '\n'
            }
        }
    }

    private fun resolveSpanStyle(scopes: List<String>, colors: TextMateColors): SpanStyle? {
        // Evaluate from the most specific scope (end of list) to general
        for (i in scopes.indices.reversed()) {
            val scope = scopes[i]
            when {
                scope.startsWith("comment") || scope.contains(".comment") ->
                    return SpanStyle(color = colors.comment)

                scope.startsWith("string") || scope.contains(".string") ->
                    return SpanStyle(color = colors.string)

                scope.startsWith("variable") || scope.contains(".variable") ->
                    return SpanStyle(color = colors.variable)

                scope.startsWith("constant.numeric") || scope.contains(".numeric") ->
                    return SpanStyle(color = colors.number)

                scope.startsWith("constant.language") || scope.contains(".boolean") || scope.contains(".null") ->
                    return SpanStyle(color = colors.constant, fontWeight = FontWeight.SemiBold)

                scope.startsWith("keyword.operator") || scope.contains(".operator") ->
                    return SpanStyle(color = colors.operator)

                scope.startsWith("keyword") || scope.startsWith("storage") ->
                    return SpanStyle(color = colors.keyword, fontWeight = FontWeight.SemiBold)

                scope.startsWith("support.function") || scope.startsWith("entity.name.function") ->
                    return SpanStyle(color = colors.function, fontWeight = FontWeight.Medium)

                scope.startsWith("entity.name.type") || scope.startsWith("support.type") || scope.startsWith("support.class") ->
                    return SpanStyle(color = colors.type)

                scope.contains("flag") || scope.contains("parameter") ->
                    return SpanStyle(color = colors.flag)
            }
        }
        return null
    }

    data class TextMateColors(
        val keyword: Color,
        val string: Color,
        val comment: Color,
        val number: Color,
        val type: Color,
        val function: Color,
        val variable: Color,
        val flag: Color,
        val operator: Color,
        val constant: Color,
    )

    private val DarkThemeColors = TextMateColors(
        keyword = Color(0xFFCC7832),
        string = Color(0xFF6A8759),
        comment = Color(0xFF808080),
        number = Color(0xFF6897BB),
        type = Color(0xFFFFC66D),
        function = Color(0xFF56B6C2),
        variable = Color(0xFFE06C75),
        flag = Color(0xFFD19A66),
        operator = Color(0xFF61AFEF),
        constant = Color(0xFF9876AA),
    )

    private val LightThemeColors = TextMateColors(
        keyword = Color(0xFF0033B3),
        string = Color(0xFF067D17),
        comment = Color(0xFF8C8C8C),
        number = Color(0xFF1750EB),
        type = Color(0xFF871094),
        function = Color(0xFF00627A),
        variable = Color(0xFFC7254E),
        flag = Color(0xFF94558D),
        operator = Color(0xFF2C6BB0),
        constant = Color(0xFF871094),
    )
}
