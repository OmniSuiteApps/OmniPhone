package app.telephone

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp

/** Alphabet de Lanceur : lettres, vague sous le doigt et lettre non vide la plus proche. */
object Alphabet {
    /** A à Z, puis `#` pour tout ce qui ne commence pas par une lettre latine. */
    val LETTERS: List<Char> = ('A'..'Z').toList() + '#'

    private const val SIGMA = 1.5f
    const val MAX_EXTRA_SCALE = 1.2f
    const val MAX_SHIFT_DP = 28f

    fun letterOf(name: String): Char {
        val first = Normalizer.normalize(name.trim(), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: return '#'
        return if (first in 'A'..'Z') first else '#'
    }

    fun wave(distance: Float): Float = exp(-(distance * distance) / (2f * SIGMA * SIGMA))

    /** Lettre visée par le doigt à [y] sur une barre haute de [height] : la plus proche qui a des contacts, ou -1. */
    fun indexAt(y: Float, height: Float, enabled: List<Boolean>): Int {
        if (height <= 0f || enabled.none { it }) return -1
        val start = (y * enabled.size / height).toInt().coerceIn(0, enabled.lastIndex)
        for (d in 0..enabled.size) {
            if (start - d >= 0 && enabled[start - d]) return start - d
            if (start + d <= enabled.lastIndex && enabled[start + d]) return start + d
        }
        return -1
    }
}

/**
 * Colonne de lettres de même hauteur, à droite de la liste. Glisser le doigt dessus appelle [onLetter] à chaque
 * nouvelle lettre (avec un petit tic) ; [onFinger] donne la lettre et la position du doigt pour la bulle, null au lever.
 */
@Composable
fun AlphabetBar(enabled: List<Boolean>, onLetter: (Int) -> Unit, onFinger: (Pair<Int, Float>?) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val shiftPx = with(LocalDensity.current) { Alphabet.MAX_SHIFT_DP.dp.toPx() }
    var active by remember { mutableFloatStateOf(-1f) }
    var fingerUnits by remember { mutableFloatStateOf(0f) }
    val intensity by animateFloatAsState(
        targetValue = if (active >= 0) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "vague",
    )
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnLetter by rememberUpdatedState(onLetter)
    val currentOnFinger by rememberUpdatedState(onFinger)
    val colors = MaterialTheme.colorScheme

    Column(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                val height = size.height.toFloat()
                var y = down.position.y
                while (true) {
                    val index = Alphabet.indexAt(y, height, currentEnabled)
                    fingerUnits = (y * currentEnabled.size / height).coerceIn(0f, currentEnabled.size.toFloat()) - 0.5f
                    if (index >= 0 && index != active.toInt()) {
                        view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                        currentOnLetter(index)
                    }
                    active = index.toFloat()
                    if (index >= 0) currentOnFinger(index to y)
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                    y = change.position.y
                }
                active = -1f
                currentOnFinger(null)
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Alphabet.LETTERS.forEachIndexed { i, letter ->
            val on = i == active.toInt()
            Text(
                letter.toString(),
                color = when {
                    on -> colors.primary
                    !enabled.getOrElse(i) { false } -> colors.onSurface.copy(alpha = 0.3f)
                    else -> colors.onSurface.copy(alpha = 0.85f)
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (on) FontWeight.Bold else null,
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
                    .graphicsLayer {
                        val f = Alphabet.wave(abs(i - fingerUnits)) * intensity
                        val s = 1f + Alphabet.MAX_EXTRA_SCALE * f
                        scaleX = s
                        scaleY = s
                        // Les lettres proches du doigt s'écartent vers la liste
                        translationX = -shiftPx * f
                    },
            )
        }
    }
}
