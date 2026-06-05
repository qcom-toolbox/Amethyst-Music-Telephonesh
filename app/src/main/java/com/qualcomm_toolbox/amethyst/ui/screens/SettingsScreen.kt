package com.qualcomm_toolbox.amethyst.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qualcomm_toolbox.amethyst.R
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystAccent
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystPanel
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystText
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystTextMuted
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onRefreshCache: () -> Unit,
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
            color = AmethystAccent,
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
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = AmethystAccent) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AmethystText,
                    unfocusedTextColor = AmethystText,
                    focusedContainerColor = AmethystPanel,
                    unfocusedContainerColor = AmethystPanel,
                    focusedBorderColor = AmethystAccent,
                    unfocusedBorderColor = AmethystBorder,
                    cursorColor = AmethystAccent,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false },
                modifier = Modifier.background(AmethystPanel)
            ) {
                languages.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = AmethystText) },
                        onClick = {
                            onLanguageChange(code)
                            isExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cache Section
        SettingsSectionTitle(stringResource(R.string.refresh_cache).substringBefore(" "))
        SettingsItem(
            icon = Icons.Default.Refresh,
            label = stringResource(R.string.refresh_cache),
            onClick = onRefreshCache
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Version Section
        SettingsSectionTitle("Version")
        Text(
            text = "0.5",
            color = AmethystText,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(AmethystPanel, RoundedCornerShape(12.dp))
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
