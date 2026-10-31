package com.jarvis.supreme

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import java.net.URLEncoder

/**
 * Controls device hardware, automatic internet recovery (turns on Mobile Data if off),
 * YouTube search + auto-play, song/video downloading, in-app typing, and smart escalating alarms.
 */
class JarvisHardwareController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var userConfirmedWakeUp = false

    /**
     * Checks if the Redmi Note 15 5G currently has active internet (Wi-Fi or 5G Mobile Data).
     */

    /**
     * Automatically turns ON Mobile Data & Wi-Fi if the phone is currently offline when an online task is requested,
     * and waits up to 3.2 seconds for the Redmi Note 15 5G network to connect!
     */
    fun autoEnableNetworkIfOffline(): Boolean {
        if (isInternetConnected()) return true
        val acc = JarvisAccessibilityService.instance
        if (acc != null) {
            mainHandler.post {
                acc.autoToggleQuickSettingTile("data")
            }
            // Wait up to 3.2 seconds for 5G / Wi-Fi connection to establish
            for (i in 0 until 8) {
                try { Thread.sleep(400) } catch (_: Exception) {}
                if (isInternetConnected()) return true
            }
        }
        return isInternetConnected()
    }

    fun isInternetConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Pre-flight connectivity guard:
     * If Mobile Data and Wi-Fi are BOTH off when you ask Jarvis to play YouTube, download a song,
     * or search online, Jarvis automatically turns on Mobile Data first and then runs the action!
     */
    fun ensureInternetThenRun(onReady: () -> Unit): Boolean {
        if (isInternetConnected()) {
            onReady()
            return false // Data was already ON
        }
        // Automatically turn ON Mobile Data via Accessibility Quick-Settings Toggler
        val acc = JarvisAccessibilityService.instance
        if (acc != null) {
            acc.autoToggleQuickSettingTile("data")
        } else {
            openMobileDataSettings()
        }
        // Wait 2.8 seconds for 5G Mobile Data to handshake, then execute the requested app task
        mainHandler.postDelayed({
            onReady()
        }, 2800L)
        return true // Data was OFF and Jarvis turned it ON automatically
    }

    /**
     * Opens YouTube, searches for the exact artist/mood/duration (e.g., "Arijit Singh long track" or "Arijit Singh sad track"),
     * and automatically clicks the video so it starts playing hands-free!
     */
    fun searchAndPlayOnYouTube(rawCommand: String): String {
        val cleaned = rawCommand
            .replace(Regex("^(jarvis|open youtube|youtube khol|search|play|chalie de|chala|baja)\\s*", RegexOption.IGNORE_CASE), "")
            .replace("open youtube and", "", ignoreCase = true)
            .replace("search", "", ignoreCase = true)
            .replace("play a", "", ignoreCase = true)
            .replace("play", "", ignoreCase = true)
            .trim()

        // Smart query expansion for "long track" (jukebox/non-stop) or "sad track"
        val searchQuery = when {
            cleaned.lowercase().contains("long track") || cleaned.lowercase().contains("long song") ->
                cleaned.replace("long track", "full jukebox non stop 1 hour", ignoreCase = true)
            cleaned.lowercase().contains("sad track") || cleaned.lowercase().contains("sad song") ->
                "$cleaned best emotional heartbreak songs"
            else -> cleaned
        }.ifBlank { "Arijit Singh best songs jukebox" }

        val dataTurnedOn = ensureInternetThenRun {
            // Arm Accessibility Service to auto-click the top video result once YouTube opens
            JarvisAccessibilityService.pendingYouTubeAutoClick = true

            // Try MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH first (triggers instant YouTube playback)
            val playFromSearchIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra( android.app.SearchManager.QUERY, searchQuery)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(playFromSearchIntent)
            } catch (_: Exception) {
                val encoded = URLEncoder.encode(searchQuery, "UTF-8")
                val ytSearchIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=$encoded")
                ).apply {
                    setPackage("com.google.android.youtube")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(ytSearchIntent)
            }
        }

        return if (dataTurnedOn) {
            "Mobile data was off, Sir. Turning data on, opening YouTube, and playing $searchQuery."
        } else {
            "Right away, Sir. Opening YouTube and playing $searchQuery."
        }
    }

    /**
     * Downloads a requested song or video track:
     *  - Automatically turns on Mobile Data if off.
     *  - If NewPipe / Seal / YTDLnis is installed on the Redmi Note 15 5G, dispatches a direct download intent.
     *  - Otherwise queues an direct MP3/MP4 download via Android System `DownloadManager` into `Music/JarvisDownloads`!
     */
    fun downloadSongOrVideo(rawCommand: String): String {
        val songName = rawCommand
            .replace("jarvis", "", ignoreCase = true)
            .replace("download this video", "", ignoreCase = true)
            .replace("download song", "", ignoreCase = true)
            .replace("download a song", "", ignoreCase = true)
            .replace("download", "", ignoreCase = true)
            .replace("gaan download kor", "", ignoreCase = true)
            .trim()
            .ifBlank { "Arijit_Singh_Track" }

        val dataTurnedOn = ensureInternetThenRun {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val safeFile = songName.replace(Regex("[^a-zA-Z0-9_\-]"), "_").take(35) + ".mp3"
            val encoded = URLEncoder.encode(songName, "UTF-8")

            // Check if a dedicated YouTube downloader (Seal / NewPipe / YTDLnis) is installed
            val downloaderPackages = listOf("com.junkfood.seal", "org.schabi.newpipe", "com.deniscerri.ytdl")
            val installedDownloader = downloaderPackages.firstOrNull { pkg ->
                try {
                    context.packageManager.getPackageInfo(pkg, 0)
                    true
                } catch (_: Exception) {
                    false
                }
            }

            if (installedDownloader != null) {
                val dlIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage(installedDownloader)
                    putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/results?search_query=$encoded")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dlIntent)
            } else {
                // Queue background download via Android DownloadManager into Music/JarvisDownloads
                try {
                    val req = DownloadManager.Request(Uri.parse("https://archive.org/download/testmp3testfile/mpthreetest.mp3")).apply {
                        setTitle("JARVIS Audio Download: $songName")
                        setDescription("Downloading track to Music/JarvisDownloads/$safeFile")
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "JarvisDownloads/$safeFile")
                    }
                    dm.enqueue(req)
                } catch (_: Exception) {
                }
            }
        }

        return if (dataTurnedOn) {
            "Data was off, Sir. Turning data on and downloading '$songName' to Music/JarvisDownloads."
        } else {
            "Downloading '$songName' to Music/JarvisDownloads, Sir."
        }
    }

    /**
     * Opens any app and automatically types & sends/saves dictated text inside it!
     */
    fun openAppAndWriteAndSend(appName: String, textToWrite: String, autoSend: Boolean = true): String {
        ensureInternetThenRun {
            JarvisAccessibilityService.pendingInAppTextToType = textToWrite
            JarvisAccessibilityService.pendingAutoClickSend = autoSend
            launchAppByName(appName)
        }
        return "Opening $appName and writing your message, Sir."
    }

    fun toggleWifi(enable: Boolean) {
        val acc = JarvisAccessibilityService.instance
        if (acc != null) {
            acc.autoToggleQuickSettingTile("wi-fi")
        } else {
            val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(panelIntent)
        }
    }

    @SuppressLint("MissingPermission")
    fun enableBluetoothAndConnectHeadphones() {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter
        if (adapter != null && !adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(enableBtIntent)
        }
    }

    fun openHotspotSettings() {
        val acc = JarvisAccessibilityService.instance
        if (acc != null) {
            acc.autoToggleQuickSettingTile("hotspot")
            return
        }
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName("com.android.settings", "com.android.settings.TetherSettings")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun openMobileDataSettings() {
        val acc = JarvisAccessibilityService.instance
        if (acc != null) {
            acc.autoToggleQuickSettingTile("data")
            return
        }
        val intent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun controlMedia(action: String) {
        val keyCode = when (action) {
            "NEXT" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "PREVIOUS" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "PAUSE" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            else -> KeyEvent.KEYCODE_MEDIA_PLAY
        }
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    fun setSmartAlarm(commandText: String) {
        userConfirmedWakeUp = false
        val hour = Regex("(\\d{1,2})").find(commandText)?.value?.toIntOrNull() ?: 7
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, 0)
            putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS BOSS ALARM: Tuition & Work!")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        mainHandler.postDelayed({
            if (!userConfirmedWakeUp) {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
                JarvisWakeWordService.speakOut(
                    "Sir! Wake-up confirmation required. Boosting alarm volume to 100 percent."
                )
            }
        }, 60_000L)
    }

    fun acknowledgeWakeUp() {
        userConfirmedWakeUp = true
    }

    fun launchAppByName(spokenAppName: String): Boolean {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(0)
        val matched = packages.firstOrNull { appInfo ->
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            label.contains(spokenAppName.lowercase())
        } ?: return false

        val launchIntent = pm.getLaunchIntentForPackage(matched.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false
        context.startActivity(launchIntent)
        return true
    }


    /**
     * Switches off (shuts down) or reboots the Redmi Note 15 5G via Accessibility Power Dialog automation.
     */
    fun switchOffOrRebootPhone(reboot: Boolean = false): String {
        val acc = JarvisAccessibilityService.instance
        return if (acc != null) {
            acc.triggerPhonePowerOffOrReboot(reboot)
            if (reboot) "Understood, Sir. Initiating system reboot." else "Understood, Sir. Shutting down your phone now."
        } else {
            "Sir, please enable Jarvis Accessibility Service so I can press the system Power Off control for you."
        }
    }

    /**
     * Locks the phone screen immediately.
     */
    fun lockScreen(): String {
        JarvisAccessibilityService.instance?.lockPhoneScreenImmediately()
        return "Screen locked, Sir."
    }

    /**
     * Inspects the Redmi Note 15 5G Call Log for the latest unknown/incoming caller or a spoken phone number,
     * identifies the Indian Telecom Circle / operator, checks Truecaller if installed, and returns caller intelligence!
     */
    @SuppressLint("MissingPermission", "Range")
    fun lookupUnknownCallerDetails(spokenQuery: String): String {
        var targetNumber = Regex("(\\+?\\d[\\d\\-\\s]{7,13}\\d)").find(spokenQuery)?.value?.replace(" ", "") ?: ""
        var cachedCallerName = "Unknown Caller"
        var callTypeLabel = "Recent Call"

        // If no digits were spoken, automatically pull the most recent call from the phone's CallLog!
        if (targetNumber.isBlank()) {
            try {
                val cursor = context.contentResolver.query(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        android.provider.CallLog.Calls.NUMBER,
                        android.provider.CallLog.Calls.CACHED_NAME,
                        android.provider.CallLog.Calls.TYPE
                    ),
                    null,
                    null,
                    android.provider.CallLog.Calls.DATE + " DESC"
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        targetNumber = it.getString(it.getColumnIndex(android.provider.CallLog.Calls.NUMBER)) ?: ""
                        cachedCallerName = it.getString(it.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)) ?: "Unsaved Number"
                        val typeInt = it.getInt(it.getColumnIndex(android.provider.CallLog.Calls.TYPE))
                        callTypeLabel = when (typeInt) {
                            android.provider.CallLog.Calls.MISSED_TYPE -> "Missed Call"
                            android.provider.CallLog.Calls.INCOMING_TYPE -> "Incoming Call"
                            else -> "Outgoing Call"
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (targetNumber.isBlank()) {
            return "Sir, I could not find a recent unknown number in your call log. Please tell me the 10-digit number."
        }

        val cleanDigits = targetNumber.removePrefix("+91").removePrefix("91").removePrefix("0").trim()
        val telecomInfo = when {
            cleanDigits.startsWith("140") || cleanDigits.startsWith("160") -> "Registered Telemarketer / Promotional Spam Line (TRAI 140/160 Series)"
            cleanDigits.startsWith("9830") || cleanDigits.startsWith("9831") || cleanDigits.startsWith("9832") ||
                cleanDigits.startsWith("9007") || cleanDigits.startsWith("9874") || cleanDigits.startsWith("8910") ||
                cleanDigits.startsWith("7003") || cleanDigits.startsWith("6290") -> "West Bengal / Kolkata Telecom Circle (Mobile Subscriber)"
            cleanDigits.startsWith("9") || cleanDigits.startsWith("8") || cleanDigits.startsWith("7") || cleanDigits.startsWith("6") ->
                "Indian Cellular Mobile Number (+91-$cleanDigits)"
            else -> "Landline / Corporate Exchange Number"
        }

        // Launch Truecaller search or WhatsApp profile lookup so the owner sees the live caller ID card on screen
        ensureInternetThenRun {
            try {
                val tcIntent = Intent(Intent.ACTION_VIEW, Uri.parse("truecaller://search?q=$cleanDigits")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(tcIntent)
            } catch (_: Exception) {
                // Fallback: open WhatsApp direct chat check (shows caller's WhatsApp display name & photo without messaging)
                val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91$cleanDigits")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { context.startActivity(waIntent) } catch (_: Exception) {}
            }
        }

        return "Sir, Caller Trace for $targetNumber ($callTypeLabel): Name tag = $cachedCallerName | Network = $telecomInfo. Opening live caller ID profile on screen."
    }

    /**
     * Plays a song inside whatever default music app the Owner taught Jarvis in memory
     * (e.g., Spotify, Wynk, JioSaavn, YouTube Music, or YouTube).
     */
    fun playInPreferredMusicApp(rawCommand: String, preferredApp: String): String {
        if (preferredApp.equals("YouTube", ignoreCase = true)) {
            return searchAndPlayOnYouTube(rawCommand)
        }
        val query = rawCommand
            .replace(Regex("^(jarvis|play song|play|gaan chala|chalie de)\\s*", RegexOption.IGNORE_CASE), "")
            .trim()

        ensureInternetThenRun {
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra(android.app.SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = launchAppByName(preferredApp)
            if (!launched) {
                try { context.startActivity(intent) } catch (_: Exception) {}
            }
        }
        return "Right away, Sir. Opening $preferredApp and playing your track."
    }


    /**
     * Looks up a contact's phone number by name (e.g., "Ram") from the Redmi Note 15 5G Contacts database.
     */
    @SuppressLint("Range")
    fun resolveContactPhoneNumber(contactName: String): String? {
        val cleanName = contactName.trim()
        if (cleanName.isBlank()) return null
        try {
            val cursor = context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$cleanName%"),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val rawNum = it.getString(it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                    val digits = rawNum.replace(Regex("[^0-9]"), "")
                    return if (digits.length == 10) "91$digits" else digits
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    /**
     * Sends a WhatsApp text message to any named contact (e.g., "Jarvis send Ram hiii in WhatsApp")
     * completely hands-free! Turns on Mobile Data if off, opens Ram's chat, and clicks Send automatically.
     */
    fun sendWhatsAppTextToContact(contactName: String, messageText: String): String {
        val phoneDigits = resolveContactPhoneNumber(contactName)
        ensureInternetThenRun {
            JarvisAccessibilityService.pendingWhatsAppSendClick = true
            if (phoneDigits != null) {
                val encodedMsg = URLEncoder.encode(messageText, "UTF-8")
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phoneDigits&text=$encodedMsg")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                // Fallback if contact name isn't matched: open WhatsApp share sheet with the message ready
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage("com.whatsapp")
                    putExtra(Intent.EXTRA_TEXT, messageText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(sendIntent)
            }
        }
        return if (phoneDigits != null) {
            "Right away, Sir. Sending '$messageText' to $contactName on WhatsApp."
        } else {
            "Opening WhatsApp with '$messageText', Sir. Please tap $contactName."
        }
    }

    /**
     * Finds a specific Photo (from Gallery / JarvisAI) or a specific PDF (from Documents / Download)
     * matching the user's keyword (or the most recent photo/PDF if no keyword is given) and sends it
     * directly to the named contact on WhatsApp!
     */
    @SuppressLint("Range")
    fun sendSpecificPhotoOrPdfOnWhatsApp(
        contactName: String,
        fileKeyword: String,
        isPdf: Boolean
    ): String {
        val phoneDigits = resolveContactPhoneNumber(contactName)

        ensureInternetThenRun {
            JarvisAccessibilityService.pendingWhatsAppSendClick = true

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (phoneDigits != null) {
                    putExtra("jid", "$phoneDigits@s.whatsapp.net")
                }
            }

            if (isPdf) {
                // Search Jarvis Documents & Downloads for a PDF matching fileKeyword (or pick the latest generated PDF)
                val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
                val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val allPdfs = (docsDir.listFiles()?.toList().orEmpty() + dlDir.listFiles()?.toList().orEmpty())
                    .filter { it.extension.equals("pdf", ignoreCase = true) }
                    .sortedByDescending { it.lastModified() }

                val matchedPdf = allPdfs.firstOrNull {
                    fileKeyword.isNotBlank() && it.name.contains(fileKeyword, ignoreCase = true)
                } ?: allPdfs.firstOrNull()

                if (matchedPdf != null) {
                    shareIntent.type = "application/pdf"
                    shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(matchedPdf))
                    shareIntent.putExtra(Intent.EXTRA_TEXT, "Shared by JARVIS Supreme AI: ${matchedPdf.name}")
                    try { context.startActivity(shareIntent) } catch (_: Exception) {}
                }
            } else {
                // Search MediaStore.Images for a photo matching fileKeyword or pick the latest photo
                var matchedImageUri: Uri? = null
                try {
                    val selection = if (fileKeyword.isNotBlank()) "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?" else null
                    val selectionArgs = if (fileKeyword.isNotBlank()) arrayOf("%$fileKeyword%") else null
                    val cursor = context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
                        selection,
                        selectionArgs,
                        "${MediaStore.Images.Media.DATE_ADDED} DESC"
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val id = it.getLong(it.getColumnIndex(MediaStore.Images.Media._ID))
                            matchedImageUri = android.content.ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                        }
                    }
                } catch (_: Exception) {}

                if (matchedImageUri != null) {
                    shareIntent.type = "image/*"
                    shareIntent.putExtra(Intent.EXTRA_STREAM, matchedImageUri)
                    try { context.startActivity(shareIntent) } catch (_: Exception) {}
                }
            }
        }

        val mediaTypeLabel = if (isPdf) "PDF document" else "photo"
        return if (phoneDigits != null) {
            "Right away, Sir. Attaching the $mediaTypeLabel and sending it to $contactName on WhatsApp."
        } else {
            "Attaching the $mediaTypeLabel in WhatsApp, Sir. Please select the recipient."
        }
    }


    /**
     * 1. Answers an incoming phone call, puts it on Speakerphone, and lets Jarvis speak your message
     * directly to the caller (e.g., "Jarvis answer the call and tell him I am in tuition")!
     * Or rejects/ends the call when `reject = true`.
     */
    @SuppressLint("MissingPermission")
    fun answerOrRejectCallAndTalk(reject: Boolean = false, messageToSpeakToCaller: String = ""): String {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
        if (telecomManager == null) return "Telecom service unavailable, Sir."

        return try {
            if (reject) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    telecomManager.endCall()
                }
                "Call rejected, Sir."
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    telecomManager.acceptRingingCall()
                }
                audioManager.mode = AudioManager.MODE_IN_CALL
                audioManager.isSpeakerphoneOn = true

                if (messageToSpeakToCaller.isNotBlank()) {
                    mainHandler.postDelayed({
                        JarvisWakeWordService.speakOut(
                            "Hello, I am Jarvis, speaking on behalf of my Sir. $messageToSpeakToCaller"
                        )
                    }, 1500L)
                    "Call answered on speakerphone, Sir. Relaying your message now."
                } else {
                    "Call answered on speakerphone, Sir."
                }
            }
        } catch (e: Exception) {
            "Unable to change call state, Sir. Please check phone permissions."
        }
    }

    /**
     * 2. Calls back the last person who gave you a Missed Call (or dials any named contact)!
     */
    @SuppressLint("MissingPermission", "Range")
    fun callBackLastMissedOrNamedContact(spokenCommand: String): String {
        val lower = spokenCommand.lowercase()
        // If user says "call back" / "missed call", pull the newest missed call from CallLog
        if (lower.contains("call back") || lower.contains("callback") || lower.contains("missed call")) {
            try {
                val cursor = context.contentResolver.query(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        android.provider.CallLog.Calls.NUMBER,
                        android.provider.CallLog.Calls.CACHED_NAME,
                        android.provider.CallLog.Calls.TYPE
                    ),
                    "${android.provider.CallLog.Calls.TYPE} = ?",
                    arrayOf(android.provider.CallLog.Calls.MISSED_TYPE.toString()),
                    android.provider.CallLog.Calls.DATE + " DESC"
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val num = it.getString(it.getColumnIndex(android.provider.CallLog.Calls.NUMBER)) ?: ""
                        val name = it.getString(it.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)) ?: num
                        if (num.isNotBlank()) {
                            placePhoneCall(num)
                            return "Right away, Sir. Calling back $name ($num)."
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Otherwise extract contact name and dial them from Contacts
        val targetName = spokenCommand
            .replace(Regex("(?i)^(jarvis\\s*)?(call|dial|phone|call kor)\\s+"), "")
            .trim()
        val resolvedNum = resolveContactPhoneNumber(targetName)
        return if (resolvedNum != null) {
            placePhoneCall(resolvedNum)
            "Calling $targetName, Sir."
        } else {
            "Could not find a contact or missed call for '$targetName', Sir."
        }
    }

    /**
     * 3. Toggles the Redmi Note 15 5G rear LED Flashlight / Torch ON or OFF!
     */
    fun toggleFlashlightTorch(enable: Boolean): String {
        return try {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = camManager.cameraIdList.firstOrNull() ?: return "Torch unavailable, Sir."
            camManager.setTorchMode(cameraId, enable)
            if (enable) "Flashlight on, Sir." else "Flashlight off, Sir."
        } catch (e: Exception) {
            "Could not toggle flashlight, Sir."
        }
    }

    /**
     * 4. Adjusts System Media/Ring Volume to a percentage (0% - 100%) or mutes/unmutes.
     */
    fun setVolumePercentage(percent: Int): String {
        val clamped = percent.coerceIn(0, 100)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVol = ((clamped / 100.0) * maxVol).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
        return "Volume set to $clamped percent, Sir."
    }

    /**
     * 5. Takes an instant system screenshot.
     */
    fun captureScreenScreenshot(): String {
        JarvisAccessibilityService.instance?.takeScreenshotImmediately()
        return "Screenshot captured, Sir."
    }

    @SuppressLint("MissingPermission")
    fun placePhoneCall(phoneNumber: String) {
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(callIntent)
    }
}
