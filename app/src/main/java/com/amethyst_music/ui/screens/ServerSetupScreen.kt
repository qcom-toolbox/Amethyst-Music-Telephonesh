package com.amethyst_music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.amethyst_music.R
import com.amethyst_music.ui.components.AuthScreenLayout
import com.amethyst_music.ui.components.authFieldColors
import com.amethyst_music.ui.components.authFieldShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amethyst_music.ui.theme.AmethystBorder
import com.amethyst_music.ui.theme.AmethystPrimary
import com.amethyst_music.ui.theme.AmethystSearchBg
import com.amethyst_music.ui.theme.AmethystTextMuted

@Composable
fun ServerSetupScreen(
    isLoading: Boolean,
    error: String?,
    onConnect: (String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    AuthScreenLayout {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "◆ Amethyst",
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Music",
            fontSize = 20.sp,
            color = AmethystTextMuted,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = stringResource(R.string.connect_server),
            color = AmethystTextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.server_url)) },
            placeholder = { Text(stringResource(R.string.server_example)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = authFieldShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = authFieldColors(),
        )

        Text(
            text = "Ex. ${stringResource(R.string.server_example)}",
            color = AmethystTextMuted,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        if (error != null) {
            Text(
                text = error,
                color = com.amethyst_music.ui.theme.AmethystDanger,
                modifier = Modifier.padding(top = 12.dp),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onConnect(url) },
            enabled = !isLoading && url.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.connect), fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = stringResource(R.string.no_server_create_own),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 16.dp)
                .clickable { uriHandler.openUri("https://github.com/qcom-toolbox/Amethyst-Music") },
        )
    }
    }
}

@Composable
fun fieldColors() = authFieldColors()
