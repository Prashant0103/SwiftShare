package com.swiftshare.core.security

import java.security.MessageDigest

/** Shared by [EcdhPairingManager] (stores under this hash) and [AesGcmSessionCrypto] (looks up by it) — must match. */
internal fun hashOf(peerId: RotatingDeviceId): String =
    MessageDigest.getInstance("SHA-256").digest(peerId.value.toByteArray()).joinToString("") { "%02x".format(it) }
