package com.thelightphone.lp3Keyboard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.lp3Keyboard.ui.LayoutOptions
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardSwipeCallback
import com.thelightphone.lp3Keyboard.ui.SpecialKey
import com.thelightphone.lp3Keyboard.ui.SpecialKey.Close
import com.thelightphone.lp3Keyboard.ui.layout.CapsLockedLayout
import com.thelightphone.lp3Keyboard.ui.layout.EmojiLayout
import com.thelightphone.lp3Keyboard.ui.layout.ExtendedCharKeyboard
import com.thelightphone.lp3Keyboard.ui.layout.Layout
import com.thelightphone.lp3Keyboard.ui.layout.LowerCaseLayout
import com.thelightphone.lp3Keyboard.ui.layout.NumberLayout
import com.thelightphone.lp3Keyboard.ui.layout.SymbolsLayout
import com.thelightphone.lp3Keyboard.ui.layout.UpperCaseLayout
import com.thelightphone.lp3Keyboard.ui.layout.ZhuyinLayout
import com.thelightphone.lp3Keyboard.ui.layout.extendedCharMapping
import com.thelightphone.lp3Keyboard.ui.zhuyin.CandidateSource
import com.thelightphone.lp3Keyboard.ui.zhuyin.StubCandidateSource
import com.thelightphone.lp3Keyboard.ui.zhuyin.ZhuyinCandidate
import com.thelightphone.lp3Keyboard.ui.zhuyin.ZhuyinComposer
import com.thelightphone.lp3Keyboard.ui.zhuyin.ZhuyinComposerHost
import com.thelightphone.lp3Keyboard.ui.zhuyin.ZhuyinComposerState
import com.thelightphone.lp3Keyboard.ui.zhuyin.ZhuyinImeActions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class EnQwertyLp3KeyboardViewModel<SwipeResult>(
    private val passedCallback: Lp3RepeatableKeyboardCallback,
    private val swipeCallback: Lp3KeyboardSwipeCallback<SwipeResult>,
    private val haptic: () -> Unit = {},
    // Backs Zhuyin candidate lookup. Defaults to the tiny stub so previews/tests
    // need no assets; the IME injects the asset-backed source.
    candidateSource: CandidateSource = StubCandidateSource(),
    initialLayout: Layout = LowerCaseLayout,
    private val optionsForLayout: (Layout) -> LayoutOptions = {
        LayoutOptions(
            displayCloseButton = true
        )
    },
    override val keyboardOptionsFlow: StateFlow<KeyboardOptions> = MutableStateFlow(
        KeyboardOptions(
            defaultEmojis,
            displayReturn = true,
            displayVoice = true,
            enableKeyAnimation = true,
            swipeEnabled = false
        )
    )
) : ViewModel(), Lp3KeyboardViewModel<SwipeResult>, ZhuyinComposerHost {
    var previousLayout: Layout? = null
        private set

    private var swipeActive = false

    // --- Zhuyin composition ------------------------------------------------
    // A single composition session per keyboard. Active only while ZhuyinLayout
    // is showing; the English/number/symbol paths never touch it.
    private val composer = ZhuyinComposer(candidateSource)
    private val _composerState = MutableStateFlow(ZhuyinComposerState.EMPTY)
    override val composerStateFlow: StateFlow<ZhuyinComposerState> = _composerState

    /** Pre-edit/commit round-trip lives in the IME; null when embedded elsewhere. */
    private val zhuyinIme: ZhuyinImeActions?
        get() = passedCallback as? ZhuyinImeActions

    /** Push the current buffer to the candidate bar and the IME pre-edit region. */
    private fun syncComposer() {
        val snapshot = composer.snapshot()
        _composerState.value = snapshot
        zhuyinIme?.onComposingChanged(snapshot.composing)
    }

    /** Abandon any in-progress composition (used when leaving ZhuyinLayout). */
    private fun resetComposer() {
        if (composer.isEmpty) return
        composer.clear()
        _composerState.value = ZhuyinComposerState.EMPTY
        zhuyinIme?.onComposingChanged("")
    }

    override fun onCandidateSelected(candidate: ZhuyinCandidate) {
        haptic()
        zhuyinIme?.onCommitCandidate(candidate.text)
        // Consume only the reading this candidate covered; any trailing syllables
        // stay composing so the user can keep picking (你 then 好, not 你好好).
        val next = composer.commit(candidate.consumed)
        _composerState.value = next
        zhuyinIme?.onComposingChanged(next.composing)
    }

    private val delegateCallback: Lp3RepeatableKeyboardCallback?
        get() = passedCallback.takeUnless { swipeActive }

    override val layoutFlow: MutableStateFlow<Layout> = MutableStateFlow(initialLayout)

    private fun setLayout(layout: Layout) {
        // Leaving Zhuyin drops any half-built composition rather than stranding
        // an underlined pre-edit region behind the new layout.
        if (layout != ZhuyinLayout) resetComposer()
        previousLayout = layoutFlow.value
        layoutOptionsFlow.value = optionsForLayout(layout)
        layoutFlow.value = layout
    }

    override val layoutOptionsFlow = MutableStateFlow(optionsForLayout(initialLayout))

    companion object {
        private const val REPEAT_INTERVAL_MS = 350L
    }

    private val heldSpecialKeys = mutableMapOf<SpecialKey, Job>()
    private val heldKeys = mutableMapOf<Int, Job>()

    fun cancelHeldKeys() {
        heldSpecialKeys.values.forEach { it.cancel() }
        heldSpecialKeys.clear()
        heldKeys.values.forEach { it.cancel() }
        heldKeys.clear()
    }

    var capsMode: CapsMode = CapsMode.Off
        private set

    private fun showAlphabetLayout() {
        setLayout(
            when (capsMode) {
                CapsMode.Off -> LowerCaseLayout
                CapsMode.Single -> UpperCaseLayout
                CapsMode.Locked -> CapsLockedLayout
            }
        )
    }

    override fun onKeyPressed(code: Int) {
        haptic()
        // eagerly drop single-caps so fast typists see lowercase before the IME round-trip
        if (capsMode == CapsMode.Single) {
            capsMode = CapsMode.Off
            showAlphabetLayout()
        }
        delegateCallback?.onKeyPressed(code)
    }

    override fun onSpecialKeyPressed(key: SpecialKey) {
        haptic()
        delegateCallback?.onSpecialKeyPressed(key)
    }

    override fun onKeyReleased(code: Int) {
        heldKeys.remove(code)?.apply {
            cancel()
            return // swallow on key released if held
        }
        // Zhuyin: bopomofo/tone keys feed the composer (pre-edit + candidates)
        // instead of committing the glyph directly. Only while ZhuyinLayout is up.
        if (layoutFlow.value == ZhuyinLayout && ZhuyinComposer.isZhuyinSymbol(code)) {
            composer.append(code.toChar())
            syncComposer()
            return
        }
        // auto-dismiss when a special key is typed
        if (layoutFlow.value is ExtendedCharKeyboard) {
            setLayout(previousLayout ?: LowerCaseLayout)
        }
        delegateCallback?.onKeyReleased(code)
    }

    override fun onKeyCancelled(code: Int) {
        // Finger left the key bounds — treat as the start of a swipe (or a
        // deliberate tap-cancel). Clean up press state but don't fire the IME
        // release, which is where text actually gets committed.
        heldKeys.remove(code)?.cancel()
        if (layoutFlow.value is ExtendedCharKeyboard) {
            setLayout(previousLayout ?: LowerCaseLayout)
        }
    }

    override fun onSpecialKeyReleased(key: SpecialKey) {
        val repeatJob = heldSpecialKeys.remove(key)
        // if we were long-pressing, swallow the release
        repeatJob?.apply {
            cancel()
            return
        }
        var consumed = true
        when (key) {
            SpecialKey.UpCase, SpecialKey.DownCase -> {
                capsMode = when (capsMode) {
                    CapsMode.Off -> CapsMode.Single
                    CapsMode.Single, CapsMode.Locked -> CapsMode.Off
                }
                showAlphabetLayout()
            }

            SpecialKey.Numbers -> {
                setLayout(NumberLayout)
            }

            SpecialKey.Letters -> {
                showAlphabetLayout()
            }

            SpecialKey.Symbols -> {
                setLayout(SymbolsLayout)
            }

            SpecialKey.Zhuyin -> {
                setLayout(ZhuyinLayout)
            }

            SpecialKey.Emojis -> {
                setLayout(EmojiLayout)
            }

            SpecialKey.Backspace -> {
                // While composing, backspace pops the bopomofo buffer; once it's
                // empty (or we're not composing) fall through to the IME's normal
                // field backspace.
                if (layoutFlow.value == ZhuyinLayout && composer.backspace()) {
                    syncComposer()
                } else {
                    consumed = false
                }
            }

            Close -> {
                if (!layoutFlow.value.isRootLayout) {
                    showAlphabetLayout()
                } else {
                    consumed = false
                }
            }

            else -> {
                consumed = false
            }
        }
        if (!consumed) {
            delegateCallback?.onSpecialKeyReleased(key)
        }
    }

    /** Called by IME after each character to handle system-requested caps. */
    fun setCapsMode(enabled: Boolean) {
        if (capsMode == CapsMode.Locked) return
        capsMode = if (enabled) CapsMode.Single else CapsMode.Off
        when (layoutFlow.value) {
            // only update the layout if we were already showing letters
            LowerCaseLayout, UpperCaseLayout, CapsLockedLayout -> showAlphabetLayout()
            else -> {}
        }
    }

    override fun onKeyLongPressed(code: Int) {
        heldKeys[code]?.cancel()
        if (extendedCharMapping.containsKey(code)) {
            haptic()
            setLayout(ExtendedCharKeyboard(code))
            heldKeys[code] = viewModelScope.launch { }
            return
        }
        delegateCallback?.onKeyLongPressed(code)
        heldKeys[code] = viewModelScope.launch {
            while (isActive) {
                delay(REPEAT_INTERVAL_MS)
                delegateCallback?.onKeyRepeated(code)
            }
        }
    }

    override fun onSpecialKeyLongPressed(key: SpecialKey) {
        heldSpecialKeys[key]?.cancel()
        val allowRepeats = when (key) {
            SpecialKey.UpCase, SpecialKey.DownCase -> {
                capsMode = if (capsMode == CapsMode.Locked) CapsMode.Off else CapsMode.Locked
                heldSpecialKeys[key] = viewModelScope.launch { }
                showAlphabetLayout()
                // don't allow repeats since we switched layouts and the original button is gone
                false
            }

            SpecialKey.Numbers -> {
                // Long-press "123" to switch into 注音 (bopomofo) — the bottom
                // row has no width budget for a dedicated toggle; "EN" on the
                // Zhuyin layout returns. Occupy the held slot so the release is
                // swallowed instead of also switching to the number layout.
                setLayout(ZhuyinLayout)
                heldSpecialKeys[key] = viewModelScope.launch { }
                false
            }

            else -> true
        }
        haptic()
        delegateCallback?.onSpecialKeyLongPressed(key)
        if (allowRepeats) {
            heldSpecialKeys[key] = viewModelScope.launch {
                while (isActive) {
                    delay(REPEAT_INTERVAL_MS)
                    delegateCallback?.onSpecialKeyRepeated(key)
                }
            }
        }
    }

    override fun onSubmitWord(word: CharSequence) {
        delegateCallback?.onSubmitWord("$word ")
    }

    override fun onSwipeStarted() {
        if (keyboardOptionsFlow.value.swipeEnabled) {
            swipeActive = true
        }
    }

    override fun onSwipeLayoutReady(
        letters: String,
        cx: FloatArray,
        cy: FloatArray
    ) {
        swipeCallback.onSwipeLayoutReady(letters, cx, cy)
    }

    override fun onSwipeCompleted(
        x: FloatArray,
        y: FloatArray,
        t: FloatArray
    ): List<SwipeResult> {
        val results = swipeCallback.onSwipeCompleted(x,y,t)
        swipeActive = false
        if (results.isNotEmpty()) {
            swipeCallback.getWordForResult(results[0])
                ?.let(this::onSubmitWord)
        }
        return results
    }

    override fun getWordForResult(swipeResult: SwipeResult) = swipeCallback.getWordForResult(swipeResult)

    override fun onCleared() {
        super.onCleared()
        cancelHeldKeys()
    }
}