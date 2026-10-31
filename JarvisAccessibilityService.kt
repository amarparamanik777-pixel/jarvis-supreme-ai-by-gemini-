package com.jarvis.supreme

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Gives Jarvis "Supreme Screen & In-App Automation Power" on Redmi Note 15 5G:
 *  1. Hands-Free Quick Settings Toggler: Pulls down the Xiaomi HyperOS Control Center and automatically taps
 *     "Mobile data", "Wi-Fi", or "Hotspot" when internet is off and a task needs connectivity!
 *  2. In-App Task Executor:
 *     - YouTube Auto-Play: When Jarvis opens a YouTube search (e.g., "Arijit Singh sad song long track"),
 *       this service waits 2.2 seconds for results to load and automatically taps the video to start playback!
 *     - In-App Typing & Sending: Types dictated text into active input fields and taps the Send/Save button.
 *  3. Reels/Shorts Trick Reader, Live Grammar Checker, Boss Distraction Guard, and Camera Shutter Clicker.
 */
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
        var currentForegroundPackage: String = ""
        var latestScreenTextSnapshot: String = ""

        // Pending automation flags triggered by JarvisHardwareController / JarvisBrain
        var pendingYouTubeAutoClick: Boolean = false
        var pendingInAppTextToType: String? = null
        var pendingAutoClickSend: Boolean = false
    }

    private lateinit var memoryStore: EncryptedMemoryStore
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastNagTimestamp = 0L
    private var lastGrammarCheckTimestamp = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        memoryStore = EncryptedMemoryStore(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !memoryStore.isJarvisActive) return

        val pkg = event.packageName?.toString() ?: return
        currentForegroundPackage = pkg

        // Capture visible text from active window
        rootInActiveWindow?.let { root ->
            val collected = mutableListOf<String>()
            collectVisibleText(root, collected)
            if (collected.isNotEmpty()) {
                latestScreenTextSnapshot = collected.take(18).joinToString(" | ")
            }

            // Execute pending YouTube video auto-play once YouTube search results load
            if (pendingYouTubeAutoClick && pkg.contains("youtube")) {
                pendingYouTubeAutoClick = false
                mainHandler.postDelayed({
                    clickFirstYouTubeVideoResult()
                }, 2200L)
            }

            // Execute pending in-app text typing & send
            pendingInAppTextToType?.let { textToType ->
                if (typeTextIntoFocusedOrEditableNode(root, textToType)) {
                    pendingInAppTextToType = null
                    if (pendingAutoClickSend) {
                        pendingAutoClickSend = false
                        mainHandler.postDelayed({ clickSendOrSubmitButton(root) }, 700L)
                    }
                }
            }
        }

        // Boss + Friend Distraction Guard (Instagram, Netflix)
        val entertainmentApps = listOf("com.instagram.android", "com.netflix.mediaclient")
        val now = System.currentTimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            entertainmentApps.any { pkg.contains(it) } &&
            (now - lastNagTimestamp > 15 * 60 * 1000L)
        ) {
            val tuitions = memoryStore.getTuitionsSummary()
            if (!tuitions.contains("No pending")) {
                lastNagTimestamp = now
                JarvisWakeWordService.speakOut(
                    "Sir, you have pending tuition work: $tuitions. Better finish that first, Boss."
                )
            }
        }

        // Live Grammar & Spelling Assistant when typing text
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            (now - lastGrammarCheckTimestamp > 20_000L)
        ) {
            val typedText = event.text?.joinToString(" ") ?: ""
            checkCommonGrammarMistakes(typedText)?.let { suggestion ->
                lastGrammarCheckTimestamp = now
                JarvisWakeWordService.speakOut(suggestion)
            }
        }
    }

    /**
     * Automatically pulls down the Xiaomi HyperOS Quick Settings shade, finds the tile
     * ("Mobile data", "Wi-Fi", "Hotspot"), taps it ON, and closes the shade!
     */
    fun autoToggleQuickSettingTile(tileKeyword: String) {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        mainHandler.postDelayed({
            val root = rootInActiveWindow
            var clicked = false
            if (root != null) {
                val candidates = listOf(tileKeyword, "Mobile data", "Data", "Internet", "Wi-Fi", "Hotspot")
                for (kw in candidates) {
                    if (!kw.lowercase().contains(tileKeyword.lowercase())) continue
                    val nodes = root.findAccessibilityNodeInfosByText(kw)
                    if (!nodes.isNullOrEmpty()) {
                        for (node in nodes) {
                            if (node.isClickable) {
                                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                clicked = true
                                break
                            } else if (node.parent?.isClickable == true) {
                                node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                clicked = true
                                break
                            }
                        }
                    }
                    if (clicked) break
                }
            }
            // Fallback coordinate tap for Mobile Data tile on Redmi Note 15 5G HyperOS Control Center
            if (!clicked && tileKeyword.lowercase().contains("data")) {
                val tapPath = Path().apply { moveTo(780f, 420f) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(tapPath, 0, 100))
                    .build()
                dispatchGesture(gesture, null, null)
            }
            // Collapse Quick Settings shade back to the app after 900ms
            mainHandler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
            }, 900L)
        }, 850L)
    }

    /**
     * Clicks the first video card in YouTube search results so the requested song/track plays automatically.
     */
    fun clickFirstYouTubeVideoResult() {
        val root = rootInActiveWindow ?: return
        // Try clicking the first playable video view in YouTube's RecyclerView
        val clickableVideo = findFirstPlayableVideoNode(root)
        if (clickableVideo != null) {
            clickableVideo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        // Fallback: Tap the center of the first video thumbnail on Redmi Note 15 5G screen (x=540, y=680)
        val path = Path().apply { moveTo(540f, 680f) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 120))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun findFirstPlayableVideoNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (node.isClickable && (desc.contains("minutes") || desc.contains("views") || desc.contains("play"))) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findFirstPlayableVideoNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun typeTextIntoFocusedOrEditableNode(root: AccessibilityNodeInfo, text: String): Boolean {
        val editable = findEditableNode(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findEditableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun clickSendOrSubmitButton(root: AccessibilityNodeInfo) {
        val sendKeywords = listOf("Send", "Post", "Save", "Submit", "Done")
        for (kw in sendKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(kw)
            if (!nodes.isNullOrEmpty()) {
                nodes.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null || out.size >= 20) return
        val txt = node.text?.toString()?.trim()
        if (!txt.isNullOrBlank() && txt.length > 3) {
            out.add(txt)
        }
        for (i in 0 until node.childCount) {
            collectVisibleText(node.getChild(i), out)
        }
    }

    private fun checkCommonGrammarMistakes(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("i didn't went") -> "Sir, grammar check: use 'I didn't go' instead of 'I didn't went'."
            lower.contains("more better") -> "Sir, quick correction: use 'better' instead of 'more better'."
            lower.contains("recieve") -> "Spelling check, Sir: 'receive' is E-I after C."
            lower.contains("tommorow") -> "Spelling check, Sir: 'tomorrow' has one M and two Rs."
            else -> null
        }
    }

    fun triggerCameraShutterWithFocusCheck() {
        val root = rootInActiveWindow ?: return
        val focusPath = Path().apply { moveTo(540f, 1200f) }
        val focusGesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(focusPath, 0, 100))
            .build()

        dispatchGesture(focusGesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                clickShutterNodeOrFallback(root)
            }
        }, null)
    }

    private fun clickShutterNodeOrFallback(root: AccessibilityNodeInfo) {
        val shutterKeywords = listOf("shutter", "capture", "take photo", "camera_shutter")
        for (keyword in shutterKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                nodes.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }
        val shutterPath = Path().apply { moveTo(540f, 2050f) }
        val shutterGesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(shutterPath, 600, 120))
            .build()
        dispatchGesture(shutterGesture, null, null)
    }


    /**
     * Opens the Xiaomi HyperOS Power Menu (`GLOBAL_ACTION_POWER_DIALOG`) and automatically taps
     * "Power off" / "Shut down" (or "Reboot") so Jarvis can switch off the phone by voice!
     */
    fun triggerPhonePowerOffOrReboot(reboot: Boolean = false) {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        mainHandler.postDelayed({
            val root = rootInActiveWindow
            val keywords = if (reboot) listOf("Reboot", "Restart") else listOf("Power off", "Shut down", "Switch off")
            var clicked = false
            if (root != null) {
                for (kw in keywords) {
                    val nodes = root.findAccessibilityNodeInfosByText(kw)
                    if (!nodes.isNullOrEmpty()) {
                        nodes.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        clicked = true
                        break
                    }
                }
            }
            // Fallback coordinate tap on Xiaomi HyperOS Power Off slider/button
            if (!clicked) {
                val yCoord = if (reboot) 1450f else 950f
                val tapPath = Path().apply { moveTo(540f, yCoord) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(tapPath, 0, 150))
                    .build()
                dispatchGesture(gesture, null, null)
            }
        }, 900L)
    }

    /**
     * Locks/turns off the Redmi Note 15 5G screen immediately (`GLOBAL_ACTION_LOCK_SCREEN`).
     */
    fun lockPhoneScreenImmediately() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }


    /**
     * Takes an instant system screenshot (`GLOBAL_ACTION_TAKE_SCREENSHOT`) on Redmi Note 15 5G.
     */
    fun takeScreenshotImmediately() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
    }

    override fun onInterrupt() {}
}
