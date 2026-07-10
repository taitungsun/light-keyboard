package com.thelightphone.lp3Keyboard.ui.zhuyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZhuyinDictionaryTest {

    // Minimal fixture mirroring the asset format: `key\tword1 word2 …`, sorted by key.
    private val dict = ZhuyinDictionary.fromLines(
        sequenceOf(
            "ㄋㄧ\t你 妳 泥 尼",
            "ㄋㄧㄇㄣ\t你們",
            "ㄋㄧㄏㄠ\t你好 妳好",
            "ㄕ\t是 事 市",
        ),
    )

    @Test
    fun `exact single-syllable lookup is frequency-ordered`() {
        // Exact-ㄋㄧ words come first (then ㄋㄧ… prefixes, covered below).
        assertEquals(listOf("你", "妳", "泥", "尼"), dict.lookup("ㄋㄧ").take(4))
    }

    @Test
    fun `exact match comes before longer prefix extensions`() {
        val r = dict.lookup("ㄋㄧ")
        // 你 (exact ㄋㄧ) must precede 你好/你們 (ㄋㄧ… prefixes).
        assertTrue(r.indexOf("你") < r.indexOf("你好"))
        assertTrue("你好" in r && "你們" in r)
    }

    @Test
    fun `multi-syllable exact lookup`() {
        assertEquals(listOf("你好", "妳好"), dict.lookup("ㄋㄧㄏㄠ"))
    }

    @Test
    fun `limit caps the result count`() {
        assertEquals(2, dict.lookup("ㄋㄧ", limit = 2).size)
    }

    @Test
    fun `miss and empty query return nothing`() {
        assertTrue(dict.lookup("ㄅㄆㄇ").isEmpty())
        assertTrue(dict.lookup("").isEmpty())
    }

    @Test
    fun `readingKey strips tone marks and spaces to match asset keys`() {
        assertEquals("ㄕ", ZhuyinDictionary.readingKey("ㄕˋ"))
        assertEquals("ㄋㄧㄏㄠ", ZhuyinDictionary.readingKey("ㄋㄧˇ ㄏㄠˇ"))
        assertEquals("ㄇㄣ", ZhuyinDictionary.readingKey("ㄇㄣ˙"))
    }

    @Test
    fun `readingKey feeds lookup end-to-end for a toned buffer`() {
        // What the composer buffer would hold after typing ㄕˋ.
        assertEquals(listOf("是", "事", "市"), dict.lookup(ZhuyinDictionary.readingKey("ㄕˋ")))
    }

    @Test
    fun `fromLines can sort unsorted input`() {
        val d = ZhuyinDictionary.fromLines(
            sequenceOf("ㄕ\t是", "ㄋㄧ\t你"),
            presorted = false,
        )
        assertEquals(listOf("你"), d.lookup("ㄋㄧ"))
        assertEquals(listOf("是"), d.lookup("ㄕ"))
    }
}
