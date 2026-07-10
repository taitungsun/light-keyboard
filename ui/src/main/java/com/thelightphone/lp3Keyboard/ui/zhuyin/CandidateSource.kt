package com.thelightphone.lp3Keyboard.ui.zhuyin

/**
 * Turns a bopomofo reading (a run of ㄅㄆㄇ… symbols plus an optional tone mark)
 * into an ordered list of candidate 漢字 / words, most likely first.
 *
 * This is the seam between the keyboard UI and the dictionary. Phase 2 ships the
 * [StubCandidateSource] below so the composing/candidate pipeline can be built
 * and demoed end-to-end; Phase 3 swaps in a real data-backed implementation
 * (a packed bopomofo→word table, frequency ranking, user history) behind this
 * exact interface without touching the UI or the composer.
 */
interface CandidateSource {
    /**
     * @param reading the raw composing buffer, e.g. "ㄋㄧˇ" or a still-incomplete
     *                "ㄋㄧ". Never empty (the composer skips lookup when empty).
     * @return candidates ordered best-first, or empty if nothing matches.
     */
    fun candidates(reading: String): List<String>
}

/**
 * A hand-seeded, in-memory dictionary covering just enough syllables to exercise
 * the pipeline (build a buffer, see candidates, tap to commit). NOT a real IME
 * dictionary — no coverage guarantees, no ranking, no multi-syllable words.
 *
 * Lookup strategy, intentionally simple:
 *   1. exact reading match, then
 *   2. prefix matches (so candidates appear while a syllable is mid-typed),
 * de-duplicated, capped so the bar never renders an unbounded row.
 */
class StubCandidateSource(
    private val table: Map<String, List<String>> = DEFAULT_TABLE,
    private val limit: Int = 20,
) : CandidateSource {

    override fun candidates(reading: String): List<String> {
        if (reading.isEmpty()) return emptyList()
        val exact = table[reading].orEmpty()
        // Prefix hits for readings the user hasn't finished (no tone mark yet).
        val prefix = table.asSequence()
            .filter { (k, _) -> k != reading && k.startsWith(reading) }
            .flatMap { it.value.asSequence() }
        return (exact.asSequence() + prefix)
            .distinct()
            .take(limit)
            .toList()
    }

    companion object {
        /** Tiny demo set. Keys are full readings incl. tone mark (˙ˇˊˋ; 1st tone bare). */
        val DEFAULT_TABLE: Map<String, List<String>> = mapOf(
            "ㄋㄧˇ" to listOf("你", "妳"),
            "ㄏㄠˇ" to listOf("好"),
            "ㄏㄠ" to listOf("蒿"),
            "ㄨㄛˇ" to listOf("我"),
            "ㄇㄣ˙" to listOf("們"),
            "ㄇㄣ" to listOf("門", "悶"),
            "ㄉㄚˋ" to listOf("大"),
            "ㄒㄧㄝˋ" to listOf("謝", "械", "卸"),
            "ㄒㄧㄝˇ" to listOf("寫"),
            "ㄕㄧˋ" to listOf("是", "事", "世", "市"),
            "ㄕˋ" to listOf("是", "事", "世", "市"),
            "ㄇㄚ˙" to listOf("嗎"),
            "ㄇㄚ" to listOf("媽", "馬", "麻"),
            "ㄓㄨㄥ" to listOf("中", "鐘", "終"),
            "ㄨㄣˊ" to listOf("文", "聞", "紋"),
            "ㄗˋ" to listOf("字", "自"),
        )
    }
}
