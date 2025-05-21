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
        title = { Text("Справка") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val uriHandler = LocalUriHandler.current
                val annotatedString = buildAnnotatedString {
                append("Приложение для мониторинга изменений через камеру:\n\n")
                append("1. Узнайте ваш Telegram User ID, запустив бота ")
                pushStringAnnotation(tag = "URL", annotation = "https://t.me/userinfobot")
                withStyle(style = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline)) {
                    append("https://t.me/userinfobot")
                }
                pop()
                append("\nи введите его в настройках приложения.\n")
                append("2. Запустите бота для мониторинга ")
                pushStringAnnotation(tag = "URL", annotation = "https://t.me/sto_rog_bot")
                withStyle(style = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline)) {
                    append("https://t.me/sto_rog_bot")
                }
                pop()
                append("\n3. Установите порог срабатывания (в процентах) кнопками '+' и '-'. Система среагирует, если различие между текущим и начальным кадром превысит это значение. Меньшее значение означает более высокую чувствительность к изменениям\n")
                append("4. Введите запрос для ИИ-анализа изображений\n")
                append("5. Нажмите 'Старт' для начала мониторинга\n")
                append("6. При обнаружении изменений фото будет отправлено ИИ для анализа\n")
                append("7. Если ИИ обнаружит нужный объект - будет отправленно сообщение в Telegram\n")
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
                Text("Закрыть")
            }
        }
    )
}