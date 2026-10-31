package app.telephone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.CallEndpoint
import android.os.VibrationEffect
import android.os.VibratorManager
import android.telecom.InCallService
import kotlinx.coroutines.flow.MutableStateFlow

/** Appels en cours, partagés entre le service (Android) et l'écran d'appel. */
object Ongoing {
    val calls = MutableStateFlow<List<Call>>(emptyList())

    /** Incrémenté à chaque changement d'état d'un appel, pour redessiner l'écran. */
    val changes = MutableStateFlow(0)
    val endpoint = MutableStateFlow<CallEndpoint?>(null)
    val endpoints = MutableStateFlow<List<CallEndpoint>>(emptyList())
    val muted = MutableStateFlow(false)
    var service: CallService? = null

    /** Appels à montrer : une conférence compte pour un seul appel, sans ses participants. */
    fun shown(): List<Call> = calls.value.filter { it.parent == null }

    /** Un appel qui sonne passe devant ; sinon celui qui n'est pas en attente ; sinon le plus récent. */
    fun current(): Call? = shown().let { list ->
        list.firstOrNull { it.details.state == Call.STATE_RINGING } ?: list.lastOrNull { it.details.state != Call.STATE_HOLDING } ?: list.lastOrNull()
    }

    /** Les autres appels (en attente) pendant [current]. */
    fun others(): List<Call> = current().let { c -> shown().filter { it != c } }
}

fun Call.title(): String =
    if (details.hasProperty(Call.Details.PROPERTY_CONFERENCE)) tr("Conférence", "Conference") else details.contactDisplayName?.takeIf { it.isNotBlank() }
        ?: details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }?.let(Phone::format)
        ?: tr("Numéro masqué", "Private number")

fun Call.hangUp() = if (details.state == Call.STATE_RINGING) reject(false, null) else disconnect()

class CallService : InCallService() {
    /** Appels sortants déjà décrochés : on ne vibre qu'une fois, pas à chaque reprise après une attente. */
    private val answered = mutableSetOf<Call>()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_ACTIVE && call.details.callDirection == Call.Details.DIRECTION_OUTGOING && answered.add(call)) {
                // Le téléphone est souvent à l'oreille : une courte vibration dit que le correspondant a décroché
                getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            changed()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) = changed()
        override fun onCannedTextResponsesLoaded(call: Call, cannedTextResponses: List<String>) = changed()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) = changed()
    }

    override fun onCreate() {
        super.onCreate()
        L10n.apply(resources.configuration.locales[0])
        Ongoing.service = this
        val nm = getSystemService(NotificationManager::class.java)
        // Pas de son : c'est Android qui fait sonner le téléphone
        nm.createNotificationChannel(NotificationChannel(CHANNEL_INCOMING, tr("Appels entrants", "Incoming calls"), NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, tr("Appel en cours", "Ongoing call"), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onDestroy() {
        Ongoing.service = null
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        Ongoing.calls.value += call
        // Un appel entrant passe par la notification plein écran ; un appel sortant ouvre l'écran tout de suite
        if (call.details.state != Call.STATE_RINGING) startActivity(InCallActivity.intent(this))
        changed()
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        answered -= call
        Ongoing.calls.value -= call
        changed()
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) { Ongoing.endpoint.value = callEndpoint }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) { Ongoing.endpoints.value = availableEndpoints }

    override fun onMuteStateChanged(isMuted: Boolean) { Ongoing.muted.value = isMuted }

    private fun changed() {
        Ongoing.changes.value++
        val nm = getSystemService(NotificationManager::class.java)
        val call = Ongoing.current() ?: return nm.cancel(NOTIFICATION)
        val open = PendingIntent.getActivity(this, 0, InCallActivity.intent(this), PendingIntent.FLAG_IMMUTABLE)
        val hangUp = PendingIntent.getBroadcast(this, 1, Intent(this, HangupReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        val ringing = call.details.state == Call.STATE_RINGING
        val builder = if (ringing) {
            // Décrocher ouvre l'écran d'appel : Android interdit d'ouvrir une activité depuis un récepteur de notification
            val answer = PendingIntent.getActivity(this, 2, InCallActivity.intent(this).putExtra(InCallActivity.EXTRA_ANSWER, true), PendingIntent.FLAG_IMMUTABLE)
            Notification.Builder(this, CHANNEL_INCOMING)
                .setStyle(Notification.CallStyle.forIncomingCall(Person.Builder().setName(call.title()).setImportant(true).build(), hangUp, answer))
                .setFullScreenIntent(open, true)
        } else {
            // Notification simple : CallStyle exige un service de premier plan hors appel entrant
            Notification.Builder(this, CHANNEL_ONGOING)
                .setContentTitle(call.title())
                .setContentText(tr("Appel en cours", "Ongoing call"))
                .setUsesChronometer(call.details.connectTimeMillis > 0)
                .setWhen(call.details.connectTimeMillis)
                .addAction(Notification.Action.Builder(null, tr("Raccrocher", "Hang up"), hangUp).build())
        }
        nm.notify(
            NOTIFICATION,
            builder.setSmallIcon(R.drawable.ic_call).setContentIntent(open).setOngoing(true).setCategory(Notification.CATEGORY_CALL).build(),
        )
    }

    companion object {
        private const val CHANNEL_INCOMING = "entrants"
        private const val CHANNEL_ONGOING = "en-cours"
        private const val NOTIFICATION = 1
    }
}

class HangupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Ongoing.current()?.hangUp()
    }
}
