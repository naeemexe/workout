package work.lockedinlabs.tracker.ui.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.lockedinlabs.tracker.R
import work.lockedinlabs.tracker.data.Profile

/** Built-in avatars. The list index is what's stored in [Profile.avatarId], so only append — never reorder. */
data class AvatarStyle(val label: String, @param:DrawableRes val icon: Int, val color: Color)

val Avatars = listOf(
    AvatarStyle("Dumbbell", R.drawable.avatar_dumbbell, Color(0xFF2EE59D)),
    AvatarStyle("Bolt", R.drawable.avatar_bolt, Color(0xFFFFC53D)),
    AvatarStyle("Flame", R.drawable.avatar_flame, Color(0xFFFF7A45)),
    AvatarStyle("Mountain", R.drawable.avatar_mountain, Color(0xFF4DA3FF)),
    AvatarStyle("Star", R.drawable.avatar_star, Color(0xFFB37FEB)),
)

/** Circular avatar: chosen icon, else initials, else a person icon. */
@Composable
fun ProfileAvatar(profile: Profile?, size: Dp, modifier: Modifier = Modifier) {
    val style = profile?.avatarId?.let { Avatars.getOrNull(it) }
    val initials = profile?.initials.orEmpty()
    when {
        style != null -> AvatarIcon(style, size, modifier)
        else -> {
            val primary = MaterialTheme.colorScheme.primary
            AvatarCircle(primary, size, modifier) {
                if (initials.isNotEmpty()) {
                    Text(initials, color = primary, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
                } else {
                    Icon(Icons.Filled.Person, contentDescription = "Profile", tint = primary, modifier = Modifier.size(size * 0.55f))
                }
            }
        }
    }
}

@Composable
fun AvatarIcon(style: AvatarStyle, size: Dp, modifier: Modifier = Modifier) {
    AvatarCircle(style.color, size, modifier) {
        Icon(painterResource(style.icon), contentDescription = style.label, tint = style.color, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun AvatarCircle(color: Color, size: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(1.5.dp, color.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}
