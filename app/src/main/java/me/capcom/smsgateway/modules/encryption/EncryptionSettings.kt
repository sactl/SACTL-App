package me.capcom.smsgateway.modules.encryption

import me.capcom.smsgateway.modules.settings.Exporter
import me.capcom.smsgateway.modules.settings.Importer
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class EncryptionSettings(
    private val storage: KeyValueStorage,
) : Importer, Exporter {
    val passphrase: String?
        get() = storage.get<String>(PASSPHRASE)

    /**
     * RSA Public Key in PEM format for encrypting outbound webhook payloads.
     * Used by CryptoManager for E2E encryption before forwarding SMS to webhooks.
     */
    var publicKeyPem: String?
        get() = storage.get<String>(PUBLIC_KEY_PEM)
        set(value) = storage.set(PUBLIC_KEY_PEM, value)

    private var version: Int
        get() = storage.get<Int>(VERSION) ?: 0
        set(value) = storage.set(VERSION, value)

    init {
        migrate()
    }

    private fun migrate() {
        if (version == VERSION_CODE) {
            return
        }

        if (version < 1) {
            passphrase?.let {
                storage.set(PASSPHRASE, it)
            }
        }

        version = VERSION_CODE
    }

    companion object {
        private const val VERSION_CODE = 1

        private const val PASSPHRASE = "passphrase"
        private const val PUBLIC_KEY_PEM = "public_key_pem"

        private const val VERSION = "version"
    }

    override fun export(): Map<String, *> {
        return mapOf(
            PUBLIC_KEY_PEM to publicKeyPem,
        )
    }

    override fun import(data: Map<String, *>): Boolean {
        return data.map {
            when (it.key) {
                PASSPHRASE -> {
                    val newValue = it.value?.toString()
                    val changed = passphrase != newValue
                    storage.set(it.key, newValue)
                    changed
                }

                PUBLIC_KEY_PEM -> {
                    val newValue = it.value?.toString()
                    val changed = publicKeyPem != newValue
                    storage.set(it.key, newValue)
                    changed
                }

                else -> false
            }
        }.any { it }
    }
}
