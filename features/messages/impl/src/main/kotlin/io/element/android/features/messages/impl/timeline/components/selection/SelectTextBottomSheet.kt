/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.selection

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectTextBottomSheet(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService<ClipboardManager>() }
    val copiedToastMessage = stringResource(CommonStrings.common_copied_to_clipboard)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        scrollable = false,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(CommonStrings.action_select_text),
                    style = ElementTheme.typography.fontHeadingSmMedium,
                    color = ElementTheme.colors.textPrimary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Copy all button
                    IconButton(
                        onClick = {
                            clipboardManager?.setPrimaryClip(ClipData.newPlainText("Message Text", text))
                            Toast.makeText(context, copiedToastMessage, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = CompoundIcons.Copy(),
                            contentDescription = stringResource(CommonStrings.action_copy_text),
                            tint = ElementTheme.colors.iconPrimary,
                        )
                    }
                    // Close button
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = CompoundIcons.Close(),
                            contentDescription = stringResource(CommonStrings.action_close),
                            tint = ElementTheme.colors.iconPrimary,
                        )
                    }
                }
            }

            HorizontalDivider()

            // Scrollable selectable text area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@PreviewsDayNight
@Composable
internal fun SelectTextBottomSheetPreview() = ElementPreview {
    SelectTextBottomSheet(
        text = "This is a sample message demonstrating text selection in Element X Android. You can select any portion of this message freely.",
        onDismiss = {},
    )
}
