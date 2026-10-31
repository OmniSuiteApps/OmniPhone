package app.telephone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.telecom.TelecomManager

/**
 * Appli Téléphone par défaut, c'est à elle de signaler les appels manqués : Android ne montre plus les siens et lui
 * envoie [TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION] (nombre d'appels manqués, et le numéro s'il n'y en a qu'un).
 */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
        MissedCalls.show(context, count, number)
    }
}

/** « Rappeler » depuis la notification : appelle et retire la notification. */
class CallBackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
        MissedCalls.clear(context)
        Phone.call(context, number)
    }

    companion object {
        const val EXTRA_NUMBER = "numero"
    }
}

object MissedCalls {
    private const val CHANNEL = "manques"
    private const val NOTIFICATION = 2

    fun show(context: Context, count: Int, number: String?) {
        L10n.apply(context.resources.configuration.locales[0])
        val nm = context.getSystemService(NotificationManager::class.java)
        if (count <= 0) return nm.cancel(NOTIFICATION)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Appels manqués", "Missed calls"), NotificationManager.IMPORTANCE_DEFAULT))
        val found = number?.let { Phone.lookup(context, it) }
        val who = found?.name ?: number?.takeIf { it.isNotBlank() }?.let(Phone::format) ?: tr("Numéro masqué", "Private number")
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(if (count == 1) tr("Appel manqué", "Missed call") else tr("$count appels manqués", "$count missed calls"))
            .setContentText(if (count == 1) who else tr("Dernier : $who", "Last: $who"))
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setNumber(count)
        // Un seul appel et un numéro connu : on peut rappeler ou écrire directement
        if (count == 1 && !number.isNullOrBlank()) {
            val callBack = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, CallBackReceiver::class.java).putExtra(CallBackReceiver.EXTRA_NUMBER, number),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val sms = PendingIntent.getActivity(
                context, 2,
                Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_call), tr("Rappeler", "Call back"), callBack).build())
            builder.addAction(Notification.Action.Builder(null, tr("SMS", "Message"), sms).build())
        }
        nm.notify(NOTIFICATION, builder.build())
    }

    /** Appels vus : Android les marque comme lus dans le journal et renvoie un nombre nul. */
    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
        runCatching { context.getSystemService(TelecomManager::class.java).cancelMissedCallsNotification() }
    }
}
