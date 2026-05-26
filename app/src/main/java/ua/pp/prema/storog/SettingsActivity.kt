package ua.pp.prema.storog

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import android.widget.Toast
import ua.pp.prema.storog.ui.theme.StorogTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            StorogTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    var chatId by remember { mutableStateOf(TextFieldValue("")) }
    var botToken by remember { mutableStateOf(TextFieldValue("")) }
    var showDetectHint by remember { mutableStateOf(false) }
    var preferGpu by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("StorogSettings", Context.MODE_PRIVATE)
    }
    val appPreferences = remember {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    // Load saved chat_id and GPU preference on first launch
    LaunchedEffect(Unit) {
        chatId = TextFieldValue(sharedPreferences.getString("TARGET_CHAT_ID", "") ?: "")
        botToken = TextFieldValue(sharedPreferences.getString("MY_BOT_TOKEN", "") ?: "")
        preferGpu = appPreferences.getBoolean("prefer_gpu", false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = chatId,
            onValueChange = { chatId = it },
            label = { Text("Telegram Chat ID") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = botToken,
            onValueChange = { botToken = it },
            label = { Text("Telegram Bot Token (MY_BOT_TOKEN)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        // GPU Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Use GPU (experimental)",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "May cause freezes on some devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Switch(
                checked = preferGpu,
                onCheckedChange = { newValue ->
                    preferGpu = newValue
                    appPreferences.edit().putBoolean("prefer_gpu", newValue).apply()
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                // Show hint dialog before detecting
                showDetectHint = true
            }) {
                Text("Detect Chat ID")
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        if (showDetectHint) {
            AlertDialog(
                onDismissRequest = { showDetectHint = false },
                title = { Text("Detect chat ID") },
                text = { Text("Send any message to your bot in Telegram, then press Detect to auto-fill the Chat ID.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDetectHint = false
                        // perform detection
                        val tokenText = botToken.text.trim()
                        if (tokenText.isEmpty()) {
                            Toast.makeText(context, "Please enter your bot token first.", Toast.LENGTH_LONG).show()
                        } else {
                            coroutineScope.launch {
                                val detected = fetchChatId(tokenText)
                                if (detected != null) {
                                    chatId = TextFieldValue(detected)
                                    Toast.makeText(context, "Detected chat id: $detected", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to detect chat id. Make sure you sent a message to the bot.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }) { Text("Detect") }
                },
                dismissButton = {
                    TextButton(onClick = { showDetectHint = false }) { Text("Cancel") }
                }
            )
        }

        Button(
            onClick = {
                // Save chat_id
                sharedPreferences.edit().apply {
                    putString("TARGET_CHAT_ID", chatId.text)
                    putString("MY_BOT_TOKEN", botToken.text)
                    apply()
                }
                // Close activity
                (context as? ComponentActivity)?.finish()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}