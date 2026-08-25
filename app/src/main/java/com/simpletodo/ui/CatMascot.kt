package com.simpletodo.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simpletodo.R

/** The four poses of the SimpleTodo cat. */
enum class CatPose(@param:DrawableRes internal val drawable: Int) {
    /** Sitting, front on. Patient — for "you have not started yet". */
    Sit(R.drawable.cat_sit),

    /** Front down, rear up, about to spring. For "there is nothing here yet". */
    Pounce(R.drawable.cat_pounce),

    /** Walking in profile. A small brand mark for dialogs. */
    Walk(R.drawable.cat_walk),

    /** Mid-leap, paw out. Celebration — for "everything is done". */
    Jump(R.drawable.cat_jump),
}

/**
 * The mascot on its spotlight disc.
 *
 * The artwork is a black cat, so it needs a light backdrop of its own: on the dark theme it would
 * otherwise disappear into the background entirely. The disc is warm rather than neutral so it
 * reads as a deliberate sticker in both themes instead of a stray light patch.
 */
@Composable
fun CatMascot(
    pose: CatPose,
    modifier: Modifier = Modifier,
    size: Dp = 136.dp,
    halo: Boolean = true,
) {
    val onDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val haloColor = if (onDark) Color(0xFFDCD7CA) else Color(0xFFF2EFE5)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (halo) {
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(haloColor),
            )
        }
        Image(
            painter = painterResource(pose.drawable),
            contentDescription = null,
            modifier = Modifier.size(size * 0.72f),
            contentScale = ContentScale.Fit,
        )
    }
}
