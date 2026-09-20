package com.example.personallangmaster.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.personallangmaster.ui.theme.PersonalLangMasterTheme

enum class Speaker { TUTOR, USER }

/**
 * Строка субтитров (как облачко чата). Тренер - слева (secondary), ученик - справа (primary).
 * Если фраза не закончена (isPartial), в конце мигает курсор (▌).
 */
@Composable
fun SubtitleLine(
    speaker: Speaker,
    text: String,
    isPartial: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isTutor = speaker == Speaker.TUTOR
    val alignment = if (isTutor) Alignment.Start else Alignment.End
    val bubbleColor = if (isTutor) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val textColor = if (isTutor) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    val shape = if (isTutor) {
        RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }

    Column(
        horizontalAlignment = alignment,
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )

                if (isPartial) {
                    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 800
                                0f at 0
                                1f at 400
                                1f at 800
                            },
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "cursorAlpha"
                    )

                    Text(
                        text = " ▌",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Black),
                        color = textColor,
                        modifier = Modifier.alpha(alpha)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SubtitleLinePreview() {
    PersonalLangMasterTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SubtitleLine(speaker = Speaker.TUTOR, text = "So, what did you do yesterday?")
                SubtitleLine(speaker = Speaker.USER, text = "Yesterday I go to the", isPartial = true)
            }
        }
    }
}
