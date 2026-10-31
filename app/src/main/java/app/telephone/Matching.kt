package app.telephone

import android.content.Context
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Tranche de temps des appels récents. */
enum class Period(private val fr: String, private val en: String) {
    TODAY("Aujourd'hui", "Today"),
    YESTERDAY("Hier", "Yesterday"),
    WEEK("Cette semaine", "This week"),
    OLDER("Antérieur", "Older"),
    ;

    val label: String get() = tr(fr, en)

    companion object {
        /** La semaine commence le lundi. */
        fun of(millis: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Period {
            val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            return when {
                !day.isBefore(today) -> TODAY
                day == today.minusDays(1) -> YESTERDAY
                !day.isBefore(today.with(DayOfWeek.MONDAY)) -> WEEK
                else -> OLDER
            }
        }
    }
}

object Dial {
    private val T9 = "22233344455566677778889999"

    /** Lettres du nom en chiffres du clavier (« Émilie » → 364543). */
    fun t9(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).lowercase().mapNotNull { c ->
            when (c) {
                in 'a'..'z' -> T9[c - 'a']
                in '0'..'9' -> c
                else -> null
            }
        }.joinToString("")

    /**
     * Le contact correspond si son numéro contient les chiffres tapés (06… trouve aussi +33 6…), ou si un mot de son
     * nom commence par eux au clavier (5 3 6 trouve « Jean »).
     */
    fun matches(contact: Contact, typed: String): Boolean {
        val digits = typed.filter { it.isDigit() }
        if (digits.isEmpty()) return false
        val number = contact.number.filter { it.isDigit() }
        if (number.contains(digits)) return true
        if (digits.length > 1 && digits.startsWith('0') && number.contains(digits.drop(1))) return true
        return contact.name.split(' ', '-').any { t9(it).startsWith(digits) }
    }
}

/** Jusqu'à 4 contacts favoris, gardés dans les préférences de l'appli (« identifiant|numéro »). */
object Favorites {
    const val MAX = 4
    private const val KEY = "favoris"

    private fun prefs(context: Context) = context.getSharedPreferences("omniphone", Context.MODE_PRIVATE)

    fun load(context: Context): List<String> = prefs(context).getString(KEY, "").orEmpty().split(';').filter { it.isNotEmpty() }

    fun save(context: Context, keys: List<String>) = prefs(context).edit().putString(KEY, keys.take(MAX).joinToString(";")).apply()

    fun key(contact: Contact) = "${contact.id}|${contact.number}"
}

/** Numéros abrégés : un appui long sur 2 à 9, clavier vide, appelle le numéro choisi. */
object SpeedDial {
    val DIGITS = '2'..'9'
    private const val KEY = "rapide"

    fun decode(text: String): Map<Char, String> = text.split(';').mapNotNull { entry ->
        val (digit, number) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        digit.singleOrNull()?.takeIf { it in DIGITS && number.isNotEmpty() }?.let { it to number }
    }.toMap()

    fun encode(slots: Map<Char, String>): String = slots.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }

    fun load(context: Context): Map<Char, String> =
        decode(context.getSharedPreferences("omniphone", Context.MODE_PRIVATE).getString(KEY, "").orEmpty())

    fun save(context: Context, slots: Map<Char, String>) =
        context.getSharedPreferences("omniphone", Context.MODE_PRIVATE).edit().putString(KEY, encode(slots)).apply()
}
