package com.simpletodo.quickadd

import android.app.Activity
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The entrance and exit of one of the sheets that open over the home screen.
 *
 * The sheet owns its own motion rather than leaving it to the window: the activity finishes only
 * once the exit has actually played, so closing is the same slide back down that opening was,
 * instead of the whole thing blinking out.
 */
@Stable
class SheetState internal constructor() {
    internal val transition = MutableTransitionState(false).apply { targetState = true }

    /** Plays the exit; the host's `onDismissed` runs when it has finished. */
    fun dismiss() {
        transition.targetState = false
    }

    val isDismissing: Boolean get() = !transition.targetState
}

@Composable
fun rememberSheetState(): SheetState = remember { SheetState() }

/**
 * A bottom sheet in the manner of iOS: it rises on a slightly under-damped spring while the page
 * behind dims, carries a grabber, and grows or shrinks smoothly as its content changes.
 */
@Composable
internal fun SheetHost(
    state: SheetState,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transition = state.transition
    BackHandler(enabled = !state.isDismissing) { state.dismiss() }
    LaunchedEffect(transition.isIdle, transition.currentState) {
        if (transition.isIdle && !transition.currentState && !transition.targetState) onDismissed()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visibleState = transition,
            enter = fadeIn(tween(durationMillis = 260)),
            exit = fadeOut(tween(durationMillis = 220)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = state::dismiss,
                    )
                    .semantics { contentDescription = "Close" },
            )
        }

        AnimatedVisibility(
            visibleState = transition,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(10.dp),
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                initialOffsetY = { it },
            ) + fadeIn(tween(durationMillis = 140)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 230, easing = FastOutLinearInEasing),
                targetOffsetY = { it },
            ) + fadeOut(tween(durationMillis = 170, delayMillis = 60)),
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    // Swallows taps so that touching the sheet itself never counts as "outside".
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 3.dp,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier.animateContentSize(
                        animationSpec = spring(
                            dampingRatio = 0.9f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                ) {
                    Grabber()
                    content()
                }
            }
        }
    }
}

@Composable
private fun Grabber() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)),
        )
    }
}

/**
 * Finishes without the window animation on top of the sheet's own. The theme already turns the
 * window animation off for these activities; this covers the close on every API level.
 */
internal fun Activity.finishWithoutTransition() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        finish()
    } else {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

/** The light tick that says "done" — a save, an add. */
internal fun View.confirmHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        },
    )
}

/** A ✓ that lands with a little overshoot, so "added" and "saved" read as a moment, not a label. */
@Composable
internal fun PoppingCheck(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val scale = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
    Icon(
        imageVector = Icons.Rounded.Check,
        contentDescription = null,
        tint = tint,
        modifier = modifier
            .size(18.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    )
}
