package com.example.personallangmaster.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.personallangmaster.ui.theme.PersonalLangMasterTheme

enum class MicButtonState { READY, LISTENING, THINKING, SPEAKING, DISABLED }

/** Как кнопка понимает жест — от этого зависит, когда реплика считается законченной. */
enum class MicGesture {
    /** Реплика идёт, пока кнопка зажата: отпустил — отправил. */
    HOLD,

    /** Один тап открывает микрофон, второй закрывает. */
    TAP,
}

/**
 * Основная кнопка управления уроком. 
 * Размер: 96dp. Вокруг кнопки рисуется кольцо, реагирующее на громкость (levelDbfs).
 */
@Composable
fun MicButton(
    state: MicButtonState,
    levelDbfs: Double,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onTap: () -> Unit,
    gesture: MicGesture = MicGesture.HOLD,
    modifier: Modifier = Modifier
) {
    // Анимация кольца в зависимости от громкости (от -100 до 0 dBFS)
    // -60 dBFS - порог тишины, 0 dBFS - максимум.
    val targetRingScale = when (state) {
        MicButtonState.LISTENING -> {
            val normalizedVolume = (levelDbfs + 60).coerceIn(0.0, 60.0) / 60.0
            1.1f + (normalizedVolume * 0.4f).toFloat() // Scale от 1.1 до 1.5
        }
        MicButtonState.SPEAKING -> 1.3f // Волна тренера будет анимироваться отдельно или тут статика
        else -> 1.0f
    }

    val ringScale by animateFloatAsState(
        targetValue = targetRingScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ringScale"
    )

    // Если говорит тренер, добавим пульсацию кольцу
    val infiniteTransition = rememberInfiniteTransition(label = "speakingPulse")
    val speakingPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.2f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speakingPulse"
    )

    val finalRingScale = if (state == MicButtonState.SPEAKING) speakingPulseScale else ringScale

    val buttonColor by animateColorAsState(
        targetValue = when (state) {
            MicButtonState.READY -> MaterialTheme.colorScheme.primary
            MicButtonState.LISTENING -> MaterialTheme.colorScheme.tertiary
            MicButtonState.THINKING -> MaterialTheme.colorScheme.secondary
            MicButtonState.SPEAKING -> MaterialTheme.colorScheme.primaryContainer
            MicButtonState.DISABLED -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "buttonColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when (state) {
            MicButtonState.READY -> MaterialTheme.colorScheme.onPrimary
            MicButtonState.LISTENING -> MaterialTheme.colorScheme.onTertiary
            MicButtonState.THINKING -> MaterialTheme.colorScheme.onSecondary
            MicButtonState.SPEAKING -> MaterialTheme.colorScheme.onPrimaryContainer
            MicButtonState.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "contentColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp) // Зона для кольца
        ) {
            // Кольцо-визуализатор
            if (state != MicButtonState.DISABLED) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(finalRingScale)
                        .border(
                            width = 2.dp,
                            color = buttonColor.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                )
            }

            // Сама кнопка 96dp
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(buttonColor)
                    .pointerInput(state, gesture) {
                        if (state == MicButtonState.DISABLED) return@pointerInput
                        when (gesture) {
                            // onTap здесь не вызываем: короткое нажатие — это та же реплика,
                            // уже законченная отпусканием, а не команда начать новую.
                            MicGesture.HOLD -> detectTapGestures(
                                onPress = {
                                    onPress()
                                    tryAwaitRelease()
                                    onRelease()
                                }
                            )
                            MicGesture.TAP -> detectTapGestures(onTap = { onTap() })
                        }
                    }
            ) {
                AnimatedContent(targetState = state, label = "micIcon") { s ->
                    when (s) {
                        MicButtonState.READY, MicButtonState.LISTENING, MicButtonState.DISABLED -> {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Микрофон",
                                tint = contentColor,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        MicButtonState.THINKING -> {
                            Icon(
                                imageVector = Icons.Rounded.MoreHoriz,
                                contentDescription = "Ожидание",
                                tint = contentColor,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        MicButtonState.SPEAKING -> {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Перебить",
                                tint = contentColor,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MicButtonPreviewReady() {
    PersonalLangMasterTheme {
        Surface {
            MicButton(
                state = MicButtonState.READY,
                levelDbfs = -100.0,
                onPress = {}, onRelease = {}, onTap = {},
                modifier = Modifier.padding(32.dp)
            )
        }
    }
}
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MicButtonPreviewTap() {
    PersonalLangMasterTheme {
        Surface {
            MicButton(
                state = MicButtonState.READY,
                levelDbfs = -100.0,
                onPress = {}, onRelease = {}, onTap = {},
                gesture = MicGesture.TAP,
                modifier = Modifier.padding(32.dp)
            )
        }
    }
}
@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MicButtonPreviewListening() {
    PersonalLangMasterTheme {
        Surface {
            MicButton(
                state = MicButtonState.LISTENING,
                levelDbfs = -20.0, // Громкий звук
                onPress = {}, onRelease = {}, onTap = {},
                modifier = Modifier.padding(32.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MicButtonPreviewSpeaking() {
    PersonalLangMasterTheme {
        Surface {
            MicButton(
                state = MicButtonState.SPEAKING,
                levelDbfs = -100.0,
                onPress = {}, onRelease = {}, onTap = {},
                modifier = Modifier.padding(32.dp)
            )
        }
    }
}
