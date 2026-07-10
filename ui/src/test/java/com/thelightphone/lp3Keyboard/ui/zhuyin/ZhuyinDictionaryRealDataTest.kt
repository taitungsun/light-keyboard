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

    /** Wraps the real dict in a source so the composer can be driven over it,
     *  mirroring how [AssetCandidateSource] maps a reading to a key. */
    private fun realSource(d: ZhuyinDictionary) = object : CandidateSource {
        override fun candidates(reading: String) =
            d.lookup(ZhuyinDictionary.readingKey(reading))
        override fun candidatesExact(reading: String) =
            d.lookupExact(ZhuyinDictionary.readingKey(reading))
    }

    @Test
    fun `composer segments a real phrase into whole-phrase and first-syllable picks`() {
        val d = dict; assumeTrue(d != null)
        val c = ZhuyinComposer(realSource(d!!))
        "ㄋㄧˇㄏㄠˇ".forEach { c.append(it) } // 你好

        val cands = c.snapshot().candidates
        // Whole-buffer phrase 你好 present and consuming the whole 6-char buffer…
        val phrase = cands.first { it.text == "你好" }
        assertEquals(6, phrase.consumed)
        // …and the first syllable 你 present, consuming only its 3 chars.
        val you = cands.first { it.text == "你" }
        assertEquals(3, you.consumed)

        // Committing 你 leaves 好 composing, which then resolves 好.
        val after = c.commit(you.consumed)
        assertEquals("ㄏㄠˇ", after.composing)
        assertTrue("好" in after.candidates.map { it.text })
    }
}
