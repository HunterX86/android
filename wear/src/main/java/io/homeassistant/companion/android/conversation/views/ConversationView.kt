package io.homeassistant.companion.android.conversation.views

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.LocalContentColor
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.assist.AssistViewModelBase
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.conversation.ConversationViewModel
import io.homeassistant.companion.android.home.views.TimeText
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.util.KeepScreenOn
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

private const val SCREEN_CONVERSATION = "conversation"
private const val SCREEN_PIPELINES = "pipelines"

@Composable
fun LoadAssistView(
    conversationViewModel: ConversationViewModel,
    onVoiceInputIntent: () -> Unit
) {
    WearAppTheme {
        val swipeDismissableNavController = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(
            navController = swipeDismissableNavController,
            startDestination = SCREEN_CONVERSATION
        ) {
            composable(SCREEN_CONVERSATION) {
                ConversationResultView(
                    conversation = conversationViewModel.conversation,
                    inputMode = conversationViewModel.inputMode,
                    currentPipeline = conversationViewModel.currentPipeline,
                    hapticFeedback = conversationViewModel.isHapticEnabled,
                    onChangePipeline = {
                        conversationViewModel.onConversationScreenHidden()
                        swipeDismissableNavController.navigate(SCREEN_PIPELINES)
                    },
                    onMicrophoneInput = {
                        if (conversationViewModel.usePipelineStt()) {
                            conversationViewModel.onMicrophoneInput()
                        } else {
                            onVoiceInputIntent()
                        }
                    }
                )
            }
            composable(SCREEN_PIPELINES) {
                ConversationPipelinesView(
                    pipelines = conversationViewModel.pipelines,
                    onSelectPipeline = {
                        conversationViewModel.changePipeline(it)
                        swipeDismissableNavController.navigateUp()
                    }
                )
            }
        }
    }
}

@Composable
fun ConversationResultView(
    conversation: List<AssistMessage>,
    inputMode: AssistViewModelBase.AssistInputMode,
    currentPipeline: AssistPipelineResponse?,
    hapticFeedback: Boolean,
    onChangePipeline: () -> Unit,
    onMicrophoneInput: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()

    Scaffold(
        positionIndicator = {
            if (scrollState.isScrollInProgress) {
                PositionIndicator(scalingLazyListState = scrollState)
            }
        },
        timeText = { TimeText(scalingLazyListState = scrollState) }
    ) {
        LaunchedEffect(conversation.size) {
            scrollState.scrollToItem(
                if (inputMode != AssistViewModelBase.AssistInputMode.BLOCKED) conversation.size else (conversation.size - 1)
            )
        }
        if (hapticFeedback) {
            val haptic = LocalHapticFeedback.current
            LaunchedEffect("${conversation.size}.${conversation.lastOrNull()?.message?.length}") {
                val message = conversation.lastOrNull() ?: return@LaunchedEffect
                if (conversation.size > 1 && !message.isInput && message.message != "…") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }

        ThemeLazyColumn(state = scrollState) {
            item {
                if (currentPipeline != null) {
                    val textColor = LocalContentColor.current.copy(alpha = 0.38f) // disabled/hint alpha
                    Row(
                        modifier = Modifier
                            .clickable(
                                onClick = { onChangePipeline() },
                                onClickLabel = stringResource(R.string.assist_change_pipeline)
                            )
                            .padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = currentPipeline.name,
                            fontSize = 11.sp,
                            color = textColor
                        )
                        Image(
                            asset = CommunityMaterial.Icon.cmd_chevron_right,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(start = 4.dp),
                            colorFilter = ColorFilter.tint(textColor)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            items(conversation) {
                SpeechBubble(text = it.message, isResponse = !it.isInput)
            }
            if (inputMode != AssistViewModelBase.AssistInputMode.BLOCKED) {
                item {
                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val inputIsActive = inputMode == AssistViewModelBase.AssistInputMode.VOICE_ACTIVE
                        if (inputIsActive) {
                            KeepScreenOn()
                            val transition = rememberInfiniteTransition()
                            val scale by transition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.2f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .scale(scale)
                                    .background(color = colorResource(R.color.colorSpeechText), shape = CircleShape)
                                    .clip(CircleShape)
                            )
                        }
                        Button(
                            onClick = { onMicrophoneInput() },
                            colors =
                            if (inputIsActive) {
                                ButtonDefaults.secondaryButtonColors(backgroundColor = Color.Transparent, contentColor = Color.Black)
                            } else {
                                ButtonDefaults.secondaryButtonColors()
                            }
                        ) {
                            Image(
                                asset = CommunityMaterial.Icon3.cmd_microphone,
                                contentDescription = stringResource(R.string.assist_start_listening),
                                colorFilter = ColorFilter.tint(LocalContentColor.current)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpeechBubble(text: String, isResponse: Boolean) {
    Row(
        horizontalArrangement = if (isResponse) Arrangement.Start else Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isResponse) 0.dp else 24.dp,
                end = if (isResponse) 24.dp else 0.dp,
                top = 4.dp,
                bottom = 4.dp
            )
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isResponse) {
                        colorResource(R.color.colorAccent)
                    } else {
                        colorResource(R.color.colorSpeechText)
                    },
                    AbsoluteRoundedCornerShape(
                        topLeft = 12.dp,
                        topRight = 12.dp,
                        bottomLeft = if (isResponse) 0.dp else 12.dp,
                        bottomRight = if (isResponse) 12.dp else 0.dp
                    )
                )
                .padding(4.dp)
        ) {
            Text(
                text = text,
                color = if (isResponse) {
                    Color.White
                } else {
                    Color.Black
                },
                modifier = Modifier
                    .padding(2.dp)
            )
        }
    }
}

@Composable
fun ConversationPipelinesView(
    pipelines: List<AssistPipelineResponse>,
    onSelectPipeline: (String) -> Unit
) {
    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(stringResource(R.string.assist_change_pipeline))
            }
            items(items = pipelines, key = { it.id }) {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(it.name) },
                    onClick = { onSelectPipeline(it.id) },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

@Preview(device = Devices.WEAR_OS_SMALL_ROUND)
@Composable
fun PreviewSpeechBubble() {
    ScalingLazyColumn(horizontalAlignment = Alignment.Start) {
        item {
            SpeechBubble(text = "Speech", isResponse = false)
        }
        item {
            SpeechBubble(text = "Response", isResponse = true)
        }
    }
}