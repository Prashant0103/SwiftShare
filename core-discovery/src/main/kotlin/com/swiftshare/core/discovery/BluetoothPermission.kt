package com.swiftshare.core.discovery

/**
 * BLUETOOTH_SCAN/BLUETOOTH_ADVERTISE are revocable runtime permissions —
 * requesting them (with rationale) is Module 06's job (FR-05.6). This module
 * just fails clearly instead of crashing with a bare SecurityException if
 * they weren't granted before a call reached here.
 */
internal inline fun requiringBluetoothPermission(action: String, block: () -> Unit) {
    try {
        block()
    } catch (e: SecurityException) {
        throw IllegalStateException("$action requires a Bluetooth permission that hasn't been granted", e)
    }
}
