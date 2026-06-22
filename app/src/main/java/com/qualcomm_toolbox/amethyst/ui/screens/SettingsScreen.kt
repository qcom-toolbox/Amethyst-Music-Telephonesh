package com.qualcomm_toolbox.amethyst.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qualcomm_toolbox.amethyst.R
import com.qualcomm_toolbox.amethyst.ui.theme.*

data class ThemePreset(
    val name: String,
    val backgroundColor: Long,
    val useHarmony: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    currentBackgroundColor: Long,
    currentUseHarmony: Boolean,
    onThemeChange: (Long, Boolean) -> Unit,
    onRefreshCache: () -> Unit,
    onOpenEqualizer: () -> Unit,
    isAdmin: Boolean = false,
    adminModeEnabled: Boolean = false,
    onAdminModeChange: (Boolean) -> Unit = {},
) {
    val languages = listOf(
        "en" to stringResource(R.string.language_english),
        "fr" to stringResource(R.string.language_french),
        "de" to stringResource(R.string.language_german),
        "it" to stringResource(R.string.language_italian),
        "es" to stringResource(R.string.language_spanish),
        "rm" to stringResource(R.string.language_romansh),
        "ru" to stringResource(R.string.language_russian),
        "zh" to stringResource(R.string.language_chinese),
        "ja" to stringResource(R.string.language_japanese),
        "hi" to stringResource(R.string.language_hindi),
        "mn" to stringResource(R.string.language_mongolian),
    )

    val themes = listOf(
        ThemePreset("Amethyst", 0xFF0F0C1D, false),
        ThemePreset("Dynamic", 0xFF0F0C1D, true),
        ThemePreset("White Mode", 0xFFFFFFFF, true),
        ThemePreset("AMOLED", 0xFF000000, true),
        ThemePreset("Vibrant Purple", 0xFF4A148C, true),
        ThemePreset("Electric Blue", 0xFF0D47A1, true),
        ThemePreset("Deep Teal", 0xFF004D40, true),
        ThemePreset("Cherry", 0xFF880E4F, true),
        ThemePreset("Midnight", 0xFF0A0E1A, true),
        ThemePreset("Forest", 0xFF0D140D, true),
        ThemePreset("Crimson", 0xFF140D0D, true),
        ThemePreset("Slate", 0xFF1A1A1B, true),
        ThemePreset("Jet Black", 0xFF0A0A0A, true),
        ThemePreset("Material", 0xFF121212, true),
    )

    var isExpanded by remember { mutableStateOf(false) }
    val currentLangLabel = languages.find { it.first == currentLanguage }?.second ?: languages.first().second

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
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Language Section
        SettingsSectionTitle(stringResource(R.string.language))
        
        ExposedDropdownMenuBox(
            expanded = isExpanded,
            onExpandedChange = { isExpanded = !isExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = currentLangLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                languages.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            onLanguageChange(code)
                            isExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Theme Section
        SettingsSectionTitle(stringResource(R.string.theme_selection))
        
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(themes) { preset ->
                val isSelected = preset.backgroundColor == currentBackgroundColor && preset.useHarmony == currentUseHarmony
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onThemeChange(preset.backgroundColor, preset.useHarmony) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(preset.backgroundColor))
                            .border(
                                width = 2.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = preset.name,
                        fontSize = 10.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Audio Section
        SettingsSectionTitle(stringResource(R.string.audio))
        SettingsItem(
            icon = Icons.Default.Equalizer,
            label = stringResource(R.string.equalizer),
            onClick = onOpenEqualizer
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Cache Section
        SettingsSectionTitle(stringResource(R.string.cache))
        SettingsItem(
            icon = Icons.Default.Refresh,
            label = stringResource(R.string.refresh_cache),
            onClick = onRefreshCache
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Admin Section
        if (isAdmin) {
            SettingsSectionTitle(stringResource(R.string.admin))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.admin_mode), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    Text(text = stringResource(R.string.admin_mode_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Switch(
                    checked = adminModeEnabled,
                    onCheckedChange = onAdminModeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.outline,
                    )
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Version Section
        SettingsSectionTitle(stringResource(R.string.version))
        Text(
            text = "1.0",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // About section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.about_made_by),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(R.string.about_backend),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(R.string.about_mysql),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_copyright),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
    }
}
