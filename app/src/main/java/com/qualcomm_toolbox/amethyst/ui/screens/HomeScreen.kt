package com.qualcomm_toolbox.amethyst.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qualcomm_toolbox.amethyst.R
import com.qualcomm_toolbox.amethyst.data.Track
import com.qualcomm_toolbox.amethyst.ui.theme.*

@Composable
fun HomeScreen(
    recommended: List<Track>,
    popular: List<Track>,
    hiddenGems: List<Track>,
    coverUrlForTrack: (Track) -> String?,
    onTrackClick: (Track) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            HomeSection(
                title = stringResource(R.string.home_for_you),
                tracks = recommended,
                coverUrlForTrack = coverUrlForTrack,
                onTrackClick = onTrackClick
            )
        }
        item {
            HomeSection(
                title = stringResource(R.string.home_popular),
                tracks = popular,
                coverUrlForTrack = coverUrlForTrack,
                onTrackClick = onTrackClick
            )
        }
        item {
            HomeSection(
                title = stringResource(R.string.home_hidden_gems),
                tracks = hiddenGems,
                coverUrlForTrack = coverUrlForTrack,
                onTrackClick = onTrackClick
            )
        }
    }
}

@Composable
fun HomeSection(
    title: String,
    tracks: List<Track>,
    coverUrlForTrack: (Track) -> String?,
    onTrackClick: (Track) -> Unit,
) {
    if (tracks.isEmpty()) return

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tracks) { track ->
                HomeTrackCard(
                    track = track,
                    cover = coverUrlForTrack(track),
                    onClick = { onTrackClick(track) }
                )
            }
        }
    }
}

@Composable
fun HomeTrackCard(
    track: Track,
    cover: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        AsyncImage(
            model = cover,
            contentDescription = track.title,
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.outline),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
