package com.qualcomm_toolbox.amethyst.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qualcomm_toolbox.amethyst.R
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystAccent
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystPanel
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystText
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystTextMuted

@Composable
fun SettingsScreen(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onRefreshCache: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AmethystAccent,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Language Section
        SettingsSectionTitle(stringResource(R.string.language))
        LanguageOption(
            label = stringResource(R.string.language_english),
            selected = currentLanguage == "en",
            onClick = { onLanguageChange("en") }
        )
        LanguageOption(
            label = stringResource(R.string.language_french),
            selected = currentLanguage == "fr",
            onClick = { onLanguageChange("fr") }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Cache Section
        SettingsSectionTitle("Cache")
        SettingsItem(
            icon = Icons.Default.Refresh,
            label = stringResource(R.string.refresh_cache),
            onClick = onRefreshCache
        )

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(32.dp))

        // About section (moved to text at the bottom)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.about_made_by),
                color = AmethystTextMuted,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(R.string.about_backend),
                color = AmethystTextMuted,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(R.string.about_mysql),
                color = AmethystTextMuted,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_copyright),
                color = AmethystTextMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = AmethystAccent,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) AmethystAccent else AmethystTextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, color = AmethystText, fontSize = 16.sp)
    }
}

@Composable
fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AmethystPanel, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = AmethystAccent)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = AmethystText, fontSize = 16.sp)
    }
}
