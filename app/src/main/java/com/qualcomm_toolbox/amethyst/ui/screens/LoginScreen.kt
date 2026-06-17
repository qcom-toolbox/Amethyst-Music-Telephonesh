package com.qualcomm_toolbox.amethyst.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qualcomm_toolbox.amethyst.R
import com.qualcomm_toolbox.amethyst.ui.components.AuthScreenLayout
import com.qualcomm_toolbox.amethyst.ui.components.authFieldColors
import com.qualcomm_toolbox.amethyst.ui.components.authFieldShape
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystDanger
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystPrimary
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystTextMuted
import com.qualcomm_toolbox.amethyst.ui.theme.AmethystText

@Composable
fun LoginScreen(
    siteName: String,
    savedUsername: String?,
    isLoading: Boolean,
    error: String?,
    hasOfflineLibrary: Boolean,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onOpenOffline: () -> Unit,
    onChangeServer: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf(savedUsername.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }

    AuthScreenLayout {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AmethystPrimary)
                    Text(
                        text = if (password.isEmpty()) stringResource(R.string.auto_login) else stringResource(R.string.logging_in),
                        color = AmethystTextMuted,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            return@AuthScreenLayout
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(64.dp))
            Text(
                text = siteName,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.login_required),
                color = AmethystTextMuted,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = authFieldShape,
                colors = authFieldColors(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = authFieldShape,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = authFieldColors(),
            )

            if (error != null) {
                Text(
                    text = error,
                    color = AmethystDanger,
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onLogin(username, password) },
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
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
                    Text(stringResource(R.string.login), fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = { onRegister(username, password) },
                enabled = !isLoading && username.isNotBlank() && password.length >= 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.create_account))
            }

            if (hasOfflineLibrary) {
                OutlinedButton(
                    onClick = onOpenOffline,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmethystTextMuted),
                ) {
                    Text(stringResource(R.string.listen_offline))
                }
            }

            TextButton(onClick = onChangeServer, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.change_server), color = AmethystTextMuted)
            }
        }
    }
}
