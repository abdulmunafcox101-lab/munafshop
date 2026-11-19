package com.example.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.json.JSONArray
import org.json.JSONObject

data class ParsedFirebaseConfig(
    val projectId: String,
    val projectNumber: String,
    val storageBucket: String,
    val applicationId: String,
    val apiKey: String,
    val webClientId: String?
)

object FirebaseRuntimeConfig {
    private const val TAG = "FirebaseRuntimeCfg"
    private const val PREFS_NAME = "firebase_runtime_config"
    private const val KEY_RAW_JSON = "raw_google_services_json"
    private const val KEY_WEB_CLIENT_ID = "default_web_client_id"

    fun ensureInitialized(context: Context): Boolean {
        val appContext = context.applicationContext
        if (FirebaseApp.getApps(appContext).isNotEmpty()) return true

        val defaultInit = runCatching { FirebaseApp.initializeApp(appContext) != null }.getOrDefault(false)
        if (defaultInit) return true

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedJson = prefs.getString(KEY_RAW_JSON, null) ?: return false
        return initializeFromJson(appContext, savedJson).isSuccess
    }

    fun getWebClientId(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WEB_CLIENT_ID, null)?.takeIf { it.isNotBlank() }
    }

    fun saveAndInitialize(context: Context, rawJson: String): Result<ParsedFirebaseConfig> {
        val appContext = context.applicationContext
        val result = initializeFromJson(appContext, rawJson)
        result.onSuccess { parsed ->
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RAW_JSON, rawJson.trim())
                .putString(KEY_WEB_CLIENT_ID, parsed.webClientId.orEmpty())
                .apply()
        }
        return result
    }

    private fun initializeFromJson(
        appContext: Context,
        rawJson: String
    ): Result<ParsedFirebaseConfig> {
        return parseAndValidate(rawJson, appContext.packageName).mapCatching { parsed ->
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                val optionsBuilder = FirebaseOptions.Builder()
                    .setProjectId(parsed.projectId)
                    .setApplicationId(parsed.applicationId)
                    .setApiKey(parsed.apiKey)
                if (parsed.projectNumber.isNotBlank()) {
                    optionsBuilder.setGcmSenderId(parsed.projectNumber)
                }
                if (parsed.storageBucket.isNotBlank()) {
                    optionsBuilder.setStorageBucket(parsed.storageBucket)
                }
                FirebaseApp.initializeApp(appContext, optionsBuilder.build())
            }
            parsed
        }.onFailure { err ->
            Log.e(TAG, "Failed to initialize Firebase from runtime JSON", err)
        }
    }

    fun parseAndValidate(rawJson: String, expectedPackage: String): Result<ParsedFirebaseConfig> = runCatching {
        val trimmed = rawJson.trim()
        require(trimmed.startsWith("{")) {
            "Invalid JSON format. Please paste or select a valid google-services.json file."
        }

        val root = JSONObject(trimmed)
        val projectInfo = root.optJSONObject("project_info")
            ?: throw IllegalArgumentException("Missing project_info in google-services.json")

        val projectId = projectInfo.optString("project_id").trim()
        val projectNumber = projectInfo.optString("project_number").trim()
        val storageBucket = projectInfo.optString("storage_bucket").trim()
        require(projectId.isNotBlank()) { "Missing project_id in google-services.json" }

        val clients = root.optJSONArray("client")
            ?: throw IllegalArgumentException("Missing client array in google-services.json")
        require(clients.length() > 0) { "No Android client entries found in google-services.json" }

        val matchingClient = findMatchingClient(clients, expectedPackage)
            ?: throw IllegalArgumentException(
                "This google-services.json does not match package $expectedPackage"
            )

        val clientInfo = matchingClient.optJSONObject("client_info")
            ?: throw IllegalArgumentException("Missing client_info in google-services.json")
        val applicationId = clientInfo.optString("mobilesdk_app_id").trim()
        require(applicationId.isNotBlank()) { "Missing mobilesdk_app_id in google-services.json" }

        val apiKeys = matchingClient.optJSONArray("api_key")
        val apiKey = apiKeys?.optJSONObject(0)?.optString("current_key")?.trim().orEmpty()
        require(apiKey.isNotBlank()) { "Missing current_key in google-services.json" }

        val webClientId = extractWebClientId(matchingClient)

        ParsedFirebaseConfig(
            projectId = projectId,
            projectNumber = projectNumber,
            storageBucket = storageBucket,
            applicationId = applicationId,
            apiKey = apiKey,
            webClientId = webClientId
        )
    }

    private fun findMatchingClient(clients: JSONArray, expectedPackage: String): JSONObject? {
        for (i in 0 until clients.length()) {
            val client = clients.optJSONObject(i) ?: continue
            val pkg = client.optJSONObject("client_info")
                ?.optJSONObject("android_client_info")
                ?.optString("package_name")
                ?.trim()
            if (pkg == expectedPackage) {
                return client
            }
        }
        return null
    }

    private fun extractWebClientId(clientObj: JSONObject): String? {
        val oauthClients = clientObj.optJSONArray("oauth_client")
        if (oauthClients != null) {
            for (i in 0 until oauthClients.length()) {
                val entry = oauthClients.optJSONObject(i) ?: continue
                if (entry.optInt("client_type") == 3) {
                    val id = entry.optString("client_id").trim()
                    if (id.isNotBlank()) return id
                }
            }
        }
        val otherClients = clientObj.optJSONObject("services")
            ?.optJSONObject("appinvite_service")
            ?.optJSONArray("other_platform_oauth_client")
        if (otherClients != null) {
            for (i in 0 until otherClients.length()) {
                val entry = otherClients.optJSONObject(i) ?: continue
                if (entry.optInt("client_type") == 3) {
                    val id = entry.optString("client_id").trim()
                    if (id.isNotBlank()) return id
                }
            }
        }
        return null
    }
}
