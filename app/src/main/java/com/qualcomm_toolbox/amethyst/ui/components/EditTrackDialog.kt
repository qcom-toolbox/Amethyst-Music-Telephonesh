package com.qualcomm_toolbox.amethyst.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qualcomm_toolbox.amethyst.R
import com.qualcomm_toolbox.amethyst.data.Track
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystAccent
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystPanel
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystText
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystTextMuted
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystPrimary
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTrackDialog(
    track: Track,
    genres: List<String>,
    onDismiss: () -> Unit,
    onSave: (id: Int, title: String, artist: String, genre: String, cover: ByteArray?, coverName: String?) -> Unit,
    onDelete: (id: Int) -> Unit
) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var genre by remember { mutableStateOf(track.genre) }
    var coverUri by remember { mutableStateOf<Uri?>(null) }
    var coverName by remember { mutableStateOf("") }
    
    var showDeleteConfirm by remember { mutableStateOf(0) } // 0: none, 1: first, 2: second, 3: last

    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coverUri = it
            readUriBytes(context, it)?.let { pair ->
                coverName = pair.second
            }
        }
    }

    if (showDeleteConfirm > 0) {
        val (confirmText, confirmColor) = when (showDeleteConfirm) {
            1 -> stringResource(R.string.delete_confirm_1) to AmethystText
            2 -> stringResource(R.string.delete_confirm_2) to AmethystAccent
            else -> stringResource(R.string.delete_confirm_3) to androidx.compose.ui.graphics.Color.Red
        }

        AlertDialog(
            onDismissRequest = { showDeleteConfirm = 0 },
            containerColor = AmethystPanel,
            title = { Text(stringResource(R.string.delete_track), color = AmethystAccent, fontWeight = FontWeight.Bold) },
            text = { Text(confirmText, color = confirmColor) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (showDeleteConfirm < 3) {
                            showDeleteConfirm++
                        } else {
                            onDelete(track.id)
                            showDeleteConfirm = 0
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete_track), color = if (showDeleteConfirm == 3) androidx.compose.ui.graphics.Color.Red else AmethystAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = 0 }) {
                    Text(stringResource(R.string.close), color = AmethystTextMuted)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmethystPanel,
        title = { Text(stringResource(R.string.edit_metadata), color = AmethystAccent, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = amethystFieldColors(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artiste") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = amethystFieldColors(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = isExpanded,
                    onExpandedChange = { isExpanded = !isExpanded }
                ) {
                    OutlinedTextField(
                        value = genre,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Genre") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                        colors = amethystFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isExpanded,
                        onDismissRequest = { isExpanded = false },
                        modifier = Modifier.background(AmethystPanel)
                    ) {
                        genres.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g, color = AmethystText) },
                                onClick = {
                                    genre = g
                                    isExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { coverPicker.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = AmethystBorder)
                    ) {
                        Text("Pochette")
                    }
                    Text(
                        text = coverName.ifEmpty { "Garder l'actuelle" },
                        color = AmethystTextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { showDeleteConfirm = 1 },
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.delete_track), color = androidx.compose.ui.graphics.Color.White)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cUri = coverUri
                    val cData = cUri?.let { readUriBytes(context, it) }
                    onSave(
                        track.id,
                        title,
                        artist,
                        genre,
                        cData?.first,
                        cData?.second
                    )
                }
            ) {
                Text(stringResource(R.string.save_changes), color = AmethystAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = AmethystTextMuted)
            }
        }
    )
}

private fun readUriBytes(context: android.content.Context, uri: Uri): Pair<ByteArray, String>? {
    return try {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val bytes = inputStream.readBytes()
        inputStream.close()

        var fileName = "file"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        bytes to fileName
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun amethystFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AmethystText,
    unfocusedTextColor = AmethystText,
    focusedContainerColor = AmethystPanel,
    unfocusedContainerColor = AmethystPanel,
    focusedBorderColor = AmethystAccent,
    unfocusedBorderColor = AmethystBorder,
    focusedLabelColor = AmethystAccent,
    unfocusedLabelColor = AmethystTextMuted,
    cursorColor = AmethystAccent,
)
