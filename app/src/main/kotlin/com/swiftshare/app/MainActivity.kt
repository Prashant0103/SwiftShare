package com.swiftshare.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.MainScope

/**
 * FR-06.1's share-sheet target: this Activity is the `ACTION_SEND`/
 * `ACTION_SEND_MULTIPLE` handler declared in the manifest. Runtime permission
 * requesting (flagged as Module 06 work in the manifest's own comment) lives
 * here, requested once up front rather than per-feature.
 */
class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer(applicationContext) }
    private val controller by lazy { ShareFlowController(container, MainScope()) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) controller.startScanning()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by controller.state.collectAsState()
            ShareFlowScreen(
                state = state,
                onSelectDevice = controller::selectDevice,
                onRetry = controller::startScanning,
            )
        }
        permissionLauncher.launch(requiredPermissions())
    }

    private fun requiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()
}
