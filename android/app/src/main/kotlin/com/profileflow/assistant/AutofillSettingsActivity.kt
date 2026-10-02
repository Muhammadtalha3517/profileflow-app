package com.profileflow.assistant

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Settings Activity launched from Android System Autofill Settings (via autofill_service_config.xml).
 */
class AutofillSettingsActivity : AppCompatActivity() {

    private lateinit var storageBridge: AutofillStorageBridge
    private lateinit var switchBiometric: Switch
    private lateinit var tvStatus: TextView
    private lateinit var btnChangeService: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_autofill_settings)

        storageBridge = AutofillStorageBridge(applicationContext)

        tvStatus = findViewById(R.id.tv_autofill_status)
        switchBiometric = findViewById(R.id.switch_biometric)
        btnChangeService = findViewById(R.id.btn_open_system_settings)

        switchBiometric.isChecked = storageBridge.isBiometricRequired()
        switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            storageBridge.setBiometricRequired(isChecked)
        }

        btnChangeService.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                    data = Uri.parse("package:com.profileflow.assistant")
                }
                startActivity(intent)
            }
        }

        updateStatusText()
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()
    }

    private fun updateStatusText() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val autofillManager = getSystemService(AutofillManager::class.java)
            if (autofillManager != null && autofillManager.hasEnabledAutofillServices()) {
                tvStatus.text = "ProfileFlow is currently the Active Autofill Provider"
                tvStatus.setTextColor(0xFF0F9D58.toInt()) // Green
            } else {
                tvStatus.text = "Not selected as active provider. Tap below to enable."
                tvStatus.setTextColor(0xFFD93025.toInt()) // Red
            }
        }
    }
}
