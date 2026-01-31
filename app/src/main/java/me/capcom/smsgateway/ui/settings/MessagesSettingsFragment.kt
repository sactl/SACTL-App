package me.capcom.smsgateway.ui.settings

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import me.capcom.smsgateway.R

class MessagesSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.messages_preferences, rootKey)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference.key) {
            "messages.log_lifetime_days" -> {
                (preference as EditTextPreference).setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER
                    it.setSelectAllOnFocus(true)
                    it.selectAll()
                }
            }
        }

        super.onDisplayPreferenceDialog(preference)
    }
}
