package com.swiftshare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Placeholder shell. Real screens (share-sheet target, device list, transfer
 * progress, pairing confirmation) are Module 06 work, later in the build order.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SwiftShareScaffoldPlaceholder()
        }
    }
}

@Composable
private fun SwiftShareScaffoldPlaceholder() {
    MaterialTheme {
        Surface {
            Text("SwiftShare — architectural scaffold")
        }
    }
}
