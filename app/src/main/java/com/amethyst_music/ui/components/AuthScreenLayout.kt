package com.amethyst_music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amethyst_music.ui.theme.AmethystBorder
import com.amethyst_music.ui.theme.AmethystSearchBg
import com.amethyst_music.ui.theme.AmethystText
import com.amethyst_music.ui.theme.AmethystTextMuted

@Composable
fun AuthScreenLayout(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content()
    }
}

@Composable
fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = AmethystBorder,
    focusedContainerColor = AmethystSearchBg,
    unfocusedContainerColor = AmethystSearchBg,
    focusedTextColor = AmethystText,
    unfocusedTextColor = AmethystText,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = AmethystTextMuted,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedPlaceholderColor = AmethystTextMuted,
    unfocusedPlaceholderColor = AmethystTextMuted,
)

val authFieldShape = RoundedCornerShape(12.dp)
