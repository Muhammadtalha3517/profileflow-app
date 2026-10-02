package com.profileflow.assistant

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.util.Log
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi

/**
 * Native Android Autofill Service for ProfileFlow.
 * 
 * Features:
 * - Traverses AssistStructure from Chrome and native apps.
 * - Heuristically classifies form fields (firstName, lastName, fullName, email, password,
 *   phone, country, city, address, postalCode).
 * - Per-field Dataset creation with custom RemoteViews branding ("ProfileFlow").
 * - Gated behind Biometric/PIN authentication (via Dataset authentication and AutofillAuthActivity).
 * - Reads data safely from encrypted storage bridge without ever logging values.
 * - Strict human-in-the-loop: fills ONLY the tapped field; never auto-submits forms.
 */
@RequiresApi(Build.VERSION_CODES.O)
class ProfileFlowAutofillService : AutofillService() {

    companion object {
        private const val TAG = "ProfileFlowAutofill"
        const val EXTRA_FIELD_KEY = "com.profileflow.assistant.FIELD_KEY"
        const val EXTRA_AUTOFILL_ID = "com.profileflow.assistant.AUTOFILL_ID"
    }

    enum class FieldType(val key: String, val displayName: String) {
        FIRST_NAME("firstName", "First Name"),
        LAST_NAME("lastName", "Last Name"),
        FULL_NAME("fullName", "Full Name"),
        EMAIL("email", "Email Address"),
        PASSWORD("password", "Password"),
        PHONE("phone", "Phone Number"),
        COUNTRY("country", "Country"),
        CITY("city", "City"),
        ADDRESS("address", "Street Address"),
        POSTAL_CODE("postalCode", "Postal / ZIP Code");

        companion object {
            fun fromKey(key: String): FieldType? = entries.find { it.key == key }
        }
    }

    data class DetectedField(
        val autofillId: AutofillId,
        val type: FieldType,
        val originalHint: String? = null
    )

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        // Parse AssistStructure to detect supported input fields
        val matchedFields = mutableListOf<DetectedField>()
        val windowNodesCount = structure.windowNodeCount

        for (i in 0 until windowNodesCount) {
            val windowNode = structure.getWindowNodeAt(i)
            traverseViewNode(windowNode.rootViewNode, matchedFields)
        }

        // If no fields matched or cancelled early
        if (matchedFields.isEmpty() || cancellationSignal.isCanceled) {
            Log.d(TAG, "AssistStructure parsed: 0 recognizable fields found.")
            callback.onSuccess(null)
            return
        }

        Log.d(TAG, "AssistStructure parsed successfully: ${matchedFields.size} fields identified.")

        val responseBuilder = FillResponse.Builder()
        val storageBridge = AutofillStorageBridge(applicationContext)
        val isBiometricEnabled = storageBridge.isBiometricRequired()

