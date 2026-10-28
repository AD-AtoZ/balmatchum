package kr.ac.cnu.balmatchum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateDebouncerTest {

    @Test
    fun firstValueIsAcceptedImmediately() {
        val d = StateDebouncer<String>(5_000)
        assertTrue(d.update(0, "A"))
        assertEquals("A", d.current)
    }

    @Test
    fun changesOnlyAfterHoldTime() {
        val d = StateDebouncer<String>(5_000)
        d.update(0, "A")
        assertFalse(d.update(1_000, "B"))
        assertFalse(d.update(5_900, "B"))
        assertEquals("A", d.current)
        assertTrue(d.update(6_000, "B"))
        assertEquals("B", d.current)
    }

    @Test
    fun flickerResetsTheTimer() {
        val d = StateDebouncer<String>(5_000)
        d.update(0, "A")
        d.update(1_000, "B")
        d.update(3_000, "A") // 다시 원래 상태
        d.update(4_000, "B")
        assertFalse(d.update(8_000, "B")) // 4초에 다시 시작했으니 아직 4초
        assertTrue(d.update(9_000, "B"))
    }
}
