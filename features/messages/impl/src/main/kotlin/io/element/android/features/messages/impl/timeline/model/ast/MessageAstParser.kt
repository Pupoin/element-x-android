/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast

import android.util.LruCache
import io.element.android.libraries.matrix.api.timeline.item.event.FormattedBody
import io.element.android.libraries.matrix.api.timeline.item.event.MessageFormat

object MessageAstParser {
    // Cache the parsed AST by the hash of its input content to avoid recomputing on Compose recompositions
    private val astCache = LruCache<Int, List<MessageBlock>>(128)

    fun parse(formattedBody: FormattedBody?, rawBody: String): List<MessageBlock> {
        val isHtml = formattedBody?.format == MessageFormat.HTML && formattedBody.body.isNotBlank()
        val contentKey = if (isHtml) formattedBody!!.body else rawBody
        val cacheKey = contentKey.hashCode()

        val cached = astCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val parsed = if (isHtml) {
            HtmlToMessageAstParser.parse(formattedBody!!.body)
        } else {
            MarkdownToMessageAstParser.parse(rawBody)
        }

        astCache.put(cacheKey, parsed)
        return parsed
    }
}
