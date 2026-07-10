package com.thelightphone.lp3Keyboard.ui.zhuyin

import kotlinx.coroutines.flow.StateFlow

/**
 * Immutable snapshot the UI renders: the raw bopomofo the user is building
 * ([composing], shown as underlined pre-edit text in the field) and the ranked
 * [candidates] for it (shown in the candidate bar).
 */
data class ZhuyinComposerState(
    val composing: String,
    val candidates: List<String>,
) {
    /** Whether a composition is in progress — drives whether the bar shows. */
    val isActive: Boolean get() = composing.isNotEmpty()

    companion object {
        val EMPTY = ZhuyinComposerState("", emptyList())
    }
}

/**
 * Holds the in-progress bopomofo buffer and derives candidates from a
 * [CandidateSource]. Pure logic, no Compose / no Android — so it unit-tests
 * cleanly and the view model can own one per keyboard session.
 *
 * Phase 2 keeps the whole buffer as one flat run of symbols and hands it to the
 * source verbatim. Real syllable segmentation (splitting "ㄋㄧˇㄏㄠˇ" into
 * 你/好 as two syllables, committing left-to-right) is Phase 3 — see [append].
 */
class ZhuyinComposer(private val source: CandidateSource) {
    private val buffer = StringBuilder()

    val composing: String get() = buffer.toString()
    val isEmpty: Boolean get() = buffer.isEmpty()

    /**
     * Append one bopomofo symbol or tone mark. Callers gate on
     * [isZhuyinSymbol]; anything else should be committed directly, not routed
     * here.
     *
     * TODO(phase3): once a tone mark lands, a syllable is complete — segment it
     * off so multi-character input builds a phrase instead of one long reading.
     */
    fun append(symbol: Char) {
        buffer.append(symbol)
    }

    /** Pop the last symbol. Returns false if the buffer was already empty (so
     *  the caller can fall through to a normal field backspace). */
    fun backspace(): Boolean {
        if (buffer.isEmpty()) return false
        buffer.deleteCharAt(buffer.length - 1)
        return true
    }

    fun clear() {
        buffer.setLength(0)
    }

    /** Current snapshot: buffer text + candidates (empty buffer ⇒ no lookup). */
    fun snapshot(): ZhuyinComposerState {
        val reading = buffer.toString()
        if (reading.isEmpty()) return ZhuyinComposerState.EMPTY
        return ZhuyinComposerState(reading, source.candidates(reading))
    }

    companion object {
        // Bopomofo consonants + medials + rimes: U+3105 (ㄅ) .. U+3129 (ㄩ),
        // with the block reserving through U+312F for later additions.
        private const val BOPOMOFO_START = 0x3105
        private const val BOPOMOFO_END = 0x312F

        // Tone marks the MOE layout emits. First tone (ˉ) is conventionally
        // omitted, so it isn't here. ˙ˇˊˋ = light/3rd/2nd/4th.
        private val TONE_MARKS = charArrayOf('˙', 'ˇ', 'ˊ', 'ˋ')

        /** True if [code] is a bopomofo symbol or tone mark the composer accepts. */
        fun isZhuyinSymbol(code: Int): Boolean =
            code in BOPOMOFO_START..BOPOMOFO_END ||
                (code <= Char.MAX_VALUE.code && code.toChar() in TONE_MARKS)
    }
}

/**
 * Implemented by a view model that supports Zhuyin composition, so the
 * candidate-bar UI can observe state and report taps without knowing the
 * concrete view model type.
 */
interface ZhuyinComposerHost {
    val composerStateFlow: StateFlow<ZhuyinComposerState>

    /** User tapped a candidate in the bar. */
    fun onCandidateSelected(candidate: String)
}

/**
 * Implemented by the InputMethodService. The view model drives the actual
 * InputConnection pre-edit / commit through this, so composition state stays in
 * the view model and only the IC calls live in the service.
 */
interface ZhuyinImeActions {
    /** Set (or, on empty text, finish) the underlined composing region. */
    fun onComposingChanged(composing: CharSequence)

    /** Commit a chosen candidate and end the current composition. */
    fun onCommitCandidate(text: CharSequence)
}
