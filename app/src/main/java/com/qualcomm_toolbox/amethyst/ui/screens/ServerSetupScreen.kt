package com.qualcomm_toolbox.amethyst.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.qualcomm_toolbox.amethyst.R
import com.qualcomm_toolbox.amethyst.ui.components.AuthScreenLayout
import com.qualcomm_toolbox.amethyst.ui.components.authFieldColors
import com.qualcomm_toolbox.amethyst.ui.components.authFieldShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.os.Build
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystAccent
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystBorder
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystPrimary
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystSearchBg
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystText
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystTextMuted

@Composable
fun ServerSetupScreen(
    isLoading: Boolean,
    error: String?,
    initialTrustAllCerts: Boolean,
    onConnect: (String, Boolean) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("https://web.office-works.ch/Purple") }
    var trustAllCerts by rememberSaveable { mutableStateOf(initialTrustAllCerts) }

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
            color = AmethystAccent,
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
            placeholder = { Text("https://…/Purple") },
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = trustAllCerts,
                onCheckedChange = { trustAllCerts = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = AmethystPrimary,
                    checkmarkColor = AmethystText,
                    uncheckedColor = AmethystBorder,
                ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.trust_all_certs),
                    color = AmethystText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(
                    text = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                        stringResource(R.string.trust_all_certs_m)
                    } else {
                        stringResource(R.string.trust_all_certs_other)
                    },
                    color = AmethystTextMuted,
                    fontSize = 12.sp,
                )
            }
        }

        if (error != null) {
            Text(
                text = error,
                color = com.qualcomm_toolbox.amethyst.ui.theme.AmethystDanger,
                modifier = Modifier.padding(top = 12.dp),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onConnect(url, trustAllCerts) },
            enabled = !isLoading && url.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = AmethystPrimary),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp),
                    color = AmethystText,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.connect), fontWeight = FontWeight.Bold)
            }
        }
    }
    }
}

@Composable
fun fieldColors() = authFieldColors()
