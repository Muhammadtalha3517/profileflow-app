package com.profileflow.assistant

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * MainActivity bridging Flutter and Native Android Autofill APIs.
 * Uses FlutterFragmentActivity to ensure compatibility with `local_auth` and biometric prompts.
 */
class MainActivity : FlutterFragmentActivity() {

    private val CHANNEL = "com.profileflow.assistant/autofill"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "isAutofillSupported" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val autofillManager = getSystemService(AutofillManager::class.java)
                        result.success(autofillManager != null && autofillManager.isAutofillSupported)
                    } else {
                        result.success(false)
                    }
                }

                "isAutofillServiceActive" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val autofillManager = getSystemService(AutofillManager::class.java)
                        val isEnabled = autofillManager != null && autofillManager.hasEnabledAutofillServices()
                        result.success(isEnabled)
                    } else {
                        result.success(false)
                    }
                }

                "openAutofillSettings" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                data = Uri.parse("package:com.profileflow.assistant")
                            }
                            startActivity(intent)
                            result.success(true)
                        } catch (e: Exception) {
                            // Fallback to general autofill input settings
                            val fallbackIntent = Intent(Settings.ACTION_AUTOFILL_SETTINGS)
                            startActivity(fallbackIntent)
                            result.success(true)
                        }
                    } else {
                        result.error("UNSUPPORTED", "Autofill requires Android 8.0 (API 26) or higher", null)
                    }
                }

                "syncProfileToNativeStorage" -> {
                    val profileData = call.argument<Map<String, String?>>("profileData")
                    val isBiometricRequired = call.argument<Boolean>("isBiometricRequired") ?: true

                    if (profileData != null) {
                        val storageBridge = AutofillStorageBridge(applicationContext)
                        storageBridge.setBiometricRequired(isBiometricRequired)
                        for ((key, value) in profileData) {
                            storageBridge.storeProfileField(key, value)
                        }
                        result.success(true)
                    } else {
                        result.error("INVALID_ARGS", "profileData map is required", null)
                    }
                }

                "setBiometricRequirement" -> {
                    val isRequired = call.argument<Boolean>("required") ?: true
                    val storageBridge = AutofillStorageBridge(applicationContext)
                    storageBridge.setBiometricRequired(isRequired)
                    result.success(true)
                }

                else -> result.notImplemented()
            }
        }
    }
}
