package com.thelightphone.lp3Keyboard.ui.zhuyin

import android.content.Context
import android.util.Log

/**
 * [CandidateSource] backed by the bundled libchewing-derived dictionary asset.
 *
 * The asset (~1 MB gzip, ~91k readings) is decompressed and parsed on a
 * background thread at construction, so the keyboard shows instantly and the
 * candidate bar simply returns nothing for the first fraction of a second until
 * [isLoaded] flips true. After that, lookups are in-memory binary searches.
 */
class AssetCandidateSource(
    context: Context,
    private val assetPath: String = DEFAULT_ASSET,
) : CandidateSource {

    private val appContext = context.applicationContext

    @Volatile
    private var dict: ZhuyinDictionary? = null

    val isLoaded: Boolean get() = dict != null

    init {
        Thread(::load, "zhuyin-dict-load").apply { isDaemon = true }.start()
    }

    private fun load() {
        try {
            appContext.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { reader ->
                // fromLines consumes the sequence eagerly, before the reader closes.
                dict = ZhuyinDictionary.fromLines(reader.lineSequence())
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to load Zhuyin dictionary from $assetPath", e)
        }
    }

    override fun candidates(reading: String): List<String> {
        val d = dict ?: return emptyList()
        val key = ZhuyinDictionary.readingKey(reading)
        if (key.isEmpty()) return emptyList()
        return d.lookup(key)
    }

    companion object {
        private const val TAG = "AssetCandidateSource"
        // We commit the dictionary gzipped (…dict.gz, ~1 MB) to keep the repo small,
        // but aapt2 auto-gunzips `.gz` assets at build time and drops the suffix, so
        // the packaged asset is plain UTF-8 text at this path (still deflate-stored
        // in the APK). Hence: no .gz here, and no GZIPInputStream above.
        const val DEFAULT_ASSET = "dictionaries/zh-bopomofo-chewing.dict"
    }
}
