package com.jarvis.supreme

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * JARVIS Core Intelligence & Multi-Task Chain Engine.
 *
 * CRISP IRON MAN SPEECH PROTOCOL:
 *  - Short, sharp, cinematic responses ("Understood, Sir. Standby mode on.", "Right away, Sir.", "Ok, Boss.").
 *  - Addresses the Owner as "Sir" (primary) and occasionally "Boss".
 *  - Understands the Owner's English, বাংলা, and casual "tui/tumi" Banglish ("chup kor", "gaan palta", "wifi on kor"),
 *    while replying with clean, punchy Iron Man precision (using respectful "apni/tumi" when speaking Bengali/Banglish).
 */
class JarvisBrain(
    private val context: Context,
    private val memoryStore: EncryptedMemoryStore,
    private val hardwareController: JarvisHardwareController
) {
    private val creativeStudio = JarvisCreativeStudio(context, memoryStore)
    private val docLectureEngine = JarvisDocumentAndLectureEngine(context, memoryStore)
    private val travelNavigator = JarvisTravelAndTrainNavigator(context, memoryStore, hardwareController)
    private val offlineLocalLlm = JarvisOfflineLocalLlmEngine(memoryStore)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun detectLanguageStyle(text: String): String {
        val hasBengaliScript = text.any { it in '\u0980'..'\u09FF' }
        if (hasBengaliScript) return "PURE_BANGLA"

        val lower = text.lowercase()
        val banglishMarkers = listOf(
            "chup kor", "chup koro", "bondho kor", "bondho koro", "ki korchis", "korchis",
            "bolchis", "shon", "dekh", "bujhli", "paltiye de", "chalie de", "khol",
            "lagiye de", "uthe gechi", "banie de", "kemon", "acho", "korbo", "korchi",
            "kalke", "ajke", "bhalo", "bolchi", "kothay", "khabar", "ghum", "porashona"
        )
        if (banglishMarkers.any { lower.contains(it) }) return "BANGLISH"
        return "ENGLISH"
    }

    fun buildStartupWelcomeMessage(batteryPct: Int): String {
        val tuitions = memoryStore.getTuitionsSummary()
        return "Welcome back, Sir. All systems online. Battery at $batteryPct%. Schedule: $tuitions. Ready for your command, Sir."
    }

    /**
     * Master Entry Point: Detects if the Owner gave MULTIPLE tasks at once
     * and executes every sub-task with a crisp summary!
     */
    suspend fun processOwnerCommand(
        utterance: String,
        detectedMood: String = "Calm / Normal",
        activeForegroundApp: String = ""
    ): String = withContext(Dispatchers.IO) {
        val subTasks = splitIntoMultiTaskQueue(utterance)

        if (subTasks.size > 1) {
            val results = mutableListOf<String>()
            for ((index, task) in subTasks.withIndex()) {
                val res = executeSingleTask(task, detectedMood, activeForegroundApp, isPartOfChain = true)
                results.add("${index + 1}. $res")
            }
            return@withContext "Done, Sir. All ${subTasks.size} tasks completed:\n" +
                results.joinToString("\n")
        }

        executeSingleTask(utterance, detectedMood, activeForegroundApp, isPartOfChain = false)
    }

    private fun splitIntoMultiTaskQueue(utterance: String): List<String> {
        val normalized = utterance
            .replace(Regex("\\b(and then|tarpor|tarpore|ebong|তারপর|এবং)\\b", RegexOption.IGNORE_CASE), " ||| ")
            .replace(Regex(",\\s*(ar|and|আর)?\\s*", RegexOption.IGNORE_CASE), " ||| ")
            .replace(Regex("\\b(ar|and|আর)\\s+(?=(turn|open|set|make|generate|create|wifi|bluetooth|hotspot|data|alarm|gaan|chobi|video|pdf|remember|mone|play|pause|change|next|click|take))", RegexOption.IGNORE_CASE), " ||| ")

        val parts = normalized.split("|||").map { it.trim() }.filter { it.length >= 3 }
        return if (parts.size >= 2) parts else listOf(utterance)
    }

    private suspend fun executeSingleTask(
        utterance: String,
        detectedMood: String,
        activeForegroundApp: String,
        isPartOfChain: Boolean
    ): String {
        val lower = utterance.lowercase().trim()

                // 0.1 Check Conversational Memory Update Rules (e.g., "Jarvis if I tell to play song open Spotify")
        if ((lower.contains("if i tell") || lower.contains("if i told") || lower.contains("when i say") || lower.contains("jodi boli")) &&
            (lower.contains("open ") || lower.contains("use ") || lower.contains("khol"))
        ) {
            val chosenApp = utterance.substringAfterLast("open ", "").substringAfterLast("use ", "").substringAfterLast("khol ", "").trim()
            if (lower.contains("song") || lower.contains("music") || lower.contains("gaan")) {
                val appClean = chosenApp.ifBlank { "Spotify" }
                memoryStore.preferredMusicApp = appClean
                memoryStore.saveCustomConversationalRule("play song", "open $appClean")
                return "Memory updated, Sir. Whenever you say play song, I will open $appClean."
            } else {
                memoryStore.saveCustomConversationalRule(utterance, chosenApp)
                return "Memory updated, Sir. Custom rule locked in encrypted storage."
            }
        }

        // 0.2 Check Phone Switch Off / Shut Down / Reboot / Lock Screen
        when {
            lower.contains("switch off my phone") || lower.contains("shut down my phone") ||
                lower.contains("shutdown phone") || lower.contains("power off phone") ||
                lower.contains("phone switch off kor") || lower.contains("phone bondho kor") -> {
                return hardwareController.switchOffOrRebootPhone(reboot = false)
            }
            lower.contains("reboot phone") || lower.contains("restart my phone") || lower.contains("phone restart kor") -> {
                return hardwareController.switchOffOrRebootPhone(reboot = true)
            }
            lower.contains("lock screen") || lower.contains("lock my phone") || lower.contains("screen lock kor") -> {
                return hardwareController.lockScreen()
            }
        }

        // 0.3 Check Unknown Caller / Number Lookup
        if ((lower.contains("unknown number") || lower.contains("who called") || lower.contains("caller info") ||
                lower.contains("number detail") || lower.contains("ke call korechilo") || lower.contains("number ta kar"))
        ) {
            return hardwareController.lookupUnknownCallerDetails(utterance)
        }

                // 0.35 Live Call Answer/Reject/AI-Talk, Missed Call Callback, Flashlight Torch, Volume & Screenshot
        when {
            lower.contains("reject call") || lower.contains("cut call") || lower.contains("end call") || lower.contains("call kete de") -> {
                return hardwareController.answerOrRejectCallAndTalk(reject = true)
            }
            lower.contains("answer call") || lower.contains("receive call") || lower.contains("pick up call") || lower.contains("call dhor") -> {
                val msgForCaller = if (lower.contains("tell ") || lower.contains("bol")) {
                    utterance.substringAfter("tell ", "").substringAfter("bol ", "").trim()
                } else ""
                return hardwareController.answerOrRejectCallAndTalk(reject = false, messageToSpeakToCaller = msgForCaller)
            }
            lower.contains("call back") || lower.contains("callback") || lower.startsWith("call ") || lower.endsWith(" ke call kor") -> {
                return hardwareController.callBackLastMissedOrNamedContact(utterance)
            }
            lower.contains("flashlight on") || lower.contains("torch on") || lower.contains("light on kor") || lower.contains("torch jala") -> {
                return hardwareController.toggleFlashlightTorch(true)
            }
            lower.contains("flashlight off") || lower.contains("torch off") || lower.contains("light bondho kor") || lower.contains("torch off kor") -> {
                return hardwareController.toggleFlashlightTorch(false)
            }
            lower.contains("screenshot") || lower.contains("screen shot") -> {
                return hardwareController.captureScreenScreenshot()
            }
            lower.contains("volume") && Regex("(\\d{1,3})").containsMatchIn(lower) -> {
                val pct = Regex("(\\d{1,3})").find(lower)?.value?.toIntOrNull() ?: 80
                return hardwareController.setVolumePercentage(pct)
            }
        }

        // 0.4 Check Smart Travel, Maps Distance, Vehicle Advisor & Train + Station + Toto/Auto/Taxi Navigator
        if (lower.contains("want to go to") || lower.contains("going to ") || lower.contains("train timing") ||
            lower.contains("nearby station") || lower.contains("reaching station") || lower.contains("how many stations") ||
            lower.contains("which route") || lower.contains("toto") || lower.contains("jete chai") ||
            (lower.contains("train") && (lower.contains("station") || lower.contains("time") || lower.contains("route")))
        ) {
            return travelNavigator.planTripAndTrainIntelligence(utterance)
        }

        // 0. Safe Pro Gaming Booster (Free Fire / BGMI)
        if (lower.contains("gaming mode") || lower.contains("free fire") || lower.contains("bgmi") || lower.contains("game boost")) {
            hardwareController.controlMedia("PAUSE")
            return "Understood, Sir. Pro Gaming Mode on. Footstep audio maximized."
        }

                        // 0.45 WhatsApp Direct Text, Specific Photo & Specific PDF Sending (Hands-free with Auto-Data + Auto-Send Click!)
        if (lower.contains("whatsapp") || (lower.contains("send ") && (lower.contains("photo") || lower.contains("pdf") || lower.contains("chobi")))) {
            // Case A: Sending a specific PDF or Photo on WhatsApp
            if (lower.contains("pdf") || lower.contains("photo") || lower.contains("image") || lower.contains("chobi")) {
                val isPdf = lower.contains("pdf")
                // Extract recipient name after "to " or after "send "
                val contactName = when {
                    lower.contains(" to ") -> utterance.substringAfterLast(" to ").replace("on whatsapp", "", ignoreCase = true).replace("in whatsapp", "", ignoreCase = true).trim()
                    lower.contains(" ke ") -> utterance.substringBefore(" ke ").substringAfterLast(" ").trim()
                    else -> utterance.replace(Regex("(?i)(jarvis|send|specific|septic|photo|image|pdf|chobi|on whatsapp|in whatsapp|whatsapp|pathiye de|patha)"), "").trim()
                }
                val keyword = utterance
                    .replace(Regex("(?i)(jarvis|send|specific|septic|photo|image|pdf|chobi|to $contactName|on whatsapp|in whatsapp|whatsapp|pathiye de|patha)"), "")
                    .trim()
                return hardwareController.sendSpecificPhotoOrPdfOnWhatsApp(contactName, keyword, isPdf)
            }

            // Case B: Sending a WhatsApp text message (e.g., "send ram hiii in WhatsApp" or "whatsapp-e ram ke bol hi")
            if (lower.contains("send ") || lower.contains("message ") || lower.contains("bol") || lower.contains("patha")) {
                val cleaned = utterance
                    .replace(Regex("(?i)^(jarvis\\s*)?(send|message)\\s+"), "")
                    .replace(Regex("(?i)\\s+(in|on)\\s+whatsapp.*$"), "")
                    .trim()
                val words = cleaned.split(" ")
                if (words.size >= 2) {
                    val contactName = words.first()
                    val msgBody = words.drop(1).joinToString(" ").removePrefix("that ").trim()
                    return hardwareController.sendWhatsAppTextToContact(contactName, msgBody)
                }
            }
        }

        // 0.5 YouTube Search + Auto-Play, Song/Video Download & In-App Write/Send (Auto-enables Mobile Data if OFF!)
        when {
            (lower.contains("youtube") && (lower.contains("search") || lower.contains("play") || lower.contains("arijit") || lower.contains("track") || lower.contains("song") || lower.contains("gaan"))) ||
                (lower.contains("play") && (lower.contains("arijit") || lower.contains("sad track") || lower.contains("long track"))) -> {
                return hardwareController.searchAndPlayOnYouTube(utterance)
            }
            lower.contains("download") && (lower.contains("song") || lower.contains("video") || lower.contains("track") || lower.contains("gaan") || lower.contains("youtube")) -> {
                return hardwareController.downloadSongOrVideo(utterance)
            }
            lower.startsWith("open ") && lower.contains(" and write ") -> {
                val afterOpen = utterance.substringAfter("open ", "").substringAfter("Open ", "")
                val appTarget = afterOpen.substringBefore(" and write").substringBefore(" And Write").trim()
                val textPayload = afterOpen.substringAfter("write ").substringAfter("Write ").replace(" and send", "", ignoreCase = true).trim()
                return hardwareController.openAppAndWriteAndSend(appTarget, textPayload, autoSend = lower.contains("send"))
            }
        }

        // 1. PDF Creation, Full Lecture Watcher, Reels Trick Vault, DIY Blueprint & Jarvis Lens
        when {
            lower.contains("jarvis lens") || lower.contains("open lens") || lower.contains("camera diye dekh") -> {
                val lensIntent = Intent(context, JarvisLensActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(lensIntent)
                return "Right away, Sir. Jarvis Lens online."
            }
            lower.contains("trick") && (lower.contains("remember") || lower.contains("save") || lower.contains("mone rakh")) -> {
                val screenSnap = JarvisAccessibilityService.latestScreenTextSnapshot.ifBlank { activeForegroundApp }
                memoryStore.saveReelOrShortTrick(utterance, screenSnap)
                return "Locked in Trick Vault, Sir."
            }
            lower.contains("show tricks") || lower.contains("saved tricks") || lower.contains("trick gulo bol") -> {
                return "Sir, saved tricks:\n${memoryStore.getSavedReelsTricks()}"
            }
            lower.contains("lecture") && (lower.contains("note") || lower.contains("pdf") || lower.contains("watch")) -> {
                val screenTopic = JarvisAccessibilityService.latestScreenTextSnapshot.ifBlank { utterance }
                hardwareController.autoEnableNetworkIfOffline()
                val (notes, pdfFile) = docLectureEngine.watchFullLectureAndGeneratePdf("$utterance | On-Screen Lecture: $screenTopic")
                return "Lecture analyzed, Sir. Decorated PDF saved (${pdfFile?.name}).\n${notes.take(220)}..."
            }
            lower.contains("blueprint") || lower.contains("equipment") ||
                (lower.contains("how to make") && lower.contains("attach")) || lower.contains("banate ki ki lagbe") -> {
                hardwareController.autoEnableNetworkIfOffline()
                val (_, pdfFile) = docLectureEngine.buildDiyProjectBlueprint(utterance)
                return "Blueprint and equipment list ready, Sir (${pdfFile?.name})."
            }
            lower.contains("make pdf") || lower.contains("create pdf") || lower.contains("pdf banie de") || lower.contains("decorated pdf") -> {
                val (_, pdfFile) = docLectureEngine.watchFullLectureAndGeneratePdf(utterance)
                return "Decorated PDF created, Sir (${pdfFile?.name})."
            }
        }

        // 2. Pro AI Image & 1080p Video Studio Commands
        when {
            lower.contains("generate image") || lower.contains("create image") ||
                lower.contains("draw ") || lower.contains("chobi banie de") || lower.contains("image banie de") -> {
                val modelStyle = when {
                    lower.contains("anime") -> "FLUX_ANIME"
                    lower.contains("3d") || lower.contains("pixar") -> "FLUX_3D"
                    lower.contains("cyberpunk") -> "CYBERPUNK_ART"
                    else -> "FLUX_PRO"
                }
                val ratio = when {
                    lower.contains("reel") || lower.contains("short") || lower.contains("wallpaper") || lower.contains("portrait") -> "9:16"
                    lower.contains("youtube") || lower.contains("landscape") -> "16:9"
                    else -> "1:1"
                }
                hardwareController.autoEnableNetworkIfOffline()
                val bmp = creativeStudio.generateProAiImage(utterance, modelStyle, ratio)
                return if (bmp != null) {
                    "Image generated and saved to Gallery, Sir."
                } else {
                    "Connection offline, Sir. Unable to generate image."
                }
            }
            lower.contains("make video") || lower.contains("generate video") ||
                lower.contains("create video") || lower.contains("video banie de") -> {
                hardwareController.autoEnableNetworkIfOffline()
                val videoFile = creativeStudio.generateMultiSceneFullHdVideo(utterance, "FLUX_PRO")
                return if (videoFile != null) {
                    "1080p video rendered and saved, Boss (${videoFile.name})."
                } else {
                    "Video render failed, Sir."
                }
            }
        }

        // 3. Crisp Hardware & System Commands
        when {
            lower.contains("turn off jarvis") || lower.contains("jarvis sleep") ||
                lower.contains("jarvis ghumiye por") || lower.contains("jarvis off ho") -> {
                memoryStore.isJarvisActive = false
                return "Understood, Sir. Standby mode on."
            }
            lower.contains("turn on jarvis") || (lower.contains("hey jarvis") && lower.length <= 14) -> {
                memoryStore.isJarvisActive = true
                return "Online and ready, Sir."
            }
            lower.contains("wifi on") || lower.contains("turn on wifi") || lower.contains("wifi on kor") || lower.contains("wifi chala") -> {
                hardwareController.toggleWifi(true)
                return "Right away, Sir. Wi-Fi on."
            }
            lower.contains("wifi off") || lower.contains("turn off wifi") || lower.contains("wifi bondho kor") || lower.contains("wifi off kor") -> {
                hardwareController.toggleWifi(false)
                return "Understood, Sir. Wi-Fi off."
            }
            lower.contains("bluetooth on") || lower.contains("bluetooth chala") ||
                (lower.contains("connect") && lower.contains("headphone")) || lower.contains("headphone connect kor") -> {
                hardwareController.enableBluetoothAndConnectHeadphones()
                return "Ok, Boss. Bluetooth on, connecting headphones."
            }
            lower.contains("hotspot") -> {
                hardwareController.openHotspotSettings()
                return "Hotspot controls open, Sir."
            }
            lower.contains("mobile data") || lower.contains("turn on data") || lower.contains("turn off data") ||
                lower.contains("data on kor") || lower.contains("data bondho kor") -> {
                hardwareController.openMobileDataSettings()
                return "Mobile data controls open, Sir."
            }
            lower.contains("next song") || lower.contains("change song") || lower.contains("change the song") ||
                lower.contains("gaan palta") || lower.contains("gaan change kor") || lower.contains("paltiye de") -> {
                hardwareController.controlMedia("NEXT")
                return "Ok, Boss. Next track."
            }
            lower.contains("pause song") || lower.contains("stop song") || lower.contains("turn off song") ||
                lower.contains("gaan bondho kor") || lower.contains("gaan thama") -> {
                hardwareController.controlMedia("PAUSE")
                return "Music paused, Sir."
            }
            lower.contains("play song") || lower.contains("turn on song") ||
                lower.contains("gaan chala") || lower.contains("gaan chalie de") -> {
                val prefApp = memoryStore.preferredMusicApp
                if (!prefApp.equals("YouTube", ignoreCase = true)) {
                    return hardwareController.playInPreferredMusicApp(utterance, prefApp)
                }
                hardwareController.controlMedia("PLAY")
                return "Playing music, Sir."
            }
            lower.contains("click photo") || lower.contains("take selfie") || lower.contains("take photo") ||
                lower.contains("chobi tol") || lower.contains("selfie tol") || lower.contains("photo tol") -> {
                JarvisAccessibilityService.instance?.triggerCameraShutterWithFocusCheck()
                return "Focus locked. Taking photo, Sir."
            }
            lower.startsWith("set alarm") || lower.contains("alarm de") || lower.contains("alarm lagie de") -> {
                hardwareController.setSmartAlarm(utterance)
                return "Alarm set, Sir. Escalation guard active."
            }
            lower.contains("i woke up") || lower.contains("ami uthe gechi") || lower.contains("uthe gechi re") || lower.contains("uthe porchi") -> {
                hardwareController.acknowledgeWakeUp()
                return "Good morning, Sir. Alarm disarmed."
            }
            lower.contains("remember") || lower.contains("mone rakhis") || lower.contains("mone rakhbi") -> {
                memoryStore.addTuitionOrHomework("Custom Tuition", "Upcoming", utterance)
                return "Saved to encrypted memory, Sir."
            }
            lower.contains("sat up") || lower.contains("shut up") ||
                lower.contains("chup kor") || lower.contains("chup koro") ||
                lower.contains("boka bondho kor") || lower.contains("gyan dish na") ||
                lower.contains("চুপ কর") || lower.contains("চুপ করো") -> {
                return "Okay, Sir. Just reminding you, Boss."
            }
            lower.startsWith("open ") || lower.endsWith(" khol") || lower.contains(" open kor") -> {
                val appName = lower
                    .removePrefix("open ")
                    .removeSuffix(" khol")
                    .replace(" open kor", "")
                    .trim()
                val launched = hardwareController.launchAppByName(appName)
                return if (launched) "Opening $appName, Sir." else "Could not find $appName, Sir."
            }
        }

        return queryJarvisLLM(utterance, detectedMood, activeForegroundApp)
    }

    private fun queryJarvisLLM(utterance: String, mood: String, activeApp: String): String {
        val apiKey = memoryStore.apiKey
        val tuitions = memoryStore.getTuitionsSummary()
        val tricks = memoryStore.getSavedReelsTricks()
        val feedbackRules = memoryStore.getFeedbackRules()
        val langStyle = detectLanguageStyle(utterance)

        val systemPrompt = """
            You are JARVIS, the supreme personal AI assistant on the owner's Redmi Note 15 5G.
            SPEECH & TONE RULES (NON-NEGOTIABLE):
            - Speak with crisp, concise, Iron Man JARVIS precision (1-2 short, sharp sentences for actions; clear structured points for study questions).
            - Example style: "Understood, Sir. Standby mode on.", "Right away, Sir.", "Ok, Boss."
            - ALWAYS address the user as "Sir" (primary title, 80% of the time) and occasionally as "Boss".
            - NEVER use "tui", "tor", or "bhai" to address the user. In Bengali or Banglish, strictly use "apni/apnar" or "tumi/tomar".
            - Context: Mood = $mood | Active App = $activeApp | Language Style = $langStyle | Rules = $feedbackRules
        """.trimIndent()

        // TIER 1: Try 100% On-Device Offline GGUF LLM (`Llama-3.2-3B` / `Qwen-2.5-3B` running locally on 127.0.0.1)
        // Zero internet needed, zero data ever leaves the Redmi Note 15 5G!
        offlineLocalLlm.queryOnDeviceGgufModel(
            "$systemPrompt\nLocal Vault: $tuitions | $tricks",
            utterance
        )?.let { localReply ->
            return localReply
        }

        // TIER 2: If Internet is connected, use Zero-Key Private Cloud AI (or optional Gemini if key is set)
        // Privacy Firewall: Notice we DO NOT send `tuitions` or `tricks` to external cloud servers unless the question asks about schedule!
        if (hardwareController.autoEnableNetworkIfOffline()) {
            if (apiKey.isBlank()) {
                offlineLocalLlm.queryZeroKeyPrivateCloudAi(systemPrompt, utterance)?.let { privateCloudReply ->
                    return privateCloudReply
                }
            } else {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                    val payload = JSONObject().apply {
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("parts", JSONArray().apply {
                                    put(JSONObject().put("text", "$systemPrompt\n\nUser said: $utterance"))
                                })
                            })
                        })
                    }
                    val req = Request.Builder()
                        .url(url)
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(req).execute().use { resp ->
                        val bodyStr = resp.body?.string() ?: ""
                        val json = JSONObject(bodyStr)
                        return json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                            .trim()
                    }
                } catch (_: Exception) {
                    offlineLocalLlm.queryZeroKeyPrivateCloudAi(systemPrompt, utterance)?.let { return it }
                }
            }
        }

        // TIER 3: 100% Offline Built-In Study, Math & Physics Encyclopedia Fallback
        return offlineLocalLlm.queryEmbeddedOfflineEncyclopedia(utterance)
    }
}
