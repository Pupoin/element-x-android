/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast.code

import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DefaultCodeSyntaxHighlighter : CodeSyntaxHighlighter {
    private const val MAX_CODE_LENGTH = 50_000
    private const val CACHE_SIZE = 100

    private val cache = LruCache<Triple<Int, String, Boolean>, AnnotatedString>(CACHE_SIZE)

    override suspend fun highlight(
        code: String,
        language: String?,
        isDark: Boolean,
    ): AnnotatedString = withContext(Dispatchers.Default) {
        if (code.length > MAX_CODE_LENGTH) {
            return@withContext AnnotatedString(code)
        }

        val detectedLang = language?.takeIf { it.isNotBlank() } ?: detectLanguage(code)
        val normalizedLang = normalizeLanguage(detectedLang)
        val cacheKey = Triple(code.hashCode(), normalizedLang, isDark)
        val cached = cache.get(cacheKey)
        if (cached != null) {
            return@withContext cached
        }

        // 1. Try TextMate engine first (VS Code standard tokenization)
        val textMateResult = if (TextMateHighlighter.isSupported(normalizedLang)) {
            try {
                TextMateHighlighter.highlight(code, normalizedLang, isDark)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }

        // 2. Fallback to regex-based engine if TextMate does not support or failed
        val highlighted = textMateResult ?: try {
            highlightInternal(code, normalizedLang, isDark)
        } catch (_: Throwable) {
            AnnotatedString(code)
        }

        cache.put(cacheKey, highlighted)
        highlighted
    }

    fun detectLanguage(code: String): String {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return ""

        val firstLine = trimmed.lineSequence().firstOrNull()?.trim() ?: ""

        // 1. Shebang
        if (firstLine.startsWith("#!")) {
            val lower = firstLine.lowercase()
            return when {
                lower.contains("bash") || lower.contains("sh") || lower.contains("zsh") -> "shell"
                lower.contains("python") -> "python"
                lower.contains("node") -> "javascript"
                else -> "shell"
            }
        }

        // 2. JSON
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            if (trimmed.contains("\"") && trimmed.contains(":")) {
                return "json"
            }
        }

        // 3. Shell prompts and common CLI commands
        val shellCommandPattern = Regex(
            """^(sudo\s+|git\s+|curl\s+|wget\s+|docker\s+|npm\s+|yarn\s+|pip\s+|apt\s+|export\s+|cd\s+|ls\s+|rm\s+|mkdir\s+|cat\s+|echo\s+|\$\s+)"""
        )
        for (line in trimmed.lines().take(5)) {
            if (shellCommandPattern.containsMatchIn(line.trim())) {
                return "shell"
            }
        }

        return ""
    }

    fun normalizeLanguage(raw: String?): String {
        if (raw == null) return ""
        val cleaned = raw.trim().lowercase()
        return when (cleaned) {
            "kt", "kts", "kotlin" -> "kotlin"
            "java" -> "java"
            "py", "python", "python3", "py3" -> "python"
            "js", "javascript", "node", "mjs", "cjs" -> "javascript"
            "ts", "typescript" -> "typescript"
            "json" -> "json"
            "rs", "rust" -> "rust"
            "c", "cpp", "c++", "h", "hpp", "cc", "cxx" -> "cpp"
            "cs", "csharp", "c#" -> "csharp"
            "sh", "bash", "zsh", "shell", "console", "terminal", "env" -> "shell"
            "html", "xml", "svg" -> "xml"
            "sql" -> "sql"
            "go", "golang" -> "go"
            "yaml", "yml" -> "yaml"
            "markdown", "md" -> "markdown"
            "css", "scss", "less" -> "css"
            else -> cleaned
        }
    }

    private fun highlightInternal(code: String, lang: String, isDark: Boolean): AnnotatedString {
        val colors = if (isDark) DarkCodeColors else LightCodeColors
        val patterns = getLanguagePatterns(lang) ?: return AnnotatedString(code)

        val ranges = mutableListOf<TokenSpan>()

        fun addMatches(regex: Regex?, style: SpanStyle) {
            if (regex == null) return
            for (match in regex.findAll(code)) {
                val start = match.range.first
                val end = match.range.last + 1
                if (ranges.none { it.overlaps(start, end) }) {
                    ranges.add(TokenSpan(start, end, style))
                }
            }
        }

        // 1. Comments
        addMatches(patterns.commentRegex, SpanStyle(color = colors.comment))

        // 2. Strings
        addMatches(patterns.stringRegex, SpanStyle(color = colors.string))

        // 3. Variables & Parameter Expansions
        addMatches(patterns.variableRegex, SpanStyle(color = colors.variable))

        // 4. Flags / Options
        addMatches(patterns.flagRegex, SpanStyle(color = colors.flag))

        // 5. Numbers
        addMatches(patterns.numberRegex, SpanStyle(color = colors.number))

        // 6. Keywords
        addMatches(patterns.keywordRegex, SpanStyle(color = colors.keyword, fontWeight = FontWeight.SemiBold))

        // 7. Functions / Commands
        addMatches(patterns.functionRegex, SpanStyle(color = colors.function, fontWeight = FontWeight.Medium))

        // 8. Types / Classes
        addMatches(patterns.typeRegex, SpanStyle(color = colors.type))

        // 9. Operators / Redirection
        addMatches(patterns.operatorRegex, SpanStyle(color = colors.operator))

        return buildAnnotatedString {
            append(code)
            for (token in ranges) {
                addStyle(token.style, token.start, token.end)
            }
        }
    }

    private data class TokenSpan(val start: Int, val end: Int, val style: SpanStyle) {
        fun overlaps(otherStart: Int, otherEnd: Int): Boolean {
            return start < otherEnd && end > otherStart
        }
    }

    private data class CodeColorScheme(
        val keyword: Color,
        val string: Color,
        val comment: Color,
        val number: Color,
        val type: Color,
        val function: Color,
        val variable: Color,
        val flag: Color,
        val operator: Color,
    )

    private val DarkCodeColors = CodeColorScheme(
        keyword = Color(0xFFCC7832),
        string = Color(0xFF6A8759),
        comment = Color(0xFF808080),
        number = Color(0xFF6897BB),
        type = Color(0xFFFFC66D),
        function = Color(0xFF56B6C2),
        variable = Color(0xFFE06C75),
        flag = Color(0xFFD19A66),
        operator = Color(0xFF61AFEF),
    )

    private val LightCodeColors = CodeColorScheme(
        keyword = Color(0xFF0033B3),
        string = Color(0xFF067D17),
        comment = Color(0xFF8C8C8C),
        number = Color(0xFF1750EB),
        type = Color(0xFF871094),
        function = Color(0xFF00627A),
        variable = Color(0xFFC7254E),
        flag = Color(0xFF94558D),
        operator = Color(0xFF2C6BB0),
    )

    private data class LanguagePatterns(
        val keywordRegex: Regex? = null,
        val functionRegex: Regex? = null,
        val variableRegex: Regex? = null,
        val typeRegex: Regex? = null,
        val stringRegex: Regex? = null,
        val commentRegex: Regex? = null,
        val flagRegex: Regex? = null,
        val operatorRegex: Regex? = null,
        val numberRegex: Regex? = Regex("""\b(0x[0-9a-fA-F]+|\d+(\.\d+)?([eE][+-]?\d+)?([fFL])?)\b"""),
    )

    private fun getLanguagePatterns(lang: String): LanguagePatterns? {
        val standardStrings = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'"
        val tripleDouble = "\"\"\"[\\s\\S]*?\"\"\""
        val tripleSingle = "'''[\\s\\S]*?'''"
        val slashComments = Regex("""//.*|/\*[\s\S]*?\*/""")
        val hashComments = Regex("""#.*""")

        return when (lang) {
            "kotlin", "java" -> LanguagePatterns(
                keywordRegex = Regex(
                    """\b(package|import|fun|val|var|class|interface|object|sealed|data|enum|override|public|private|protected|internal|return|if|else|when|for|while|do|try|catch|finally|throw|new|this|super|is|as|in|null|true|false|abstract|companion|open|lateinit|suspend|inline|crossinline|noinline|reified|tailrec|operator|infix|const|by)\b"""
                ),
                typeRegex = Regex("""\b(String|Int|Long|Float|Double|Boolean|Byte|Short|Char|Unit|Any|List|Map|Set|Array|void)\b"""),
                flagRegex = Regex("""@[a-zA-Z_][a-zA-Z0-9_.]*"""),
                stringRegex = Regex("$tripleDouble|$standardStrings"),
                commentRegex = slashComments,
            )
            "python" -> LanguagePatterns(
                keywordRegex = Regex(
                    """\b(def|class|return|if|elif|else|for|while|break|continue|try|except|finally|raise|import|from|as|with|lambda|yield|global|nonlocal|pass|assert|in|is|not|and|or|async|await)\b"""
                ),
                typeRegex = Regex("""\b(int|float|str|bool|list|dict|set|tuple|object|bytes)\b"""),
                functionRegex = Regex("""\b(print|len|range|enumerate|zip|isinstance|type|sum|min|max|sorted|map|filter|super|open|input)\b"""),
                variableRegex = Regex("""\b(self|cls|None|True|False|__name__|__file__|__init__|__main__|__doc__)\b"""),
                flagRegex = Regex("""@[a-zA-Z_][a-zA-Z0-9_.]*"""),
                stringRegex = Regex("$tripleDouble|$tripleSingle|[rfRF]?$standardStrings"),
                commentRegex = hashComments,
            )
            "javascript", "typescript" -> LanguagePatterns(
                keywordRegex = Regex(
                    """\b(function|const|let|var|return|if|else|for|while|do|switch|case|break|continue|default|try|catch|finally|throw|class|extends|new|this|super|import|export|from|default|async|await|yield|typeof|instanceof|null|undefined|true|false|type|interface|as|of)\b"""
                ),
                typeRegex = Regex("""\b(string|number|boolean|any|void|never|unknown|Promise|Array|Record|Object)\b"""),
                functionRegex = Regex("""\b(console|document|window|Math|JSON|Promise|setTimeout|setInterval|require)\b"""),
                stringRegex = Regex("`([^`\\\\]|\\\\.)*`|$standardStrings"),
                commentRegex = slashComments,
            )
            "json" -> LanguagePatterns(
                keywordRegex = Regex("""\b(true|false|null)\b"""),
                stringRegex = Regex(standardStrings),
                numberRegex = Regex("""\b\d+(\.\d+)?([eE][+-]?\d+)?\b"""),
            )
            "rust" -> LanguagePatterns(
                keywordRegex = Regex(
                    """\b(fn|let|mut|pub|mod|use|struct|enum|trait|impl|for|while|loop|if|else|match|return|break|continue|as|in|ref|self|Self|true|false|async|await|move|where|type|const|static)\b"""
                ),
                typeRegex = Regex("""\b(i8|i16|i32|i64|i128|u8|u16|u32|u64|u128|f32|f64|bool|char|str|String|Vec|Option|Result|Some|None|Ok|Err)\b"""),
                functionRegex = Regex("""\b[a-zA-Z_][a-zA-Z0-9_]*!"""),
                stringRegex = Regex(standardStrings),
                commentRegex = slashComments,
            )
            "cpp", "csharp" -> LanguagePatterns(
                keywordRegex = Regex(
                    """\b(auto|const|constexpr|class|struct|enum|union|template|typename|namespace|using|public|private|protected|virtual|override|final|static|inline|explicit|friend|new|delete|this|nullptr|true|false|return|if|else|for|while|do|switch|case|break|continue|try|catch|throw)\b"""
                ),
                typeRegex = Regex("""\b(int|long|short|float|double|char|bool|void|size_t|std::string|std::vector|std::map)\b"""),
                stringRegex = Regex(standardStrings),
                commentRegex = slashComments,
            )
            "shell" -> LanguagePatterns(
                keywordRegex = Regex(
                    """\b(if|then|else|elif|fi|case|esac|for|select|while|until|do|done|in|function|time|return|exit|export|local|declare|typeset|readonly|alias|unalias|source|eval|exec|set|unset|shift|trap|test)\b"""
                ),
                functionRegex = Regex(
                    """\b(sudo|su|curl|wget|git|docker|docker-compose|podman|kubectl|helm|npm|npx|yarn|pnpm|bun|pip|pip3|python|python3|node|deno|go|cargo|rustc|java|javac|mvn|gradle|gradlew|make|cmake|ninja|gcc|g\+\+|clang|apt|apt-get|dpkg|yum|dnf|pacman|brew|apk|zypper|tar|gzip|gunzip|zip|unzip|xz|cat|grep|egrep|fgrep|rg|sed|awk|find|fd|ls|ll|la|mkdir|rm|cp|mv|chmod|chown|touch|ln|ssh|scp|rsync|systemctl|journalctl|service|top|htop|btop|ps|kill|killall|pkill|df|du|free|uname|uptime|whoami|echo|printf|read|cd|pwd|pushd|popd|history|jobs|fg|bg|wait|disown|tee|xargs|head|tail|sort|uniq|wc|diff|ping|ip|ifconfig|netstat|ss|clear)\b"""
                ),
                variableRegex = Regex(
                    """\$\{([^}]+)\}|\$[a-zA-Z_][a-zA-Z0-9_]*|\$[@*#?$!0-9_-]"""
                ),
                flagRegex = Regex("""(?<=\s|^)(--[a-zA-Z0-9_-]+|-[a-zA-Z0-9]+)"""),
                operatorRegex = Regex("""(&&|\|\||\||>>|>|<|<<|2>&1|&>|;)"""),
                stringRegex = Regex("$standardStrings|`[^`]*`|\\$\\([^\\n)]+\\)"),
                commentRegex = Regex("""(?m)^#!.*|#.*"""),
            )
            "go" -> LanguagePatterns(
                keywordRegex = Regex(
                    """\b(package|import|func|return|var|const|type|struct|interface|map|chan|select|case|default|go|defer|if|else|for|range|break|continue|fallthrough|switch|true|false|nil)\b"""
                ),
                typeRegex = Regex("""\b(int|int8|int16|int32|int64|uint|uint8|uint16|uint32|uint64|float32|float64|string|bool|byte|rune|error)\b"""),
                stringRegex = Regex("`[^`]*`|$standardStrings"),
                commentRegex = slashComments,
            )
            "sql" -> LanguagePatterns(
                keywordRegex = Regex(
                    """(?i)\b(select|from|where|insert|into|update|delete|create|table|drop|alter|index|view|join|left|right|inner|outer|on|group|by|order|having|limit|offset|union|all|distinct|as|and|or|not|in|is|null|like|between|case|when|then|else|end|primary|key|foreign|references|default)\b"""
                ),
                typeRegex = Regex("""(?i)\b(int|integer|bigint|varchar|char|text|boolean|date|datetime|timestamp|decimal|numeric|float|double)\b"""),
                stringRegex = Regex(standardStrings),
                commentRegex = Regex("""--.*|/\*[\s\S]*?\*/"""),
            )
            "xml" -> LanguagePatterns(
                keywordRegex = Regex("""</?[a-zA-Z0-9_:-]+|/?>"""),
                flagRegex = Regex("""\b[a-zA-Z0-9_:-]+(?==)"""),
                stringRegex = Regex(standardStrings),
                commentRegex = Regex("""<!--[\s\S]*?-->"""),
                numberRegex = null,
            )
            "yaml" -> LanguagePatterns(
                keywordRegex = Regex("""\b(true|false|null|yes|no|on|off)\b"""),
                variableRegex = Regex("""^[ \t]*[a-zA-Z0-9_-]+(?=:)"""),
                stringRegex = Regex(standardStrings),
                commentRegex = hashComments,
            )
            else -> null
        }
    }
}
