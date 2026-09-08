/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast.code

import android.util.LruCache
import androidx.compose.ui.text.AnnotatedString
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

        // Try TextMate engine (VS Code standard tokenization)
        val highlighted = if (TextMateHighlighter.isSupported(normalizedLang)) {
            try {
                TextMateHighlighter.highlight(code, normalizedLang, isDark) ?: AnnotatedString(code)
            } catch (_: Throwable) {
                AnnotatedString(code)
            }
        } else {
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
}
