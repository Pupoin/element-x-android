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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayout
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayoutData
import io.element.android.features.messages.impl.timeline.model.event.AN_EMOJI_ONLY_TEXT
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContentPreviewParam
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.timeline.model.ast.HtmlToMessageAstParser
import io.element.android.features.messages.impl.timeline.model.ast.MarkdownToMessageAstParser
import io.element.android.features.messages.impl.timeline.model.ast.MessageBlock
import io.element.android.features.messages.impl.timeline.model.ast.MessageBlocksView
import io.element.android.features.messages.impl.utils.containsOnlyEmojis
import io.element.android.features.messages.impl.utils.latex.LatexHelper
import io.element.android.features.messages.impl.utils.table.TableData
import io.element.android.features.messages.impl.utils.table.TableHelper
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
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
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
        if (url.startsWith("latex://")) {
            val formula = Uri.decode(url.removePrefix("latex://"))
            val clipboard = context.getSystemService<ClipboardManager>()
            clipboard?.setPrimaryClip(ClipData.newPlainText("LaTeX Formula", formula))
            Toast.makeText(context, "已复制 LaTeX: $formula", Toast.LENGTH_SHORT).show()
        } else {
            onLinkClick(link)
        }
    }

    val isInPreview = LocalInspectionMode.current
    val emojiOnly = remember(content.body, content.formattedBody, isInPreview) {
        content.formattedBody.toString() == content.body &&
            content.body.replace(" ", "").let { body ->
                if (isInPreview) body == AN_EMOJI_ONLY_TEXT else body.containsOnlyEmojis()
            }
    }
    val textStyle = when {
        emojiOnly -> ElementTheme.typography.fontHeadingXlRegular
        else -> ElementTheme.typography.fontBodyLgRegular
    }
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
        val segments = remember(text, isRenderLatexEnabled) {
            val mathSegments = if (isRenderLatexEnabled) {
                LatexHelper.splitByBlockMath(text)
            } else {
                listOf(LatexHelper.TextSegment.Text(text))
            }
            val result = mutableListOf<TimelineItemSegment>()
            for (mSeg in mathSegments) {
                when (mSeg) {
                    is LatexHelper.TextSegment.BlockMath -> {
                        result.add(TimelineItemSegment.BlockMath(mSeg.formula))
                    }
                    is LatexHelper.TextSegment.Text -> {
                        val tableSegments = TableHelper.splitByTables(mSeg.text)
                        for (tSeg in tableSegments) {
                            when (tSeg) {
                                is TableHelper.TableSegment.Text -> {
                                    result.add(TimelineItemSegment.Text(tSeg.text))
                                }
                                is TableHelper.TableSegment.Table -> {
                                    result.add(TimelineItemSegment.Table(tSeg.tableData, tSeg.rawMarkdown))
                                }
                            }
                        }
                    }
                }
            }
            result
        }

        val astBlocks = remember(content.body, content.htmlDocument) {
            if (content.htmlDocument != null) {
                HtmlToMessageAstParser.parse(content.htmlDocument!!)
            } else {
                MarkdownToMessageAstParser.parse(content.body)
            }
        }
        val hasRichBlocks = remember(astBlocks) {
            astBlocks.any {
                it is MessageBlock.Table ||
                    it is MessageBlock.CodeBlock ||
                    it is MessageBlock.Heading ||
                    it is MessageBlock.Quote ||
                    it is MessageBlock.ListBlock ||
                    it is MessageBlock.BlockMath
            }
        }

        if (hasRichBlocks) {
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
                    onLinkClick = { url -> onLinkClick(Link(url)) },
                )
            }
        } else if (segments.size == 1 && segments[0] is TimelineItemSegment.Text) {
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
            Column(
                modifier = modifier.semantics { contentDescription = content.plainText },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                segments.forEachIndexed { index, segment ->
                    val isLast = index == segments.lastIndex
                    when (segment) {
                        is TimelineItemSegment.Text -> {
                            EditorStyledText(
                                text = segment.text,
                                onLinkClickedListener = handleLinkClick,
                                onLinkLongClickedListener = onLinkLongClick,
                                style = ElementRichTextEditorStyle.textStyle(),
                                onTextLayout = if (isLast) {
                                    ContentAvoidingLayout.measureLegacyLastTextLine(onContentLayoutChange = onContentLayoutChange)
                                } else {
                                    {}
                                },
                                releaseOnDetach = false,
                            )
                        }
                        is TimelineItemSegment.BlockMath -> {
                            val formula = segment.formula
                            val fullFormula = "\$\$$formula\$\$"
                            val onLongClickBlock = {
                                onLinkLongClick(Link("latex://${Uri.encode(fullFormula)}"))
                            }
                            if (isLast) {
                                Box(
                                    modifier = Modifier.onSizeChanged { size ->
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
                                    LatexBlockMathView(
                                        rawFormula = formula,
                                        onLongClick = onLongClickBlock,
                                    )
                                }
                            } else {
                                LatexBlockMathView(
                                    rawFormula = formula,
                                    onLongClick = onLongClickBlock,
                                )
                            }
                        }
                        is TimelineItemSegment.Table -> {
                            val onLongClickTable = {
                                onLinkLongClick(Link("table://${Uri.encode(segment.rawMarkdown)}"))
                            }
                            if (isLast) {
                                Box(
                                    modifier = Modifier.onSizeChanged { size ->
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
                                    TableBlockView(
                                        tableData = segment.tableData,
                                        onLongClick = onLongClickTable,
                                    )
                                }
                            } else {
                                TableBlockView(
                                    tableData = segment.tableData,
                                    onLongClick = onLongClickTable,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface TimelineItemSegment {
    data class Text(val text: CharSequence) : TimelineItemSegment
    data class BlockMath(val formula: String) : TimelineItemSegment
    data class Table(val tableData: TableData, val rawMarkdown: String) : TimelineItemSegment
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
