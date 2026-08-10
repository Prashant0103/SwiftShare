package com.swiftshare.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * FR-06.1's share-sheet target: this Activity is the `ACTION_SEND`/
 * `ACTION_SEND_MULTIPLE` handler declared in the manifest. Runtime permission
 * requesting (flagged as Module 06 work in the manifest's own comment) lives
 * here, requested once up front rather than per-feature.
 */
class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer(applicationContext) }
    private val scope = MainScope()
    private val controller by lazy { ShareFlowController(container, scope) }

    private var permissionsGranted = false

    /** Set when launched from the share sheet — that flow scans as soon as permissions land. */
    private val startedFromShareSheet: Boolean
        get() = intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            // Only permissions discovery/transport actually need gate scanning — POST_NOTIFICATIONS
            // etc. are commonly denied and must not block the whole flow (was: any single denial,
            // including notifications, silently left the UI stuck on the initial spinner forever).
            permissionsGranted = discoveryPermissions().all { granted[it] == true }
            if (!permissionsGranted) {
                controller.permissionDenied("Bluetooth and location permissions are required to find nearby devices")
            } else if (startedFromShareSheet) {
                controller.startScanning()
            }
        }

    /** Launcher-started sends have no EXTRA_STREAM, so the files have to be picked here. */
    private val filePicker =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isEmpty()) {
                controller.goIdle()
                return@registerForActivityResult
            }
            scope.launch(Dispatchers.IO) {
                controller.filesToSend = uris.mapIndexedNotNull { index, uri -> copyToCache(uri, index) }
                controller.startScanning()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by controller.state.collectAsState()
            ShareFlowScreen(
                state = state,
                onSelectDevice = controller::selectDevice,
                onRetry = controller::goIdle,
                onSend = ::onSendClicked,
                onReceive = ::onReceiveClicked,
            )
        }
        permissionLauncher.launch(allRequestedPermissions())
        if (startedFromShareSheet) {
            scope.launch(Dispatchers.IO) { controller.filesToSend = extractSharedFiles() }
        }
    }

    private fun onSendClicked() {
        if (!permissionsGranted) {
            controller.permissionDenied("Bluetooth and location permissions are required to find nearby devices")
            return
        }
        filePicker.launch("*/*")
    }

    private fun onReceiveClicked() {
        if (!permissionsGranted) {
            controller.permissionDenied("Bluetooth and location permissions are required to find nearby devices")
        } else {
            controller.startReceiving()
        }
    }

    /** Permissions discovery/transport can't function without — scanning is gated on these. */
    private fun discoveryPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    private fun allRequestedPermissions(): Array<String> = buildList {
        addAll(discoveryPermissions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    /** Resolves FR-06.1's `ACTION_SEND`/`ACTION_SEND_MULTIPLE` payload into local files [TransferEngine] can read. */
    private fun extractSharedFiles(): List<File> {
        val uris = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { listOf(it) } ?: emptyList()
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
            else -> emptyList()
        }
        return uris.mapIndexed { index, uri -> copyToCache(uri, index) }.filterNotNull()
    }

    /** [index]-prefixed, basename-only filename: closes path traversal (a malicious display name
     *  like "../../x") and same-name collisions between multiple shared files in one intent. */
    private fun copyToCache(uri: Uri, index: Int): File? {
        val rawName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: uri.lastPathSegment ?: return null
        val safeName = File(rawName).name.ifBlank { "shared_file" }
        val outFile = File(cacheDir, "${index}_$safeName")
        contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        return outFile
    }
}
