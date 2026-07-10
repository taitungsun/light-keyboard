package com.thelightphone.lp3Keyboard.ui.zhuyin

/**
 * In-memory bopomofo→漢字 lookup, built from the preprocessed dictionary asset
 * (see `ui/tools/build_zhuyin_dict.py`). Pure Kotlin / no Android, so it's
 * unit-testable and independent of how the bytes are loaded — [AssetCandidateSource]
 * feeds it the asset lines.
 *
 * Keys are readings with **tone marks and spaces removed** (matching how the
 * keyboard buffer arrives: space-less, tones optional). Per-key word lists are
 * already frequency-ranked by the preprocessing step, so lookup preserves order.
 */
class ZhuyinDictionary private constructor(
    private val keys: Array<String>,          // sorted ascending
    private val values: Array<Array<String>>, // parallel: words per key, best-first
) {
    val keyCount: Int get() = keys.size

    /**
     * Candidates for [query] (already tone/space-stripped by the caller):
     * exact-match words first, then words from keys that have [query] as a prefix
     * (so partially-typed multi-syllable phrases still surface), de-duplicated and
     * capped at [limit].
     */
    fun lookup(query: String, limit: Int = DEFAULT_LIMIT): List<String> {
        if (query.isEmpty() || keys.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        var i = lowerBound(query)
        // keys are sorted, so an exact match (if present) sits at the first index
        // whose key >= query, and all prefix extensions follow contiguously.
        while (i < keys.size && keys[i].startsWith(query)) {
            for (w in values[i]) {
                out.add(w)
                if (out.size >= limit) return out.toList()
            }
            i++
        }
        return out.toList()
    }

    /**
     * Candidates whose key is **exactly** [query] (tone/space-stripped by the
     * caller), best-first, capped at [limit]. Unlike [lookup] this never pulls in
     * words from longer keys, so a caller that has committed a reading boundary
     * (e.g. one segmented syllable) never over-consumes into a longer phrase.
     */
    fun lookupExact(query: String, limit: Int = DEFAULT_LIMIT): List<String> {
        if (query.isEmpty() || keys.isEmpty()) return emptyList()
        val i = lowerBound(query)
        if (i >= keys.size || keys[i] != query) return emptyList()
        val words = values[i]
        return if (words.size <= limit) words.toList() else words.take(limit)
    }

    /** First index whose key is >= [q]; keys.size if none. */
    private fun lowerBound(q: String): Int {
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < q) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        const val DEFAULT_LIMIT = 60

        // Must match the tone/space stripping in build_zhuyin_dict.py so a runtime
        // query lands on the same keys the asset was built with.
        // U+02C9 ˉ, U+02CA ˊ, U+02C7 ˇ, U+02CB ˋ, U+02D9 ˙.
        private val STRIP = charArrayOf(' ', '\t', 'ˉ', 'ˊ', 'ˇ', 'ˋ', '˙')

        /** Reduce a raw bopomofo buffer to its tone/space-stripped lookup key. */
        fun readingKey(reading: String): String =
            buildString { for (c in reading) if (c !in STRIP) append(c) }

        /**
         * Build from `key\tword1 word2 …` lines (the format emitted by the
         * preprocessing script). Input is expected already sorted by key; if not,
         * pass [presorted] = false to sort here.
         */
        fun fromLines(lines: Sequence<String>, presorted: Boolean = true): ZhuyinDictionary {
            val ks = ArrayList<String>()
            val vs = ArrayList<Array<String>>()
            for (line in lines) {
                if (line.isEmpty()) continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                ks.add(line.substring(0, tab))
                vs.add(line.substring(tab + 1).split(' ').toTypedArray())
            }
            if (!presorted) {
                val order = ks.indices.sortedBy { ks[it] }
                return ZhuyinDictionary(
                    Array(order.size) { ks[order[it]] },
                    Array(order.size) { vs[order[it]] },
                )
            }
            return ZhuyinDictionary(ks.toTypedArray(), vs.toTypedArray())
        }
    }
}
