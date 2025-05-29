package ua.pp.soulrise.storog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

@Composable
fun HelpDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Help") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val uriHandler = LocalUriHandler.current
                val annotatedString = buildAnnotatedString {
                append("Application for monitoring changes via camera:\n\n")
                append("1. Find out your Telegram User ID by launching the bot ")
                pushStringAnnotation(tag = "URL", annotation = "https://t.me/userinfobot")
                withStyle(style = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline)) {
                    append("https://t.me/userinfobot")
                }
                pop()
                append("\nand enter it in the application settings.\n")
                append("2. Launch the bot for monitoring ")
                pushStringAnnotation(tag = "URL", annotation = "https://t.me/sto_rog_bot")
                withStyle(style = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline)) {
                    append("https://t.me/sto_rog_bot")
                }
                pop()
                append("\n3. Set the trigger threshold (in percent) using the '+' and '-' buttons. The system will react if the difference between the current and initial frame exceeds this value. A smaller value means higher sensitivity to changes.\n")
                append("4. Enter a query for AI image analysis\n")
                append("5. Press 'Start' to begin monitoring\n")
                append("6. When changes are detected, the photo will be sent to AI for analysis\n")
                append("7. If the AI detects the desired object, a message will be sent to Telegram\n")
            }
                ClickableText(
                    text = annotatedString,
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let {
                                uriHandler.openUri(it.item)
                            }
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}