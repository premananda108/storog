package ua.pp.soulrise.storog

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ua.pp.soulrise.storog.ui.theme.StorogTheme

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
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("StorogSettings", Context.MODE_PRIVATE)
    }

    // Load saved chat_id on first launch
    LaunchedEffect(Unit) {
        chatId = TextFieldValue(sharedPreferences.getString("TARGET_CHAT_ID", "") ?: "")
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

        Button(
            onClick = {
                // Save chat_id
                sharedPreferences.edit().apply {
                    putString("TARGET_CHAT_ID", chatId.text)
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