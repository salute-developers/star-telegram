package ru.sberdevices.sbdv.view

import junit.framework.TestCase
import org.junit.Test

class AvatarPlaceholderTest : TestCase() {

    @Test
    fun testDeterminePlaceholderText() {
        //empty or bad signs
        assertEquals("-", "".extractEmoji())
        assertEquals("-", "!@#$%?,|[]".extractEmoji())

        //letters
        assertEquals("Ф", "фю".extractEmoji())
        assertEquals("Ф", "фю\uD83D\uDE00Ф".extractEmoji())
        assertEquals("Ф", "=-!\$фю".extractEmoji())

        //digits
        assertEquals("1", "1ф".extractEmoji())
        assertEquals("1", "11".extractEmoji())

        //emojis
        assertEquals("\uD83D\uDE00", "\uD83D\uDE00".extractEmoji())
        assertEquals("\uD83D\uDE00", "\uD83D\uDE00\uD83D\uDE00".extractEmoji())
        assertEquals("\uD83D\uDE00", "\uD83D\uDE00Ф".extractEmoji())
        assertEquals("\uD83D\uDE00", "!-$\uD83D\uDE00Ф".extractEmoji())
    }

    private fun String.extractEmoji(): String {
        val privateMethod = AvatarPlaceholder::class.java.getDeclaredMethod(
            "determinePlaceholderText",
            String::class.java,
            String::class.java
        )
        privateMethod.isAccessible = true
        val returnValue = privateMethod.invoke(privateMethod, this, null) as String
        println("determinePlaceholderText($this): $returnValue")
        return returnValue
    }
}