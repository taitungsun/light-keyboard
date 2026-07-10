package com.thelightphone.lp3Keyboard.ui.zhuyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Exercises the real bundled dictionary (~91k readings) through the real lookup
 * code, so parse/encoding/scale issues surface without needing a device. Reads
 * the committed `.gz` straight from the module's assets (Gradle runs unit tests
 * with the module dir as CWD); skipped gracefully if the asset isn't reachable.
 */
class ZhuyinDictionaryRealDataTest {

    private val dict: ZhuyinDictionary? by lazy {
        val f = File("src/main/assets/dictionaries/zh-bopomofo-chewing.dict.gz")
        if (!f.exists()) return@lazy null
        GZIPInputStream(f.inputStream()).bufferedReader(Charsets.UTF_8).use {
            ZhuyinDictionary.fromLines(it.lineSequence())
        }
    }

    private fun lookup(buffer: String) =
        dict!!.lookup(ZhuyinDictionary.readingKey(buffer))

    @Test
    fun `real dictionary loads a large key set`() {
        val d = dict; assumeTrue("asset not reachable from test CWD", d != null)
        assertTrue("expected tens of thousands of keys, got ${d!!.keyCount}", d.keyCount > 50_000)
    }

    @Test
    fun `common single syllables rank the frequent word first`() {
        assumeTrue(dict != null)
        assertEquals("你", lookup("ㄋㄧˇ").first())
        assertEquals("是", lookup("ㄕˋ").first())
        assertEquals("我", lookup("ㄨㄛˇ").first())
    }

    @Test
    fun `multi-syllable phrases resolve`() {
        assumeTrue(dict != null)
        assertTrue("你好" in lookup("ㄋㄧˇ ㄏㄠˇ"))
        assertTrue("謝謝" in lookup("ㄒㄧㄝˋ ㄒㄧㄝˋ"))
        assertTrue("中文" in lookup("ㄓㄨㄥ ㄨㄣˊ"))
    }

    @Test
    fun `tones are ignored so any tone on a syllable still finds the word`() {
        assumeTrue(dict != null)
        // Same reading, different (or absent) tone marks → same candidate set.
        assertEquals(lookup("ㄕ"), lookup("ㄕˋ"))
        assertTrue("是" in lookup("ㄕ"))
    }
}
