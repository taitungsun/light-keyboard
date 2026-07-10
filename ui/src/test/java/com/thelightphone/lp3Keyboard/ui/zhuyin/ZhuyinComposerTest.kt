package com.thelightphone.lp3Keyboard.ui.zhuyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZhuyinComposerTest {

    private fun composer() = ZhuyinComposer(StubCandidateSource())

    @Test
    fun `empty buffer yields empty state`() {
        assertEquals(ZhuyinComposerState.EMPTY, composer().snapshot())
    }

    @Test
    fun `building a full reading surfaces candidates`() {
        val c = composer()
        "ㄋㄧˇ".forEach { c.append(it) }
        val state = c.snapshot()
        assertEquals("ㄋㄧˇ", state.composing)
        assertTrue("你" in state.candidates)
    }

    @Test
    fun `partial reading matches by prefix before the tone mark`() {
        val c = composer()
        c.append('ㄋ'); c.append('ㄧ') // no tone yet
        // "ㄋㄧ" isn't an exact key, but "ㄋㄧˇ" is a prefix match.
        assertTrue("你" in c.snapshot().candidates)
    }

    @Test
    fun `backspace pops one symbol and reports emptiness`() {
        val c = composer()
        c.append('ㄕ'); c.append('ˋ')
        assertTrue(c.backspace())
        assertEquals("ㄕ", c.composing)
        assertTrue(c.backspace())
        assertFalse(c.backspace()) // now empty
        assertTrue(c.isEmpty)
    }

    @Test
    fun `clear empties the buffer`() {
        val c = composer()
        "ㄉㄚˋ".forEach { c.append(it) }
        c.clear()
        assertTrue(c.isEmpty)
        assertEquals(ZhuyinComposerState.EMPTY, c.snapshot())
    }

    @Test
    fun `isZhuyinSymbol accepts bopomofo and tone marks but not latin`() {
        assertTrue(ZhuyinComposer.isZhuyinSymbol('ㄅ'.code))
        assertTrue(ZhuyinComposer.isZhuyinSymbol('ㄩ'.code))
        assertTrue(ZhuyinComposer.isZhuyinSymbol('ˇ'.code))
        assertTrue(ZhuyinComposer.isZhuyinSymbol('˙'.code))
        assertFalse(ZhuyinComposer.isZhuyinSymbol('a'.code))
        assertFalse(ZhuyinComposer.isZhuyinSymbol('5'.code))
    }
}
