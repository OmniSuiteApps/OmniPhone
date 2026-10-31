package app.telephone

import android.Manifest
import android.provider.CallLog
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

private enum class Tab(private val fr: String, private val en: String, val icon: ImageVector) {
    RECENTS("Récents", "Recents", Icons.Outlined.History),
    CONTACTS("Contacts", "Contacts", Icons.Outlined.Person),
    KEYPAD("Clavier", "Keypad", Icons.Outlined.Dialpad),
    ;

    val label: String get() = tr(fr, en)
}

@Composable
fun MainScreen(
    refresh: Int,
    dialRequest: DialRequest?,
    isDefault: Boolean,
    onRequestRole: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.RECENTS) }
    var number by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(dialRequest) {
        dialRequest?.let { number = it.number; tab = Tab.KEYPAD }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.weight(1f).statusBarsPadding()) {
            if (!isDefault) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth().clip(CardShape).background(cardBackground()).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr("Téléphone n'est pas l'appli d'appel par défaut", "Phone is not the default calling app"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRequestRole) { Text(tr("Choisir", "Set")) }
                }
            }
            when (tab) {
                Tab.RECENTS -> Recents(refresh, onRequestPermissions)
                Tab.CONTACTS -> Contacts(refresh, onRequestPermissions)
                Tab.KEYPAD -> Dialer(number, onNumber = { number = it })
            }
        }
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Tab.entries.forEach {
                NavigationBarItem(
                    selected = tab == it,
                    onClick = { tab = it },
                    icon = { Icon(it.icon, contentDescription = null) },
                    label = { Text(it.label) },
                )
            }
        }
    }
}

@Composable
private fun PageTitle(text: String) = CardLabel(text, Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp))

@Composable
private fun MissingPermission(text: String, onRequest: () -> Unit) {
    Column(Modifier.padding(8.dp)) {
        HintText(text)
        TextButton(onClick = onRequest, modifier = Modifier.padding(horizontal = 8.dp)) { Text(tr("Autoriser", "Allow")) }
    }
}

/** Ligne de liste de Lanceur : coins arrondis, titre en titleMedium, sous-titre discret. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonRow(title: String, subtitle: String, onClick: () -> Unit, titleColor: Color = MaterialTheme.colorScheme.onSurface, leading: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            leading()
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Recents(refresh: Int, onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    val calls by produceState<List<RecentCall>?>(null, refresh) { value = withContext(Dispatchers.IO) { Phone.recents(context) } }
    PageTitle(tr("Récents", "Recents"))
    if (!Phone.granted(context, Manifest.permission.READ_CALL_LOG)) {
        return MissingPermission(tr("Autorisez l'accès au journal d'appels pour voir vos appels récents.", "Allow call log access to see your recent calls."), onRequestPermissions)
    }
    val list = calls ?: return
    if (list.isEmpty()) return HintText(tr("Aucun appel", "No calls"))
    LazyColumn {
        items(list, key = { it.id }) { call ->
            val missed = call.type == CallLog.Calls.MISSED_TYPE || call.type == CallLog.Calls.REJECTED_TYPE
            val (icon, kind) = when (call.type) {
                CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade to tr("Sortant", "Outgoing")
                CallLog.Calls.MISSED_TYPE -> Icons.AutoMirrored.Filled.CallMissed to tr("Manqué", "Missed")
                CallLog.Calls.REJECTED_TYPE -> Icons.AutoMirrored.Filled.CallMissed to tr("Refusé", "Declined")
                else -> Icons.AutoMirrored.Filled.CallReceived to tr("Entrant", "Incoming")
            }
            val number = if (call.number.isBlank()) tr("Numéro masqué", "Private number") else Phone.format(call.number)
            val time = DateUtils.getRelativeTimeSpanString(call.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            val duration = if (call.duration > 0) " · ${formatDuration(call.duration)}" else ""
            PersonRow(
                title = call.name ?: number,
                subtitle = "$kind · $time$duration",
                titleColor = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                onClick = { Phone.call(context, call.number) },
            ) {
                Icon(icon, contentDescription = kind, tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun searchKey(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

@Composable
private fun Contacts(refresh: Int, onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    val contacts by produceState(emptyList<Contact>(), refresh) { value = withContext(Dispatchers.IO) { Phone.contacts(context) } }
    var query by rememberSaveable { mutableStateOf("") }
    PageTitle(tr("Contacts", "Contacts"))
    if (!Phone.granted(context, Manifest.permission.READ_CONTACTS)) {
        return MissingPermission(tr("Autorisez l'accès aux contacts pour les afficher ici.", "Allow contacts access to show them here."), onRequestPermissions)
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text(tr("Rechercher", "Search")) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        singleLine = true,
        shape = CardShape,
    )
    Spacer(Modifier.height(8.dp))
    val key = searchKey(query.trim())
    val digits = query.filter { it.isDigit() }
    val shown = remember(contacts, key) {
        if (key.isEmpty()) contacts
        else contacts.filter { searchKey(it.name).contains(key) || (digits.isNotEmpty() && it.number.filter(Char::isDigit).contains(digits)) }
    }
    LazyColumn {
        items(shown, key = { "${it.id}-${it.number}" }) { contact ->
            PersonRow(contact.name, "${contact.label} · ${Phone.format(contact.number)}", onClick = { Phone.call(context, contact.number) }) {
                Text(contact.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dialer(number: String, onNumber: (String) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Phone.format(number),
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
            if (number.isNotEmpty()) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).combinedClickable(onClick = { onNumber(number.dropLast(1)) }, onLongClick = { onNumber("") }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = tr("Effacer", "Delete"))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Keypad(
            onKey = { onNumber(number + it) },
            onLongPress = {
                when (it) {
                    '0' -> onNumber("$number+")
                    '1' -> if (number.isEmpty()) Phone.voicemail(context) else onNumber(number + it)
                    else -> onNumber(number + it)
                }
            },
        )
        Spacer(Modifier.height(24.dp))
        RoundButton(MaterialTheme.colorScheme.primary, onClick = { Phone.call(context, number) }) {
            Icon(Icons.Filled.Call, contentDescription = tr("Appeler", "Call"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
        }
    }
}
