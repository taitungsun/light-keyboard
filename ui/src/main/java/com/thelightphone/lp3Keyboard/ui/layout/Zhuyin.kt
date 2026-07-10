package com.thelightphone.lp3Keyboard.ui.layout

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.thelightphone.lp3Keyboard.ui.DefaultRow
import com.thelightphone.lp3Keyboard.ui.FinalRow
import com.thelightphone.lp3Keyboard.ui.ICON_KEY_WIDTH_DP
import com.thelightphone.lp3Keyboard.ui.IconKey
import com.thelightphone.lp3Keyboard.ui.Key
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardCallback
import com.thelightphone.lp3Keyboard.ui.MultiLabelKey
import com.thelightphone.lp3Keyboard.ui.R
import com.thelightphone.lp3Keyboard.ui.SpecialKey

// Standard MOE Zhuyin keyboard mapping (the layout used by default on
// Windows/macOS/most phones' bopomofo IMEs), laid out on the same four
// QWERTY-shaped rows as EnQwerty.kt so this is muscle-memory-familiar to
// any Taiwanese typist rather than a novel arrangement.
//   number row (11): ㄅㄉˇˋㄓˊ˙ㄚㄞㄢㄦ
//   qwertyuiop  (10): ㄆㄊㄍㄐㄔㄗㄧㄛㄟㄣ
//   asdfghjkl    (9): ㄇㄋㄎㄑㄕㄖㄨㄜㄠ
//   zxcvbnm+;,./(11): ㄈㄌㄏㄒㄘㄙㄩㄤㄝㄡㄥ
//
// Phase 1 skeleton: raw symbol keys only, each commits its bopomofo glyph
// directly (same commit path as EnQwerty). No composing buffer, no
// dictionary lookup, no candidate row yet — that's Phase 2.
object ZhuyinLayout : Layout {
    override val isRootLayout: Boolean
        get() = true

    @Composable
    override fun ColumnScope.Render(
        options: KeyboardOptions,
        callback: Lp3KeyboardCallback
    ) {
        DefaultRow {
            for (char in "ㄅㄉˇˋㄓˊ˙ㄚㄞㄢㄦ") {
                Key(char, callback, swipeConfig = null, enableKeyAnimation = options.enableKeyAnimation)
            }
        }
        DefaultRow {
            for (char in "ㄆㄊㄍㄐㄔㄗㄧㄛㄟㄣ") {
                Key(char, callback, swipeConfig = null, enableKeyAnimation = options.enableKeyAnimation)
            }
        }
        DefaultRow {
            for (char in "ㄇㄋㄎㄑㄕㄖㄨㄜㄠ") {
                Key(char, callback, swipeConfig = null, enableKeyAnimation = options.enableKeyAnimation)
            }
        }
        DefaultRow {
            for (char in "ㄈㄌㄏㄒㄘㄙㄩㄤㄝㄡㄥ") {
                Key(char, callback, swipeConfig = null, enableKeyAnimation = options.enableKeyAnimation)
            }
            IconKey(
                R.drawable.back_lp3,
                SpecialKey.Backspace,
                callback,
                options.enableKeyAnimation,
                width = ICON_KEY_WIDTH_DP.dp
            )
        }
        FinalRow(options, callback) {
            // Round-trips back to the English alphabet layout, mirroring how
            // NumberLayout/SymbolsLayout use SpecialKey.Letters to return to
            // LowerCaseLayout/UpperCaseLayout/CapsLockedLayout.
            MultiLabelKey("EN", SpecialKey.Letters, callback, options.enableKeyAnimation)
        }
    }
}
