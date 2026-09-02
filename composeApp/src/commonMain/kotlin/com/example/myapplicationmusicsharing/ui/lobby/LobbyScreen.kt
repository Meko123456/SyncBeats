@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myapplicationmusicsharing.ui.lobby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplicationmusicsharing.data.RoomCode

@Composable
fun LobbyScreen(
    onEnterRoom: (roomId: String) -> Unit,
    onSignOut: () -> Unit,
    viewModel: LobbyViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.enterRoomId) {
        state.enterRoomId?.let { roomId ->
            viewModel.consumeEnterRoom()
            onEnterRoom(roomId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SyncBeats") },
                actions = {
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.savedRooms.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("My rooms", style = MaterialTheme.typography.titleMedium)
                        state.savedRooms.forEach { room ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(room.name.ifBlank { "Room" }, maxLines = 1)
                                    Text(
                                        room.key,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.openSavedRoom(room) },
                                    enabled = !state.loading,
                                ) { Text("Open") }
                                TextButton(onClick = { viewModel.removeSavedRoom(room) }) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Start a room", style = MaterialTheme.typography.titleMedium)
                    var roomName by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = roomName,
                        onValueChange = { roomName = it },
                        label = { Text("Room name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { viewModel.createRoom(roomName) },
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Create room") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Join a room", style = MaterialTheme.typography.titleMedium)
                    var code by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = code,
                        // Normalising as it is typed, rather than truncating: the old
                        // uppercase().take(6) turned a pasted "ABC-234" into "ABC-23", which then
                        // looked like a valid six-character code and failed at the lookup.
                        onValueChange = { code = RoomCode.normalise(it).take(RoomCode.LENGTH) },
                        label = { Text("Room code") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { viewModel.joinRoom(code) },
                        enabled = !state.loading && RoomCode.isValid(code),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Join") }
                }
            }

            if (state.loading) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
            }
            state.error?.let {
                Text("Error: $it", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
            }
        }
    }
}
