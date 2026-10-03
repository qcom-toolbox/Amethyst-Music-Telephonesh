package com.amethyst_music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amethyst_music.R
import com.amethyst_music.data.SearchText
import com.amethyst_music.data.Track
import com.amethyst_music.ui.theme.*

data class ThemePreset(
    val name: String,
    val backgroundColor: Long,
    val useHarmony: Boolean,
    val isDynamic: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    currentBackgroundColor: Long,
    currentUseHarmony: Boolean,
    currentDynamicThemeEnabled: Boolean = false,
    onThemeChange: (Long, Boolean, Boolean) -> Unit,
    currentDynamicThemeFullPlayerOnly: Boolean = false,
    onDynamicThemeFullPlayerOnlyChange: (Boolean) -> Unit = {},
    onRefreshCache: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenBulkDownload: () -> Unit,
    onOpenHistory: () -> Unit = {},
    defaultPlaybackSpeed: Float = 1f,
    onDefaultPlaybackSpeedChange: (Float) -> Unit = {},
    isAdmin: Boolean = false,
    adminModeEnabled: Boolean = false,
    onAdminModeChange: (Boolean) -> Unit = {},
    artistLinksEnabled: Boolean = true,
    onArtistLinksEnabledChange: (Boolean) -> Unit = {},
    artistLinksInListsEnabled: Boolean = true,
    onArtistLinksInListsEnabledChange: (Boolean) -> Unit = {},
    ignorableGenres: List<String> = emptyList(),
    ignoredGenres: Set<String> = emptySet(),
    onGenreIgnoredChange: (String, Boolean) -> Unit = { _, _ -> },
    onClearIgnoredGenres: () -> Unit = {},
    isOnline: Boolean = true,
    isCheckingConnection: Boolean = false,
    onCheckConnection: () -> Unit = {},
    searchQuery: String = "",
    // True when shown as the top section of the search results (searching from Settings also
    // finds songs): no scroll of its own, since it's one item inside the results list.
    embedded: Boolean = false,
    // Whether the rest of the search results found anything — decides who shows "no results".
    hasOtherResults: Boolean = false,
    // Account actions moved here from the header; null hides the row (offline-only mode).
    onUpload: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val versionDisplay = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.2"
    }

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
        ThemePreset("Dynamic", 0xFF0F0C1D, true, isDynamic = true),
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

    // The shared search bar filters Settings section by section: a section stays if its title
    // or any label/hint inside it matches, so "speed" finds Audio and "amoled" finds Theme. Same
    // punctuation rules as the music search (see SearchText).
    val query = SearchText.prepareQuery(searchQuery)
    val isSearching = query.isNotEmpty()
    fun matches(labels: List<String>) = !isSearching || labels.any { SearchText.matches(it, query) }

    val untaggedGenreLabel = stringResource(R.string.genre_other)
    fun genreLabel(genre: String) = if (genre == Track.UNTAGGED_GENRE) untaggedGenreLabel else genre

    val showLanguage = matches(listOf(stringResource(R.string.language)) + languages.map { it.second })
    val showTheme = matches(
        listOf(
            stringResource(R.string.theme_selection),
            stringResource(R.string.dynamic_theme_full_player_only),
            stringResource(R.string.dynamic_theme_full_player_only_hint),
        ) + themes.map { it.name }
    )
    val showAudio = matches(
        listOf(
            stringResource(R.string.audio),
            stringResource(R.string.equalizer),
            stringResource(R.string.default_playback_speed),
            stringResource(R.string.default_playback_speed_description),
        )
    )
    val showCache = matches(
        listOf(stringResource(R.string.cache), stringResource(R.string.refresh_cache), stringResource(R.string.bulk_download))
    )
    val showHistory = matches(listOf(stringResource(R.string.header_history), stringResource(R.string.recently_played)))
    val showArtistPages = matches(
        listOf(
            stringResource(R.string.tab_library),
            stringResource(R.string.artist_links),
            stringResource(R.string.artist_links_hint),
            stringResource(R.string.artist_links_lists),
            stringResource(R.string.artist_links_lists_hint),
        )
    )
    // Ignored Genres also matches on the genre names themselves, so searching "rap" here jumps
    // straight to the chip. If the section's own title/hint matched, every chip stays visible;
    // if only some genres matched, just those chips are shown.
    val ignoredGenresSectionMatches = matches(
        listOf(stringResource(R.string.ignored_genres), stringResource(R.string.ignored_genres_hint))
    )
    val visibleIgnorableGenres = if (ignoredGenresSectionMatches) {
        ignorableGenres
    } else {
        ignorableGenres.filter { SearchText.matches(genreLabel(it), query) }
    }
    val showIgnoredGenres = ignoredGenresSectionMatches || visibleIgnorableGenres.isNotEmpty()
    val showConnection = matches(
        listOf(
            stringResource(R.string.connection),
            stringResource(R.string.check_connection),
            stringResource(R.string.connection_online),
            stringResource(R.string.connection_offline),
        )
    )
    val showUpload = onUpload != null && matches(listOf(stringResource(R.string.upload)))
    val showAccount = onLogout != null && matches(listOf(stringResource(R.string.account), stringResource(R.string.logout)))
    val showAdmin = isAdmin && matches(
        listOf(stringResource(R.string.admin), stringResource(R.string.admin_mode), stringResource(R.string.admin_mode_hint))
    )
    val showVersion = matches(listOf(stringResource(R.string.version), versionDisplay))
    val nothingMatches = !(showUpload || showLanguage || showTheme || showAudio || showCache || showHistory ||
        showArtistPages || showIgnoredGenres || showConnection || showAccount || showAdmin || showVersion)

    if (isSearching && nothingMatches) {
        // No setting matches: stay out of the way if the song results found something,
        // otherwise this is the one "no results" message for the whole search.
        if (!hasOtherResults) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.search_no_results, searchQuery.trim()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
        }
        return
    }

    Column(
        modifier = if (embedded) {
            // TrackList already pads its rows by 16dp; 4 more lines this up with the 20dp the
            // standalone screen uses.
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
        } else {
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        }
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            fontSize = if (embedded) 18.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = if (embedded) 12.dp else 24.dp)
        )

        // Upload — first thing in Settings, since it's an action people come here to do
        // rather than a preference.
        if (showUpload) {
            SettingsItem(
                icon = Icons.Default.Upload,
                label = stringResource(R.string.upload),
                onClick = onUpload
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Language Section
        if (showLanguage) {
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
        }

        // Theme Section
        if (showTheme) {
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
                    val isSelected = preset.backgroundColor == currentBackgroundColor &&
                        preset.useHarmony == currentUseHarmony &&
                        preset.isDynamic == currentDynamicThemeEnabled
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onThemeChange(preset.backgroundColor, preset.useHarmony, preset.isDynamic) }
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

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dynamic_theme_full_player_only),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    )
                    Text(
                        text = stringResource(R.string.dynamic_theme_full_player_only_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = currentDynamicThemeFullPlayerOnly,
                    onCheckedChange = onDynamicThemeFullPlayerOnlyChange,
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

        // Audio Section
        if (showAudio) {
            SettingsSectionTitle(stringResource(R.string.audio))
            SettingsItem(
                icon = Icons.Default.Equalizer,
                label = stringResource(R.string.equalizer),
                onClick = onOpenEqualizer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.default_playback_speed), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                        Text(text = stringResource(R.string.default_playback_speed_description), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Text(
                        text = formatSpeed(defaultPlaybackSpeed),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = defaultPlaybackSpeed,
                    onValueChange = { onDefaultPlaybackSpeedChange(PLAYBACK_SPEEDS.minByOrNull { s -> kotlin.math.abs(s - it) } ?: it) },
                    valueRange = 0.5f..2f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Cache Section
        if (showCache) {
            SettingsSectionTitle(stringResource(R.string.cache))
            SettingsItem(
                icon = Icons.Default.Refresh,
                label = stringResource(R.string.refresh_cache),
                onClick = onRefreshCache
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsItem(
                icon = Icons.Default.Download,
                label = stringResource(R.string.bulk_download),
                onClick = onOpenBulkDownload
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // History Section
        if (showHistory) {
            SettingsSectionTitle(stringResource(R.string.header_history))
            SettingsItem(
                icon = Icons.Default.History,
                label = stringResource(R.string.recently_played),
                onClick = onOpenHistory
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Artist Pages Section
        if (showArtistPages) {
            SettingsSectionTitle(stringResource(R.string.tab_library))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.artist_links), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    Text(text = stringResource(R.string.artist_links_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Switch(
                    checked = artistLinksEnabled,
                    onCheckedChange = onArtistLinksEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.outline,
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val listToggleAlpha = if (artistLinksEnabled) 1f else 0.5f
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.artist_links_lists),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = listToggleAlpha),
                        fontSize = 16.sp
                    )
                    Text(
                        text = stringResource(R.string.artist_links_lists_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = listToggleAlpha),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = artistLinksInListsEnabled,
                    onCheckedChange = onArtistLinksInListsEnabledChange,
                    enabled = artistLinksEnabled,
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

        // Ignored Genres Section
        if (showIgnoredGenres) {
            SettingsSectionTitle(stringResource(R.string.ignored_genres))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.ignored_genres_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                if (ignorableGenres.isEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.ignored_genres_none_available),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    // The backend's fallback genre is a French literal ("Autre") that doubles as
                    // "untagged", so it's shown under the translated Other label (genreLabel) rather than raw.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        visibleIgnorableGenres.forEach { genre ->
                            val isIgnored = ignoredGenres.contains(genre)
                            val label = genreLabel(genre)
                            FilterChip(
                                selected = isIgnored,
                                onClick = { onGenreIgnoredChange(genre, !isIgnored) },
                                label = { Text(label, fontSize = 13.sp) },
                                leadingIcon = if (isIgnored) {
                                    { Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isIgnored,
                                    borderColor = MaterialTheme.colorScheme.outline,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                    if (ignoredGenres.isNotEmpty()) {
                        TextButton(onClick = onClearIgnoredGenres) {
                            Text(
                                text = stringResource(R.string.ignored_genres_clear),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Connection Section
        if (showConnection) {
            SettingsSectionTitle(stringResource(R.string.connection))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .clickable(enabled = !isCheckingConnection, onClick = onCheckConnection)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.check_connection), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    Text(
                        text = if (isOnline) stringResource(R.string.connection_online) else stringResource(R.string.connection_offline),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                if (isCheckingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Admin Section
        if (showAdmin) {
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

        // Account Section — Log out sits below Admin, near the bottom, away from everyday
        // settings. Not inside Admin itself: that section only exists for admins.
        if (showAccount) {
            SettingsSectionTitle(stringResource(R.string.account))
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = stringResource(R.string.logout),
                onClick = onLogout,
                contentColor = MaterialTheme.colorScheme.error,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Version Section
        if (showVersion) {
            SettingsSectionTitle(stringResource(R.string.version))
            Text(
                text = versionDisplay,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        // About section
        if (!isSearching) {
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
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    // Overrides the icon and label color — e.g. the error color for Log out.
    contentColor: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor ?: MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = contentColor ?: MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
    }
}
