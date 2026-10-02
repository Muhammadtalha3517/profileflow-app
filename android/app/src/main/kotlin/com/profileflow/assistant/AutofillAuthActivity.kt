package com.profileflow.assistant

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

/**
 * Trampoline Activity enforcing Biometric / Device Credential (PIN/Pattern) authentication
 * before any profile dataset value is released to the requesting app/Chrome.
 * 
 * Satisfies security requirement:
 * "Require biometric/PIN auth (existing local_auth) before data is returned (use Dataset authentication)."
 */
@RequiresApi(Build.VERSION_CODES.O)
class AutofillAuthActivity : FragmentActivity() {

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fieldKey = intent.getStringExtra(ProfileFlowAutofillService.EXTRA_FIELD_KEY)
        val autofillId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(ProfileFlowAutofillService.EXTRA_AUTOFILL_ID, AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(ProfileFlowAutofillService.EXTRA_AUTOFILL_ID)
        }

        if (fieldKey.isNullOrEmpty() || autofillId == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val fieldType = ProfileFlowAutofillService.FieldType.fromKey(fieldKey)
        val displayName = fieldType?.displayName ?: "Profile Data"

        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(applicationContext, "Autofill cancelled: $errString", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_CANCELED)
                finish()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                handleAuthSuccess(fieldKey, autofillId)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Biometric mismatch; prompt remains open for another try
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock ProfileFlow")
            .setSubtitle("Verify identity to autofill $displayName")
            .setDescription("Touch sensor or use your device screen lock PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun handleAuthSuccess(fieldKey: String, autofillId: AutofillId) {
        val storageBridge = AutofillStorageBridge(applicationContext)
        val fieldValue = storageBridge.getProfileField(fieldKey)

        if (fieldValue == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // Build authenticated Dataset satisfying Android Autofill API contract
        val dataset = Dataset.Builder()
            .setValue(autofillId, AutofillValue.forText(fieldValue))
            .build()

        val replyIntent = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
        }

        setResult(Activity.RESULT_OK, replyIntent)
        finish()
    }
}
