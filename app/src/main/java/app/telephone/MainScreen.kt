package app.telephone

import android.Manifest
import android.provider.CallLog
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import java.time.LocalDate
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import kotlin.math.roundToInt

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
    val context = LocalContext.current
    val contacts by produceState(emptyList<Contact>(), refresh) { value = withContext(Dispatchers.IO) { Phone.contacts(context) } }
    var favorites by remember { mutableStateOf(Favorites.load(context)) }
    val onFavorites = { keys: List<String> -> favorites = keys; Favorites.save(context, keys) }
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
                Tab.RECENTS -> Recents(refresh, contacts, favorites, onFavorites, onRequestPermissions)
                Tab.CONTACTS -> Contacts(contacts, onRequestPermissions)
                Tab.KEYPAD -> Dialer(number, contacts, onNumber = { number = it })
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
        leading()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Recents(
    refresh: Int,
    contacts: List<Contact>,
    favorites: List<String>,
    onFavorites: (List<String>) -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val calls by produceState<List<RecentCall>?>(null, refresh) { value = withContext(Dispatchers.IO) { Phone.recents(context) } }
    val sections = remember(calls) {
        val today = LocalDate.now()
        calls.orEmpty().groupBy { Period.of(it.date, today) }.toSortedMap()
    }
    LazyColumn {
        if (contacts.isNotEmpty()) {
            item(key = "favoris") { FavoritesCard(contacts, favorites, onFavorites) }
        }
        if (!Phone.granted(context, Manifest.permission.READ_CALL_LOG)) {
            item { MissingPermission(tr("Autorisez l'accès au journal d'appels pour voir vos appels récents.", "Allow call log access to see your recent calls."), onRequestPermissions) }
        } else if (calls?.isEmpty() == true) {
            item { HintText(tr("Aucun appel", "No calls"), Modifier.padding(top = 16.dp)) }
        }
        sections.forEach { (period, list) ->
            item(key = period.name) { PageTitle(period.label) }
            items(list, key = { it.id }) { call ->
                RecentRow(call)
            }
        }
    }
}

@Composable
private fun RecentRow(call: RecentCall) {
    val context = LocalContext.current
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
        Avatar(
            photo = call.photo,
            name = call.name,
            badgeColor = if (missed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            badge = {
                Icon(icon, contentDescription = kind, tint = if (missed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(12.dp))
            },
        )
    }
}

/** Carte des favoris : jusqu'à 4 contacts à appeler d'un geste, et un « + » tant qu'il reste de la place. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesCard(contacts: List<Contact>, favorites: List<String>, onFavorites: (List<String>) -> Unit) {
    val context = LocalContext.current
    val byKey = remember(contacts) { contacts.associateBy(Favorites::key) }
    // Un favori dont le contact a été supprimé disparaît simplement
    val shown = favorites.mapNotNull { byKey[it] }
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth().clip(CardShape).background(cardBackground()).padding(16.dp)) {
        CardLabel(tr("Favoris", "Favorites"))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            shown.forEach { contact ->
                var menu by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(onClick = { Phone.call(context, contact.number) }, onLongClick = { menu = true })
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Avatar(contact.photo, contact.name, size = 56.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(contact.name.substringBefore(' '), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(tr("Retirer des favoris", "Remove from favorites")) },
                            onClick = { menu = false; onFavorites(favorites - Favorites.key(contact)) },
                        )
                    }
                }
            }
            if (shown.size < Favorites.MAX) {
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { picking = true }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Ajouter", "Add"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Places vides : les favoris gardent la même largeur quel que soit leur nombre
                repeat(Favorites.MAX - shown.size - 1) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }) {
            var query by remember { mutableStateOf("") }
            val key = searchKey(query.trim())
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(tr("Choisir un favori", "Choose a favorite")) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = CardShape,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(contacts.filter { Favorites.key(it) !in favorites && searchKey(it.name).contains(key) }, key = Favorites::key) { contact ->
                    PersonRow(contact.name, contactSubtitle(contact), onClick = { picking = false; onFavorites(favorites + Favorites.key(contact)) }) {
                        Avatar(contact.photo, contact.name)
                    }
                }
            }
        }
    }
}

private fun contactSubtitle(contact: Contact) = listOf(contact.label, Phone.format(contact.number)).filter { it.isNotEmpty() }.joinToString(" · ")

private fun searchKey(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

@Composable
private fun Contacts(contacts: List<Contact>, onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Première ligne de chaque lettre ; les lettres sans contact restent grisées dans l'alphabet
    val firstRow = remember(shown) {
        buildMap { shown.forEachIndexed { i, contact -> putIfAbsent(Alphabet.letterOf(contact.name), i) } }
    }
    var finger by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), state = listState) {
                items(shown, key = { "${it.id}-${it.number}" }) { contact ->
                    PersonRow(contact.name, contactSubtitle(contact), onClick = { Phone.call(context, contact.number) }) {
                        Avatar(contact.photo, contact.name)
                    }
                }
            }
            AlphabetBar(
                enabled = Alphabet.LETTERS.map { it in firstRow },
                onLetter = { index -> firstRow[Alphabet.LETTERS[index]]?.let { scope.launch { listState.scrollToItem(it) } } },
                onFinger = { finger = it },
                modifier = Modifier.width(32.dp).fillMaxHeight().padding(vertical = 8.dp),
            )
        }
        finger?.let { (index, y) ->
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(x = -72.dp.roundToPx(), y = (y - 40.dp.toPx()).roundToInt().coerceAtLeast(0)) }
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(Alphabet.LETTERS[index].toString(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dialer(number: String, contacts: List<Contact>, onNumber: (String) -> Unit) {
    val context = LocalContext.current
    val results = remember(number, contacts) { contacts.filter { Dial.matches(it, number) } }
    Column(Modifier.fillMaxSize().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Les meilleurs résultats au plus près du numéro, juste au-dessus
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true) {
            items(results, key = Favorites::key) { contact ->
                PersonRow(contact.name, contactSubtitle(contact), onClick = { Phone.call(context, contact.number) }) {
                    Avatar(contact.photo, contact.name)
                }
            }
        }
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
