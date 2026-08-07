package com.swiftshare.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swiftshare.core.discovery.DiscoveredDevice

@Composable
fun ShareFlowScreen(state: UiState, onSelectDevice: (DiscoveredDevice) -> Unit, onRetry: () -> Unit) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (state) {
                is UiState.Scanning -> ScanningScreen()
                is UiState.DeviceList -> DeviceListScreen(state.devices, onSelectDevice)
                is UiState.Connecting -> ConnectingScreen(state.peer)
                is UiState.Connected -> ConnectedScreen(state.peer)
                is UiState.Failed -> FailedScreen(state.message, onRetry)
            }
        }
    }
}

@Composable
private fun ScanningScreen() {
    CenteredColumn {
        CircularProgressIndicator()
        Text("Looking for nearby devices…")
    }
}

/** Proximity-sorted list (FR-06.2's default mode; directional UWB view is a separate slice). */
@Composable
private fun DeviceListScreen(devices: List<DiscoveredDevice>, onSelectDevice: (DiscoveredDevice) -> Unit) {
    if (devices.isEmpty()) {
        CenteredColumn { Text("No devices found yet…") }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(devices) { device ->
            Text(
                text = "Nearby device (${device.rssi} dBm)",
                modifier = Modifier.fillMaxSize().padding(12.dp).clickable { onSelectDevice(device) },
            )
        }
    }
}

@Composable
private fun ConnectingScreen(peer: DiscoveredDevice) {
    CenteredColumn {
        CircularProgressIndicator()
        Text("Connecting…")
    }
}

@Composable
private fun ConnectedScreen(peer: DiscoveredDevice) {
    CenteredColumn { Text("Connected — ready to transfer") }
}

@Composable
private fun FailedScreen(message: String, onRetry: () -> Unit) {
    CenteredColumn {
        Text(message)
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
