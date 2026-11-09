package ir.rokoi.rmho.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Simple app-wide UI settings, persisted by AppRoot via SharedPreferences. */
object UiPrefs {
    var showFa by mutableStateOf(true)
}

private val GoldA = Color(0xFFFFD54F)
private val GoldB = Color(0xFFFF8F00)
private val GoldRing = Color(0xFFFFC400)
private val RedA = Color(0xFFE05252)
private val RedB = Color(0xFF7A1020)
private val RedRing = Color(0xFFFF5252)
private val Navy = Color(0xFF0A1730)

/**
 * Circular portrait on a rich gold gradient with a gold ring —
 * the Adventurer treatment. Falls back to the name's initial.
 */
@Composable
fun GoldPortrait(imageUrl: String, name: String, size: Dp, glow: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size)
            .then(if (glow) Modifier.shadow(14.dp, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(GoldA, GoldB)))
            .border(3.dp, GoldRing, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNotEmpty()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                name.take(1),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
        }
    }
}

/**
 * Circular portrait on a deep red gradient with a red ring —
 * the Monster treatment. Falls back to the name's initial.
 */
@Composable
fun RedPortrait(imageUrl: String, name: String, size: Dp, glow: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size)
            .then(if (glow) Modifier.shadow(14.dp, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(RedA, RedB)))
            .border(3.dp, RedRing, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNotEmpty()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                name.take(1),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
