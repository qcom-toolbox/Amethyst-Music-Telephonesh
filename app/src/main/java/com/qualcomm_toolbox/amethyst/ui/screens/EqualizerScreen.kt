package com.qualcomm_toolbox.amethyst.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qualcomm_toolbox.amethyst.R
import com.qualcomm_toolbox.amethyst.player.EqualizerManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    manager: EqualizerManager,
    onClose: () -> Unit
) {
    val isEnabled by manager.isEnabled.collectAsState()
    val bandLevels by manager.bandLevels.collectAsState()
    val currentPreset by manager.currentPreset.collectAsState()
    val presets = remember { manager.getPresets() }
    val range = remember { manager.getBandLevelRange() }
    
    var isPresetsExpanded by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = stringResource(R.string.equalizer),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = { manager.setEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                )
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Presets Dropdown
            if (presets.isNotEmpty()) {
                Column {
                    Text(
                        text = stringResource(R.string.presets),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ExposedDropdownMenuBox(
                        expanded = isPresetsExpanded,
                        onExpandedChange = { isPresetsExpanded = !isPresetsExpanded }
                    ) {
                        val presetName = if (currentPreset >= 0 && currentPreset < presets.size) {
                            presets[currentPreset.toInt()]
                        } else {
                            stringResource(R.string.custom)
                        }

                        OutlinedTextField(
                            value = presetName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isPresetsExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = isPresetsExpanded,
                            onDismissRequest = { isPresetsExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            presets.forEachIndexed { index, name ->
                                DropdownMenuItem(
                                    text = { Text(name, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        manager.usePreset(index.toShort())
                                        isPresetsExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Bands
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                bandLevels.keys.sorted().forEach { band ->
                    val level = bandLevels[band] ?: 0
                    val freq = remember { manager.getBandFrequency(band) }
                    
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatFrequencyCorrected(freq),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${level / 100} dB",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { manager.setBandLevel(band, it.toInt().toShort()) },
                            valueRange = range.first.toFloat()..range.second.toFloat(),
                            enabled = isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
            }

            // Reset Button
            Button(
                onClick = { 
                    // Flat levels
                    bandLevels.keys.forEach { manager.setBandLevel(it, 0) }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.reset))
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun formatFrequencyCorrected(mHz: Int): String {
    val hz = mHz / 1000
    return when {
        hz >= 1000 -> "${hz / 1000} kHz"
        else -> "$hz Hz"
    }
}
