package com.kitt.reader
import android.app.Application
class KittApp : Application() { val runtime: KittRuntime by lazy { KittRuntime(this) } }
