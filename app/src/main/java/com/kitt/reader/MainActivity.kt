package com.kitt.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Text("路上读山河", style = MaterialTheme.typography.headlineLarge)
                        Text("自动时，它读山河；你开口时，它听你的。")
                        Spacer(Modifier.height(32.dp))
                        Button({}, Modifier.fillMaxWidth().height(80.dp)) { Text("开始读山河") }
                    }
                }
            }
        }
    }
}
