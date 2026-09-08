/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.text.SpannedString
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.core.content.getSystemService
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayout
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayoutData
import io.element.android.features.messages.impl.timeline.model.ast.InlineNode
import io.element.android.features.messages.impl.timeline.model.ast.MessageAstParser
import io.element.android.features.messages.impl.timeline.model.ast.MessageBlock
import io.element.android.features.messages.impl.timeline.model.ast.MessageBlocksView
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContentPreviewParam
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.utils.latex.LatexHelper
import io.element.android.libraries.androidutils.text.LinkifyHelper
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.utils.LocalUiTestMode
import io.element.android.libraries.textcomposer.ElementRichTextEditorStyle
import io.element.android.libraries.textcomposer.mentions.LocalMentionSpanUpdater
import io.element.android.wysiwyg.compose.EditorStyledText
import io.element.android.wysiwyg.link.Link

val LocalRenderLatexEnabled = compositionLocalOf { true }

@Composable
fun TimelineItemTextView(
    content: TimelineItemTextBasedContent,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLinkClick: (Link) -> Unit = {},
    onLinkLongClick: (Link) -> Unit = {},
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit = {},
) {
    // The View <-> Compose interop is not working well with Compose UI tests (it loops indefinitely), so we skip it in the UI test mode.
    if (LocalUiTestMode.current) return

    val isRenderLatexEnabled = LocalRenderLatexEnabled.current
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            ru.noties.jlatexmath.JLatexMathAndroid.init(context.applicationContext)
        } catch (_: Throwable) {
        }
    }
    val handleLinkClick: (Link) -> Unit = { link ->
        val url = link.url
        if (isRenderLatexEnabled && url.startsWith("latex://")) {
            val encoded = url.removePrefix("latex://")
            val formula = android.net.Uri.decode(encoded)
            val clipboard = context.getSystemService<android.content.ClipboardManager>()
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("LaTeX Formula", formula))
            android.widget.Toast.makeText(context, "已复制 LaTeX 公式", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            onLinkClick(link)
        }
    }
    val textStyle = ElementTheme.typography.fontBodyLgRegular
    CompositionLocalProvider(
        LocalContentColor provides ElementTheme.colors.textPrimary,
        LocalTextStyle provides textStyle
    ) {
        val rawText = getTextWithResolvedMentions(content)
        val text = remember(rawText, isRenderLatexEnabled) {
            if (isRenderLatexEnabled) {
                rawText
            } else {
                LatexHelper.removeLatexSpans(rawText)
            }
        }
        val astBlocks = remember(content.body, content.htmlDocument) {
            MessageAstParser.parse(content.htmlDocument, content.body)
        }

        val hasCustomBlock = remember(astBlocks) {
            astBlocks.any {
                it is MessageBlock.Table ||
                    it is MessageBlock.CodeBlock ||
                    it is MessageBlock.Heading ||
                    it is MessageBlock.Quote ||
                    it is MessageBlock.ListBlock ||
                    it is MessageBlock.BlockMath ||
                    (it is MessageBlock.Paragraph && it.children.any { child -> child is InlineNode.InlineMath })
            }
        }

        if (!hasCustomBlock) {
            Box(modifier.semantics { contentDescription = content.plainText }) {
                EditorStyledText(
                    text = text,
                    onLinkClickedListener = handleLinkClick,
                    onLinkLongClickedListener = onLinkLongClick,
                    style = ElementRichTextEditorStyle.textStyle(),
                    onTextLayout = ContentAvoidingLayout.measureLegacyLastTextLine(onContentLayoutChange = onContentLayoutChange),
                    releaseOnDetach = false,
                )
            }
        } else {
            Box(
                modifier = modifier
                    .semantics { contentDescription = content.plainText }
                    .onSizeChanged { size ->
                        onContentLayoutChange(
                            ContentAvoidingLayoutData(
                                contentWidth = size.width,
                                contentHeight = size.height,
                                nonOverlappingContentWidth = size.width,
                                nonOverlappingContentHeight = size.height,
                            )
                        )
                    }
            ) {
                MessageBlocksView(
                    blocks = astBlocks,
                    onLongClick = onLongClick,
                    onLinkClick = { url -> onLinkClick(Link(url)) },
                )
            }
        }
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable
internal fun getTextWithResolvedMentions(content: TimelineItemTextBasedContent): CharSequence {
    val mentionSpanUpdater = LocalMentionSpanUpdater.current
    val bodyWithResolvedMentions = mentionSpanUpdater.rememberMentionSpans(content.formattedBody)
    return SpannedString.valueOf(bodyWithResolvedMentions)
}

@PreviewsDayNight
@Composable
internal fun TimelineItemTextViewPreview(
    @PreviewParameter(TimelineItemTextBasedContentPreviewParam::class) content: TimelineItemTextBasedContent
) = ElementPreview {
    TimelineItemTextView(
        content = content,
        onLinkClick = {},
        onLinkLongClick = {},
    )
}

@Preview
@Composable
internal fun TimelineItemTextViewWithLinkifiedUrlPreview() = ElementPreview {
    val content = aTimelineItemTextContent(
        formattedBody = LinkifyHelper.linkify("The link should end after the first '?' (url: github.com/element-hq/element-x-android/README?)?.")
    )
    TimelineItemTextView(
        content = content,
        onLinkClick = {},
        onLinkLongClick = {},
    )
}

@Preview
@Composable
internal fun TimelineItemTextViewWithLinkifiedUrlAndNestedParenthesisPreview() = ElementPreview {
    val content = aTimelineItemTextContent(
        formattedBody = LinkifyHelper.linkify("The link should end after the '(ME)' ((url: github.com/element-hq/element-x-android/READ(ME)))!")
    )
    TimelineItemTextView(
        content = content,
        onLinkClick = {},
        onLinkLongClick = {},
    )
}
