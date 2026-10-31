package app.telephone

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Miniatures déjà décodées : la liste redessine les lignes à chaque défilement. */
private val photos = LruCache<String, ImageBitmap>(300)

private fun decode(context: Context, uri: String): ImageBitmap? = photos.get(uri) ?: runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))) { decoder, _, _ ->
        decoder.setTargetSize(120, 120)
    }.asImageBitmap()
}.getOrNull()?.also { photos.put(uri, it) }

/**
 * Rond ([size], 40 dp par défaut) : la photo du contact, sinon son initiale. [badge] se pose en bas à droite, dans un petit rond détouré
 * de la couleur du fond.
 */
@Composable
fun Avatar(photo: String?, name: String?, size: Dp = 40.dp, badge: (@Composable () -> Unit)? = null, badgeColor: Color = MaterialTheme.colorScheme.secondaryContainer) {
    val context = LocalContext.current
    val image by produceState(photo?.let { photos.get(it) }, photo) {
        if (photo != null && value == null) value = withContext(Dispatchers.IO) { decode(context, photo) }
    }
    Box(Modifier.size(size)) {
        Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            val bitmap = image
            when {
                bitmap != null -> Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
                !name.isNullOrBlank() -> Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                else -> Text("#", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) { badge() }
        }
    }
}
