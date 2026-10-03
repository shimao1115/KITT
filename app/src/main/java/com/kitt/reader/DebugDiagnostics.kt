package com.kitt.reader

import android.content.Context
import android.util.Log
import java.io.File

/** Bounded, debug-only fallback for OEMs that suppress application logcat. No credentials or transcripts. */
class DebugDiagnostics(context: Context) {
    private val file = File(context.cacheDir, "hotfix-diagnostics.log")
    init { if (BuildConfig.DEBUG) runCatching { file.delete() } }
    @Synchronized fun log(tag: String, message: String) {
        Log.i(tag, message)
        if (BuildConfig.DEBUG) runCatching {
            if (file.length() > 128000) file.writeText(file.readText().takeLast(64000))
            file.appendText("${System.currentTimeMillis()} $tag $message\n")
        }
    }
}
