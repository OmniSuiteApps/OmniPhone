package app.telephone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MatchingTest {
    private val jean = Contact(1, "Jean-Éric Dupont", "+33 6 12 34 56 78", "", null)

    @Test
    fun dialMatches() {
        assertTrue(Dial.matches(jean, "0612")) // 06 retrouve +33 6
        assertTrue(Dial.matches(jean, "3456"))
        assertTrue(Dial.matches(jean, "5326")) // « Jean »
        assertTrue(Dial.matches(jean, "374")) // « Éric »
        assertFalse(Dial.matches(jean, "999"))
        assertFalse(Dial.matches(jean, ""))
    }

    @Test
    fun periods() {
        val today = LocalDate.of(2026, 10, 29) // jeudi
        fun at(day: LocalDate) = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(Period.TODAY, Period.of(at(today), today, ZoneOffset.UTC))
        assertEquals(Period.YESTERDAY, Period.of(at(today.minusDays(1)), today, ZoneOffset.UTC))
        assertEquals(Period.WEEK, Period.of(at(LocalDate.of(2026, 10, 26)), today, ZoneOffset.UTC)) // lundi
        assertEquals(Period.OLDER, Period.of(at(LocalDate.of(2026, 10, 25)), today, ZoneOffset.UTC)) // dimanche
    }

    @Test
    fun speedDialRoundTrip() {
        val slots = mapOf('3' to "+33612345678", '2' to "0102030405")
        assertEquals("2=0102030405;3=+33612345678", SpeedDial.encode(slots))
        assertEquals(slots, SpeedDial.decode(SpeedDial.encode(slots)))
        // 1 (messagerie), 0 et les entrées abîmées sont ignorés
        assertEquals(mapOf('5' to "06"), SpeedDial.decode("1=0600;0=1;5=06;9=;x;77=1"))
        assertEquals(emptyMap<Char, String>(), SpeedDial.decode(""))
    }
}
