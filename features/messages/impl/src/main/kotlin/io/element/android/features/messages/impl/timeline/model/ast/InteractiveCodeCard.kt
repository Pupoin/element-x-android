/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.ast

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import io.element.android.compound.theme.ElementTheme

@Composable
fun InteractiveCodeCard(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val copyAction = {
        val clipboard = context.getSystemService<ClipboardManager>()
        clipboard?.setPrimaryClip(ClipData.newPlainText("Code Block", code))
        Toast.makeText(context, "已复制代码块", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x10808080), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x2D808080), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column {
            if (language != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0x22808080), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = language.uppercase(),
                            style = ElementTheme.typography.fontBodyXsMedium,
                            color = ElementTheme.colors.textSecondary,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                Text(
                    text = code,
                    style = ElementTheme.typography.fontBodyMdRegular.copy(fontFamily = FontFamily.Monospace),
                    color = ElementTheme.colors.textPrimary,
                )
            }
        }
    }
}
