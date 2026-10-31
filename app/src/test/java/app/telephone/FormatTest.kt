package app.telephone

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun durations() {
        assertEquals("0:05", formatDuration(5))
        assertEquals("12:00", formatDuration(720))
        assertEquals("1:02:03", formatDuration(3723))
    }
}