        // Build a dedicated Dataset per matched field so user selects exactly what to fill
        for (field in matchedFields) {
            val rawValue = storageBridge.getProfileField(field.type.key)
            if (rawValue.isNullOrBlank()) {
                continue // Skip empty fields from user profile
            }

            val datasetBuilder = Dataset.Builder()
            val presentation = createRemoteViews(field.type.displayName, "Tap to fill from ProfileFlow")

            if (isBiometricEnabled) {
                // Dataset-level biometric/PIN authentication gate
                val authIntent = Intent(this, AutofillAuthActivity::class.java).apply {
                    putExtra(EXTRA_FIELD_KEY, field.type.key)
                    putExtra(EXTRA_AUTOFILL_ID, field.autofillId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                val pendingIntent = PendingIntent.getActivity(
                    this,
                    field.type.key.hashCode(),
                    authIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                datasetBuilder.setValue(
                    field.autofillId,
                    null, // No plaintext value supplied until biometric auth completes
                    presentation
                )
                datasetBuilder.setAuthentication(pendingIntent.intentSender)
            } else {
                // Direct fill with RemoteViews presentation
                datasetBuilder.setValue(
                    field.autofillId,
                    AutofillValue.forText(rawValue),
                    presentation
                )
            }

            responseBuilder.addDataset(datasetBuilder.build())
        }

        val fillResponse = responseBuilder.build()
        callback.onSuccess(fillResponse)
    }

    override fun onSaveRequest(request: android.service.autofill.SaveRequest, callback: android.service.autofill.SaveCallback) {
        // Zero auto-save without explicit user request.
        // User manages their profile strictly in ProfileFlow Flutter vault.
        callback.onSuccess()
    }

    /**
     * Recursive ViewNode traversal to inspect inputs and HTML form elements from Chrome.
     */
    private fun traverseViewNode(node: AssistStructure.ViewNode?, result: MutableList<DetectedField>) {
        if (node == null) return

        val autofillId = node.autofillId
        if (autofillId != null && (node.className?.contains("EditText", ignoreCase = true) == true ||
                    node.htmlInfo != null ||
                    node.autofillType == View.AUTOFILL_TYPE_TEXT ||
                    node.inputType != 0)) {
            val classified = classifyNode(node)
            if (classified != null) {
                result.add(DetectedField(autofillId, classified, node.hint))
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            traverseViewNode(node.getChildAt(i), result)
        }
    }

    /**
     * Multi-signal heuristic classifier:
     * 1. autofillHints (W3C / Android standard hints)
     * 2. idEntry (HTML tag id/name or resource id)
     * 3. hint / placeholder text
     * 4. inputType flags
     * 5. node text or label
     */
    private fun classifyNode(node: AssistStructure.ViewNode): FieldType? {
        val hints = node.autofillHints?.toList() ?: emptyList()
        val idEntry = node.idEntry?.lowercase() ?: ""
        val hintText = node.hint?.lowercase() ?: ""
        val textContent = node.text?.toString()?.lowercase() ?: ""
        val inputType = node.inputType
        val htmlTag = node.htmlInfo?.tag?.lowercase() ?: ""

        val combinedTokens = "$idEntry $hintText $textContent"

        // 1. Check autofillHints first (highest confidence)
        for (hint in hints) {
            when (hint.lowercase()) {
                View.AUTOFILL_HINT_EMAIL_ADDRESS, "email" -> return FieldType.EMAIL
                View.AUTOFILL_HINT_PHONE, "phone", "tel" -> return FieldType.PHONE
                View.AUTOFILL_HINT_PASSWORD, "password", "new-password", "current-password" -> return FieldType.PASSWORD
                View.AUTOFILL_HINT_POSTAL_CODE, "postal-code", "zip-code" -> return FieldType.POSTAL_CODE
                View.AUTOFILL_HINT_POSTAL_ADDRESS, "street-address", "address" -> return FieldType.ADDRESS
                "given-name", "first-name" -> return FieldType.FIRST_NAME
                "family-name", "last-name" -> return FieldType.LAST_NAME
                View.AUTOFILL_HINT_NAME, "name" -> return FieldType.FULL_NAME
            }
        }

        // 2. Check inputType variations
        val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
        val inputVariation = inputType and android.text.InputType.TYPE_MASK_VARIATION

        if (inputClass == android.text.InputType.TYPE_CLASS_PHONE) {
            return FieldType.PHONE
        }
        if (inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) {
            return FieldType.EMAIL
        }
        if (inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
            return FieldType.PASSWORD
        }
        if (inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS) {
            return FieldType.ADDRESS
        }

        // 3. String matching on idEntry, hint, placeholder
        return when {
            Regex("(\\bfirst.*name\\b|\\bfname\\b|\\bgiven.*name\\b|\\bfirstname\\b)").containsMatchIn(combinedTokens) ->
                FieldType.FIRST_NAME

            Regex("(\\blast.*name\\b|\\blname\\b|\\bsurname\\b|\\bfamily.*name\\b|\\blastname\\b)").containsMatchIn(combinedTokens) ->
                FieldType.LAST_NAME

            Regex("(\\bfull.*name\\b|\\byour.*name\\b|\\buser.*name\\b|\\bdisplay.*name\\b)").containsMatchIn(combinedTokens) ->
                FieldType.FULL_NAME

            Regex("(\\bemail\\b|\\be-mail\\b|\\bmail\\b)").containsMatchIn(combinedTokens) ->
                FieldType.EMAIL

            Regex("(\\bpassword\\b|\\bpwd\\b|\\bpasscode\\b)").containsMatchIn(combinedTokens) ->
                FieldType.PASSWORD

            Regex("(\\bphone\\b|\\bmobile\\b|\\bcell\\b|\\btelephone\\b|\\btel\\b)").containsMatchIn(combinedTokens) ->
                FieldType.PHONE

            Regex("(\\bpostal.*code\\b|\\bzip\\b|\\bzipcode\\b|\\bpostcode\\b|\\bpincode\\b)").containsMatchIn(combinedTokens) ->
                FieldType.POSTAL_CODE

            Regex("(\\bcity\\b|\\btown\\b|\\bmunicipality\\b)").containsMatchIn(combinedTokens) ->
                FieldType.CITY

            Regex("(\\bcountry\\b|\\bnation\\b|\\bregion\\b)").containsMatchIn(combinedTokens) ->
                FieldType.COUNTRY

            Regex("(\\baddress\\b|\\bstreet\\b|\\baddr\\b|\\bline1\\b)").containsMatchIn(combinedTokens) ->
                FieldType.ADDRESS

            else -> null
        }
    }

    /**
     * Constructs a clean RemoteViews dataset presentation adhering to the "ProfileFlow" label requirement.
     */
    private fun createRemoteViews(fieldLabel: String, subtext: String): RemoteViews {
        val views = RemoteViews(packageName, R.layout.autofill_dataset_item)
        views.setTextViewText(R.id.autofill_title, "ProfileFlow • $fieldLabel")
        views.setTextViewText(R.id.autofill_subtext, subtext)
        views.setImageViewResource(R.id.autofill_icon, R.drawable.ic_profileflow_logo)
        return views
    }
}
