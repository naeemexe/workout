package work.lockedinlabs.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import work.lockedinlabs.tracker.data.SetCodec
import work.lockedinlabs.tracker.data.SetEntry
import java.util.Locale

class FormatTest {
    @Test fun `weight text is exact and has no trailing zeros`() {
        assertEquals("50", 50.0.weightText())
        assertEquals("52.5", 52.5.weightText())
        assertEquals("52.25", 52.25.weightText())
    }

    @Test fun `weight text ignores the phone's decimal comma`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("52.5", 52.5.weightText())
            assertEquals(52.5, 52.5.weightText().toDouble(), 0.0)
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test fun `a typed decimal comma becomes a point`() = assertEquals("52.5", normalizeDecimal("52,5"))

    @Test fun `sets round-trip compactly and old rows still read`() {
        val sets = listOf(SetEntry(50.0, 10), SetEntry(52.25, 8), SetEntry(0.0, 12))
        assertEquals("50x10;52.25x8;0x12", SetCodec.encode(sets))
        assertEquals(sets, SetCodec.decode(SetCodec.encode(sets)))
        assertEquals(listOf(SetEntry(50.0, 10)), SetCodec.decode("50.0x10"))
    }
}
