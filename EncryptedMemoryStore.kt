package com.jarvis.supreme

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * 100% On-Device Encrypted Vault backed by Redmi Note 15 5G Hardware Android Keystore (AES-256-GCM).
 * Stores:
 *  - Owner Voice Biometric Signature
 *  - Conversational Custom Rules & App Preferences (e.g., "When I say play song, open Spotify")
 *  - Voice Engine Mode ("HYBRID_AUTO", "IRON_MAN_OFFLINE", "ULTRA_REALISTIC_CLOUD")
 *  - Tuition Schedule & Homework Reminders
 *  - Reels & Shorts Tricks / Hacks Vault
 *  - User Mood History & Personal Feedback Rules
 */
class EncryptedMemoryStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "jarvis_encrypted_vault.prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var isJarvisActive: Boolean
        get() = prefs.getBoolean("jarvis_active", true)
        set(value) = prefs.edit().putBoolean("jarvis_active", value).apply()

    var isVoiceLockEnabled: Boolean
        get() = prefs.getBoolean("voice_lock_enabled", true)
        set(value) = prefs.edit().putBoolean("voice_lock_enabled", value).apply()

    var voiceMode: String
        get() = prefs.getString("jarvis_voice_mode", "HYBRID_AUTO") ?: "HYBRID_AUTO"
        set(value) = prefs.edit().putString("jarvis_voice_mode", value).apply()

    var voicePitch: Float
        get() = prefs.getFloat("jarvis_voice_pitch", 0.88f)
        set(value) = prefs.edit().putFloat("jarvis_voice_pitch", value).apply()

    var voiceRate: Float
        get() = prefs.getFloat("jarvis_voice_rate", 1.05f)
        set(value) = prefs.edit().putFloat("jarvis_voice_rate", value).apply()

    var playHudChime: Boolean
        get() = prefs.getBoolean("jarvis_hud_chime", true)
        set(value) = prefs.edit().putBoolean("jarvis_hud_chime", value).apply()

    // --- Conversational App Preferences & Custom Trigger Rules ---
    var preferredMusicApp: String
        get() = prefs.getString("preferred_music_app", "YouTube") ?: "YouTube"
        set(value) = prefs.edit().putString("preferred_music_app", value.trim()).apply()

    fun saveCustomConversationalRule(triggerPhrase: String, actionTarget: String) {
        val raw = prefs.getString("custom_user_rules", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val item = JSONObject().apply {
            put("trigger", triggerPhrase.lowercase().trim())
            put("action", actionTarget.trim())
            put("updatedAt", System.currentTimeMillis())
        }
        arr.put(item)
        prefs.edit().putString("custom_user_rules", arr.toString()).apply()
    }

    fun getCustomConversationalRules(): String {
        val raw = prefs.getString("custom_user_rules", "[]") ?: "[]"
        val arr = JSONArray(raw)
        if (arr.length() == 0) return "Preferred music app: $preferredMusicApp."
        val sb = StringBuilder("Preferred music app: $preferredMusicApp.\n")
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            sb.append("• Rule: When Sir says '${obj.optString("trigger")}' -> Do '${obj.optString("action")}'\n")
        }
        return sb.toString().trim()
    }

    fun findMatchingCustomRule(utterance: String): String? {
        val lower = utterance.lowercase()
        val raw = prefs.getString("custom_user_rules", "[]") ?: "[]"
        val arr = JSONArray(raw)
        for (i in arr.length() - 1 downTo 0) {
            val obj = arr.getJSONObject(i)
            val trig = obj.optString("trigger")
            if (trig.isNotBlank() && lower.contains(trig)) {
                return obj.optString("action")
            }
        }
        return null
    }

    // --- Voice Biometric Print ---
    fun saveOwnerVoicePrint(featureVector: FloatArray) {
        val json = JSONArray()
        featureVector.forEach { json.put(it.toDouble()) }
        prefs.edit().putString("owner_voice_print", json.toString()).apply()
    }

    fun getOwnerVoicePrint(): FloatArray? {
        val raw = prefs.getString("owner_voice_print", null) ?: return null
        val arr = JSONArray(raw)
        return FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
    }

    // --- Tuition & Homework Memory ---
    fun addTuitionOrHomework(subject: String, dayAndTime: String, homeworkNote: String) {
        val list = getTuitionsJson()
        val item = JSONObject().apply {
            put("subject", subject)
            put("dayAndTime", dayAndTime)
            put("homework", homeworkNote)
            put("createdAt", System.currentTimeMillis())
        }
        list.put(item)
        prefs.edit().putString("tuitions_homework", list.toString()).apply()
    }

    fun getTuitionsSummary(): String {
        val list = getTuitionsJson()
        if (list.length() == 0) return "No pending tuition or homework recorded."
        val sb = StringBuilder()
        for (i in 0 until list.length()) {
            val obj = list.getJSONObject(i)
            sb.append("• ${obj.optString("subject")} (${obj.optString("dayAndTime")}): Homework = ${obj.optString("homework")}\n")
        }
        return sb.toString().trim()
    }

    private fun getTuitionsJson(): JSONArray {
        val raw = prefs.getString("tuitions_homework", "[]") ?: "[]"
        return JSONArray(raw)
    }

    // --- Reels & Shorts Trick / Hack Memory Vault ---
    fun saveReelOrShortTrick(trickTitle: String, screenContext: String) {
        val raw = prefs.getString("reels_tricks_vault", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val item = JSONObject().apply {
            put("trick", trickTitle)
            put("screenContext", screenContext)
            put("timestamp", System.currentTimeMillis())
        }
        arr.put(item)
        prefs.edit().putString("reels_tricks_vault", arr.toString()).apply()
    }

    fun getSavedReelsTricks(): String {
        val raw = prefs.getString("reels_tricks_vault", "[]") ?: "[]"
        val arr = JSONArray(raw)
        if (arr.length() == 0) return "No Reels/Shorts tricks saved yet."
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            sb.append("⚡ Trick #${i + 1}: ${obj.optString("trick")} (Source: ${obj.optString("screenContext")})\n")
        }
        return sb.toString().trim()
    }

    // --- Feedback & Self-Improvement Memory ---
    fun addUserFeedbackRule(feedback: String) {
        val arr = JSONArray(prefs.getString("feedback_rules", "[]") ?: "[]")
        arr.put(feedback)
        prefs.edit().putString("feedback_rules", arr.toString()).apply()
    }

    fun getFeedbackRules(): String {
        val arr = JSONArray(prefs.getString("feedback_rules", "[]") ?: "[]")
        if (arr.length() == 0) return "Talk with crisp Iron Man precision, addressing the owner as Sir (primary) and Boss (secondary)."
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            sb.append("- ${arr.getString(i)}\n")
        }
        return sb.toString()
    }

    // --- API Keys (Encrypted locally) ---
    var apiKey: String
        get() = prefs.getString("llm_api_key", "") ?: ""
        set(value) = prefs.edit().putString("llm_api_key", value.trim()).apply()

    var elevenLabsApiKey: String
        get() = prefs.getString("elevenlabs_api_key", "") ?: ""
        set(value) = prefs.edit().putString("elevenlabs_api_key", value.trim()).apply()

    var elevenLabsVoiceId: String
        get() = prefs.getString("elevenlabs_voice_id", "JBFqnCBsd6RMkjVDRZzb") ?: "JBFqnCBsd6RMkjVDRZzb"
        set(value) = prefs.edit().putString("elevenlabs_voice_id", value.trim()).apply()
}
