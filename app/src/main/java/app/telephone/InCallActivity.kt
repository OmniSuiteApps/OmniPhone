package app.telephone

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InCallActivity : ComponentActivity() {
    private val proximity by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "telephone:oreille")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        L10n.apply(resources.configuration.locales[0])
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        answerIfAsked(intent)
        setContent {
            TelephoneTheme {
                InCallScreen(onFinished = ::finish, onEar = ::setProximity)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        answerIfAsked(intent)
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }

    private fun answerIfAsked(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ANSWER, false) != true) return
        Ongoing.calls.value.firstOrNull { it.details.state == Call.STATE_RINGING }?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    private fun setProximity(on: Boolean) {
        if (on && !proximity.isHeld) proximity.acquire()
        if (!on && proximity.isHeld) proximity.release()
    }

    companion object {
        const val EXTRA_ANSWER = "decrocher"

        fun intent(context: Context) = Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private fun statusLabel(state: Int) = when (state) {
    Call.STATE_RINGING -> tr("Appel entrant", "Incoming call")
    Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW, Call.STATE_SELECT_PHONE_ACCOUNT -> tr("Appel…", "Calling…")
    Call.STATE_HOLDING -> tr("En attente", "On hold")
    Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> tr("Appel terminé", "Call ended")
    else -> tr("Appel en cours", "Ongoing call")
}

private fun CallEndpoint.icon(): ImageVector = when (endpointType) {
    CallEndpoint.TYPE_SPEAKER -> Icons.AutoMirrored.Outlined.VolumeUp
    CallEndpoint.TYPE_BLUETOOTH -> Icons.Outlined.Bluetooth
    CallEndpoint.TYPE_WIRED_HEADSET -> Icons.Outlined.Headset
    else -> Icons.Outlined.PhoneInTalk
}

@Composable
private fun InCallScreen(onFinished: () -> Unit, onEar: (Boolean) -> Unit) {
    Ongoing.calls.collectAsState().value
    Ongoing.changes.collectAsState().value
    val endpoint by Ongoing.endpoint.collectAsState()
    val endpoints by Ongoing.endpoints.collectAsState()
    val muted by Ongoing.muted.collectAsState()
    val call = Ongoing.current()
    val state = call?.details?.state ?: Call.STATE_DISCONNECTED
    LaunchedEffect(call == null) {
        if (call == null) {
            delay(800)
            onFinished()
        }
    }
    val onEarpiece = state == Call.STATE_ACTIVE && endpoint?.endpointType == CallEndpoint.TYPE_EARPIECE
    DisposableEffect(onEarpiece) {
        onEar(onEarpiece)
        onDispose { }
    }
    val connectedAt = call?.details?.connectTimeMillis ?: 0
    val elapsed by produceState(0L, connectedAt, state) {
        while (state == Call.STATE_ACTIVE && connectedAt > 0) {
            value = (System.currentTimeMillis() - connectedAt) / 1000
            delay(1000)
        }
    }
    var keypad by remember { mutableStateOf(false) }
    var tones by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        CardLabel(if (state == Call.STATE_ACTIVE && connectedAt > 0) formatDuration(elapsed) else statusLabel(state))
        Spacer(Modifier.height(16.dp))
        Text(
            if (keypad && tones.isNotEmpty()) tones else call?.title().orEmpty(),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.StartEllipsis,
        )
        val number = call?.details?.handle?.schemeSpecificPart
        if (!keypad && call?.details?.contactDisplayName != null && !number.isNullOrBlank()) {
            Text(Phone.format(number), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        if (call == null) return@Column
        if (state == Call.STATE_RINGING) {
            Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundButton(MaterialTheme.colorScheme.error, onClick = { call.hangUp() }, size = 80.dp) {
                    Icon(Icons.Filled.CallEnd, tr("Refuser", "Decline"), tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(36.dp))
                }
                RoundButton(MaterialTheme.colorScheme.primary, onClick = { call.answer(VideoProfile.STATE_AUDIO_ONLY) }, size = 80.dp) {
                    Icon(Icons.Filled.Call, tr("Décrocher", "Answer"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                }
            }
            return@Column
        }
        if (keypad) {
            Keypad(onKey = { digit ->
                tones += digit
                call.playDtmfTone(digit)
                scope.launch { delay(150); call.stopDtmfTone() }
            })
        } else {
            Row(
                Modifier.fillMaxWidth().clip(CardShape).background(cardBackground()).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Toggle(if (muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, tr("Muet", "Mute"), muted) { Ongoing.service?.setMuted(!muted) }
                val current = endpoint
                if (current != null && endpoints.size > 1) {
                    // Chaque appui passe à la sortie suivante : écouteur, haut-parleur, Bluetooth…
                    Toggle(current.icon(), current.endpointName.toString(), current.endpointType == CallEndpoint.TYPE_SPEAKER) {
                        val next = endpoints[(endpoints.indexOfFirst { it.identifier == current.identifier } + 1) % endpoints.size]
                        Ongoing.service?.requestCallEndpointChange(next, Runnable::run) { }
                    }
                }
                Toggle(Icons.Outlined.Dialpad, tr("Clavier", "Keypad"), false) { keypad = true }
                if (call.details.can(Call.Details.CAPABILITY_HOLD)) {
                    val held = state == Call.STATE_HOLDING
                    Toggle(Icons.Outlined.Pause, tr("Attente", "Hold"), held) { if (held) call.unhold() else call.hold() }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            if (keypad) Text(tr("Masquer", "Hide"), Modifier.clip(RoundedCornerShape(16.dp)).clickable { keypad = false }.padding(16.dp), color = MaterialTheme.colorScheme.primary)
            RoundButton(MaterialTheme.colorScheme.error, onClick = { call.hangUp() }) {
                Icon(Icons.Filled.CallEnd, tr("Raccrocher", "Hang up"), tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.size(width = 72.dp, height = 18.dp), textAlign = TextAlign.Center)
    }
}
