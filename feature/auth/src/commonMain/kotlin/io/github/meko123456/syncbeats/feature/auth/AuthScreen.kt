@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.meko123456.syncbeats.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AuthScreen(
    state: AuthState,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String, username: String) -> Unit,
    onGoogleSignIn: () -> Unit,
    navigateNext: () -> Unit,
) {
    LaunchedEffect(state.signedIn) {
        if (state.signedIn) navigateNext()
    }

    var tab by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("SyncBeats") }) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Sign in") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Create account") })
            }

            Spacer(Modifier.height(8.dp))

            val canSubmit = !state.loading &&
                email.isNotBlank() &&
                password.isNotBlank() &&
                (tab == 0 || username.isNotBlank())
            val submit = {
                if (tab == 0) onSignIn(email.trim(), password)
                else onSignUp(email.trim(), password, username)
            }

            // The keyboard's action key goes down the form, and on the password does what the
            // button does, and nothing while the button is disabled.
            if (tab == 1) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canSubmit) submit() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = submit,
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (tab == 0) "Sign in" else "Create account")
            }

            Text("or", style = MaterialTheme.typography.labelMedium)

            OutlinedButton(
                onClick = onGoogleSignIn,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue with Google")
            }

            if (state.loading) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
