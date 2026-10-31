package app.telephone

import org.junit.Assert.assertEquals
import org.junit.Test

class AlphabetTest {
    @Test
    fun letters() {
        assertEquals('E', Alphabet.letterOf("Émilie"))
        assertEquals('#', Alphabet.letterOf("0649923402"))
        assertEquals('#', Alphabet.letterOf(""))
    }

    @Test
    fun nearestLetterWithContacts() {
        val enabled = Alphabet.LETTERS.map { it == 'A' || it == 'D' }
        val height = 27f
        assertEquals(0, Alphabet.indexAt(0.5f, height, enabled))
        assertEquals(3, Alphabet.indexAt(4.5f, height, enabled)) // E vide : D est le plus proche
        assertEquals(-1, Alphabet.indexAt(3f, height, Alphabet.LETTERS.map { false }))
    }
}
