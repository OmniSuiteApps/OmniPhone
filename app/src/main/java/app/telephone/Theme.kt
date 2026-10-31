package app.telephone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Material You, comme Lanceur : les couleurs suivent le fond d'écran, sans que celui-ci soit affiché. */
@Composable
fun TelephoneTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme) {
        // Les écrans n'utilisent pas de Surface : sans cela, textes et icônes seraient noirs par défaut
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
    }
}

/** Forme et fond des cartes de Lanceur, ici opaques puisque rien n'apparaît derrière. */
val CardShape = RoundedCornerShape(28.dp)

@Composable
fun cardBackground(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

/** Petit titre de carte de Lanceur : majuscules espacées, couleur primaire. */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text.uppercase(Locale.FRENCH),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
        maxLines = 1,
    )
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Bouton rond plein (appeler, raccrocher, décrocher). */
@Composable
fun RoundButton(color: Color, onClick: () -> Unit, size: Dp = 72.dp, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val KEYS = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to "",
)

/** Clavier à 12 touches ; un appui long sur 0 donne +, sur 1 la messagerie (si [onLongPress] le gère). */
@Composable
fun Keypad(onKey: (Char) -> Unit, onLongPress: (Char) -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        KEYS.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { (digit, letters) ->
                    Column(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(cardBackground())
                            .combinedClickable(onClick = { onKey(digit[0]) }, onLongClick = { onLongPress(digit[0]) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(digit, style = MaterialTheme.typography.headlineMedium)
                        if (letters.isNotEmpty()) {
                            Text(letters, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
