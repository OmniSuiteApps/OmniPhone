package app.telephone

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.widget.Toast
import java.util.Locale

data class RecentCall(val id: Long, val number: String, val name: String?, val type: Int, val date: Long, val duration: Long, val photo: String? = null)

data class Contact(val id: Long, val name: String, val number: String, val label: String, val photo: String?)

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

object Phone {
    fun granted(context: Context, permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun format(number: String): String = PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number

    fun recents(context: Context): List<RecentCall> {
        if (!granted(context, Manifest.permission.READ_CALL_LOG)) return emptyList()
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${CallLog.Calls.DATE} DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, 300)
        }
        val projection = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION,
        )
        return context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, args, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(RecentCall(c.getLong(0), c.getString(1).orEmpty(), c.getString(2)?.takeIf { it.isNotBlank() }, c.getInt(3), c.getLong(4), c.getLong(5)))
                }
            }
        }.orEmpty().let { calls ->
            // Nom et photo relus dans les contacts : le cache du journal n'est rempli que par Google Téléphone
            val found = calls.map { it.number }.filter { it.isNotBlank() }.distinct().associateWith { lookup(context, it) }
            calls.map { call -> found[call.number]?.let { (name, photo) -> call.copy(name = call.name ?: name, photo = photo) } ?: call }
        }
    }

    private fun lookup(context: Context, number: String): Pair<String?, String?>? {
        if (!granted(context, Manifest.permission.READ_CONTACTS)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getString(1) else null
        }
    }

    fun contacts(context: Context): List<Contact> {
        if (!granted(context, Manifest.permission.READ_CONTACTS)) return emptyList()
        val p = CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            CommonDataKinds.Phone.CONTACT_ID, CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            CommonDataKinds.Phone.NUMBER, CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.LABEL,
            CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
        )
        return context.contentResolver.query(p, projection, null, null, "${CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC")?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    // Type « personnalisé » sans nom : rien plutôt que « Custom »
                    val custom = c.getInt(3) == CommonDataKinds.Phone.TYPE_CUSTOM && c.getString(4).isNullOrBlank()
                    val label = if (custom) "" else CommonDataKinds.Phone.getTypeLabel(context.resources, c.getInt(3), c.getString(4)).toString()
                    add(Contact(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), label, c.getString(5)))
                }
            }
        }.orEmpty()
            .distinctBy { it.id to PhoneNumberUtils.normalizeNumber(it.number) }
            // Ce qui ne commence pas par une lettre va à la fin, sous le « # » de l'alphabet
            .sortedBy { Alphabet.letterOf(it.name) == '#' }
    }

    fun call(context: Context, number: String) = place(context, Uri.fromParts("tel", number, null), number.isNotBlank())

    fun voicemail(context: Context) = place(context, Uri.fromParts("voicemail", "", null), true)

    private fun place(context: Context, uri: Uri, valid: Boolean) {
        if (!valid) return
        if (!granted(context, Manifest.permission.CALL_PHONE)) {
            Toast.makeText(context, tr("Autorisez les appels pour Téléphone", "Allow Phone to make calls"), Toast.LENGTH_SHORT).show()
            return
        }
        context.getSystemService(TelecomManager::class.java).placeCall(uri, Bundle())
    }
}
