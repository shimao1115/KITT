package com.kitt.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var revision by remember { mutableIntStateOf(0) }
                val journey = remember { Journey(System::currentTimeMillis, object : VoicePort {
                    override fun stop() {}
                    override fun speak(text: String, complete: (Boolean) -> Unit) { complete(false) }
                    override fun listen(result: (String?) -> Unit) { result(null) }
                }) { revision++ } }
                revision
                Surface {
                    DrivingScreen(journey, "GPS", journey::start, { journey.beginListening {} }, { journey.end() }, {}, {})
                }
            }
        }
    }
}
