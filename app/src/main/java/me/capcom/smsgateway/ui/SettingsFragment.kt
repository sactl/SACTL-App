package me.capcom.smsgateway.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import me.capcom.smsgateway.BuildConfig
import me.capcom.smsgateway.R
import me.capcom.smsgateway.modules.encryption.CryptoManager
import org.koin.android.ext.android.inject

class SettingsFragment : PreferenceFragmentCompat() {

    private val cryptoManager: CryptoManager by inject()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        findPreference<Preference>("transient.app_version")?.summary =
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            findPreference<Preference>("system")?.isEnabled = false
            findPreference<Preference>("system.disable_battery_optimizations")?.summary =
                getString(R.string.battery_optimization_is_not_supported_on_this_device)
        } else {
            findPreference<Preference>("system.disable_battery_optimizations")?.summary =
                if (isIgnoringBatteryOptimizations()) getString(R.string.disabled) else getString(R.string.enabled)
        }

        // Update public key status
        updatePublicKeySummary()

        // Set up public key preference change listener
        findPreference<EditTextPreference>("encryption.public_key_pem")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                val pemKey = newValue as? String
                if (pemKey.isNullOrBlank()) {
                    // Allow clearing the key
                    updatePublicKeySummary()
                    true
                } else if (cryptoManager.validatePublicKey(pemKey)) {
                    updatePublicKeySummary()
                    true
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.encryption_public_key_invalid),
                        Toast.LENGTH_LONG
                    ).show()
                    false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePublicKeySummary()
    }

    private fun updatePublicKeySummary() {
        findPreference<EditTextPreference>("encryption.public_key_pem")?.apply {
            summary = when {
                cryptoManager.isConfigured() -> getString(R.string.encryption_public_key_configured)
                else -> getString(R.string.encryption_public_key_not_configured)
            }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference.key == "encryption.passphrase") {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                it.setSelectAllOnFocus(true)
                it.selectAll()
            }
        }

        if (preference.key == "encryption.public_key_pem") {
            (preference as EditTextPreference).setOnBindEditTextListener {
                // Multiline input for PEM key
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                it.minLines = 5
                it.maxLines = 15
                it.setHorizontallyScrolling(false)
                it.setSelectAllOnFocus(true)
            }
        }

        if (preference.key == "ping.interval_seconds"
            || preference.key == "logs.lifetime_days"
        ) {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
                it.setSelectAllOnFocus(true)
                it.selectAll()
            }
        }

        super.onDisplayPreferenceDialog(preference)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "system.disable_battery_optimizations") {
            requestIgnoreBatteryOptimizations()
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(
                requireContext(),
                getString(R.string.battery_optimization_is_not_supported_on_this_device),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (isIgnoringBatteryOptimizations()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.battery_optimization_already_disabled),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:${requireContext().packageName}")
        startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}
