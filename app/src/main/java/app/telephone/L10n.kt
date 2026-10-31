package app.telephone

import java.util.Locale

/** Anglais si Android est en anglais, sinon français ; les textes vivent en paire dans le code, comme dans Lanceur. */
object L10n {
    @Volatile var english = false

    fun apply(locale: Locale?) { english = locale?.language == "en" }
}

fun tr(fr: String, en: String): String = if (L10n.english) en else fr
