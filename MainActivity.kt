package com.jarvis.supreme

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * JARVIS Main Control Center on Redmi Note 15 5G.
 * Includes:
 *  - JARVIS LIVE LENS (Talk to Jarvis through your live camera like Gemini Live)
 *  - DECORATED COLOR-CODED PDF STUDIO, FULL LECTURE WATCHER & BOARD/PPT CONVERTER
 *  - DIY PROJECT BLUEPRINT & EQUIPMENT ARCHITECT + REELS/SHORTS TRICK VAULT
 *  - PRO AI IMAGE, PHOTO EDITOR & 1080p MULTI-SCENE VIDEO DIRECTOR
 *  - DUAL-MODE VOICE STUDIO (Iron Man Sci-Fi + ElevenLabs Ultra-Realistic Cloud AI)
 *  - VOICE BIOMETRIC LOCK & AES-256 ENCRYPTED TUITION VAULT
 */
class MainActivity : AppCompatActivity() {

    private lateinit var memoryStore: EncryptedMemoryStore
    private lateinit var biometricVerifier: VoiceBiometricVerifier
    private lateinit var brain: JarvisBrain
    private lateinit var creativeStudio: JarvisCreativeStudio
    private lateinit var docLectureEngine: JarvisDocumentAndLectureEngine
    private lateinit var consoleOutput: TextView
    private lateinit var imagePreview: ImageView

    private var currentBitmap: Bitmap? = null
    private var selectedModelStyle: String = "FLUX_PRO"
    private var selectedAspectRatio: String = "9:16"
    private var currentContrast: Float = 1.1f
    private var currentSaturation: Float = 1.25f

    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val PICK_IMAGE_REQUEST = 201

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        memoryStore = EncryptedMemoryStore(this)
        biometricVerifier = VoiceBiometricVerifier(this.memoryStore)
        brain = JarvisBrain(this, memoryStore, JarvisHardwareController(this))
        creativeStudio = JarvisCreativeStudio(this, memoryStore)
        docLectureEngine = JarvisDocumentAndLectureEngine(this, memoryStore)

        requestRuntimePermissions()
        startJarvisService()

        setContentView(buildDashboardUI())
    }

    private fun buildDashboardUI(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F19"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 56, 42, 56)
        }

        val title = TextView(this).apply {
            text = "JARVIS SUPREME AI PRO"
            textSize = 24f
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val subtitle = TextView(this).apply {
            text = "Redmi Note 15 5G • Jarvis Lens • Lecture PDF Studio • 1080p AI Video • Iron Man Voice"
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 8, 0, 28)
        }
        root.addView(title)
        root.addView(subtitle)

        // Manual ON/OFF Master Switch
        val masterSwitch = Switch(this).apply {
            text = "JARVIS Master Power (Manual ON / OFF)"
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = memoryStore.isJarvisActive
            setOnCheckedChangeListener { _, isChecked ->
                memoryStore.isJarvisActive = isChecked
                val statusMsg = if (isChecked) "Jarvis is ONLINE. Say 'Hey Jarvis' anytime!" else "Jarvis is in Standby. Say 'Hey Jarvis' to wake."
                Toast.makeText(this@MainActivity, statusMsg, Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(masterSwitch)

        // =========================================================================
        // 0. JARVIS LIVE LENS + DECORATED PDF, LECTURE NOTES & DIY BLUEPRINT STUDIO
        // =========================================================================
        val lensHeader = TextView(this).apply {
            text = "JARVIS LENS, LECTURE PDF & DIY BLUEPRINT STUDIO"
            textSize = 14f
            setTextColor(Color.parseColor("#00E5FF"))
            setPadding(0, 24, 0, 10)
        }
        root.addView(lensHeader)

        val launchLensBtn = Button(this).apply {
            text = "Launch JARVIS LIVE LENS (Camera + Voice Vision like Gemini Live)"
            setBackgroundColor(Color.parseColor("#0284C7"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, JarvisLensActivity::class.java))
            }
        }
        root.addView(launchLensBtn)

        val studyTopicInput = EditText(this).apply {
            hint = "Lecture URL / Topic / DIY Project (e.g., Quantum Theory or Tesla Coil)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
        }
        root.addView(studyTopicInput)

        val makeLecturePdfBtn = Button(this).apply {
            text = "Watch Full Lecture & Create Decorated Color-Coded PDF (EN / বাংলা)"
            setBackgroundColor(Color.parseColor("#7C3AED"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val topic = studyTopicInput.text.toString().ifBlank { "Unit 7: Quantum Theory & Photoelectric Effect" }
                consoleOutput.text = "Watching full lecture & generating Decorated Color-Coded PDF (Violet Headers, Pink Symbols ★ >> !, Golden Formulas, Green Diagrams)..."
                JarvisWakeWordService.speakOut("Yes Sir, puro lecture analyze kore apnar pen color system diye Decorated PDF banachhi!")
                uiScope.launch {
                    val (notes, pdfFile) = docLectureEngine.watchFullLectureAndGeneratePdf(topic)
                    consoleOutput.text = "Decorated PDF Saved to Documents:\n${pdfFile?.absolutePath}\n\n$notes"
                    JarvisWakeWordService.speakOut("Boss! Full lecture notes ar Decorated PDF ready!")
                }
            }
        }
        root.addView(makeLecturePdfBtn)

        val boardToPdfBtn = Button(this).apply {
            text = "Convert Loaded Board Work / PPT Photo -> Decorated PDF"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val bmp = currentBitmap
                if (bmp == null) {
                    Toast.makeText(this@MainActivity, "Pick a Board/PPT photo from Gallery or use Jarvis Lens first!", Toast.LENGTH_SHORT).show()
                } else {
                    consoleOutput.text = "Reading classroom board work / PPT slide & building Decorated PDF..."
                    JarvisWakeWordService.speakOut("Board-er lekha ar diagram theke Decorated PDF toiri korchi Boss!")
                    uiScope.launch {
                        val (notes, pdfFile) = docLectureEngine.convertBoardOrPptPhotoToPdf(bmp)
                        consoleOutput.text = "Board/PPT PDF Saved:\n${pdfFile?.absolutePath}\n\n$notes"
                        JarvisWakeWordService.speakOut("Sir, Board work-er Decorated PDF save hoye geche!")
                    }
                }
            }
        }
        root.addView(boardToPdfBtn)

        val diyBlueprintBtn = Button(this).apply {
            text = "Generate DIY Project Blueprint + Equipment List + Video Links"
            setBackgroundColor(Color.parseColor("#0D9488"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val idea = studyTopicInput.text.toString().ifBlank { "Smart Ultrasonic Radar with Arduino" }
                consoleOutput.text = "Building full engineering blueprint, equipment list & YouTube tutorial links..."
                JarvisWakeWordService.speakOut("Boss, '$idea' bananor full blueprint, equipment list ar video link ready korchi!")
                uiScope.launch {
                    val (bp, pdfFile) = docLectureEngine.buildDiyProjectBlueprint(idea)
                    consoleOutput.text = "Blueprint PDF Saved: ${pdfFile?.absolutePath}\n\n$bp"
                    JarvisWakeWordService.speakOut("Blueprint ar equipment list ready Boss!")
                }
            }
        }
        root.addView(diyBlueprintBtn)

        val saveReelTrickBtn = Button(this).apply {
            text = "Save Trick from Last Reel/Short to Encrypted Trick Vault"
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val note = studyTopicInput.text.toString().ifBlank { "Short Trick from Reel/Short" }
                val screenCtx = JarvisAccessibilityService.latestScreenTextSnapshot.ifBlank { "Instagram Reel / YouTube Short" }
                memoryStore.saveReelOrShortTrick(note, screenCtx)
                consoleOutput.text = "Saved Tricks Vault:\n${memoryStore.getSavedReelsTricks()}"
                JarvisWakeWordService.speakOut("Ok, Boss! Ei trick-ti apnar encrypted memory-te save kore nilam, Sir!")
            }
        }
        root.addView(saveReelTrickBtn)

        // =========================================================================
        // 1. PRO AI IMAGE, PHOTO EDITOR & 1080p MULTI-SCENE VIDEO STUDIO
        // =========================================================================
        val mediaHeader = TextView(this).apply {
            text = "PRO AI IMAGE, PHOTO EDITOR & 1080p VIDEO STUDIO"
            textSize = 14f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 28, 0, 10)
        }
        root.addView(mediaHeader)

        val mediaPromptInput = EditText(this).apply {
            hint = "Prompt or Edit Instruction (e.g., Iron Man in cyberpunk Kolkata)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
        }
        val captionOverlayInput = EditText(this).apply {
            hint = "Optional Glowing Poster Text / Meme Caption Overlay"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
        }
        root.addView(mediaPromptInput)
        root.addView(captionOverlayInput)

        val studioStatusLabel = TextView(this).apply {
            text = "Model: $selectedModelStyle • Aspect Ratio: $selectedAspectRatio (Reels/Shorts)"
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, 8, 0, 8)
        }
        root.addView(studioStatusLabel)

        val modelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("FLUX_PRO" to "8K Photo", "FLUX_ANIME" to "Anime", "FLUX_3D" to "3D Unreal").forEach { (code, label) ->
            val b = Button(this).apply {
                text = label
                textSize = 11f
                setBackgroundColor(Color.parseColor("#1E293B"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    selectedModelStyle = code
                    studioStatusLabel.text = "Model: $selectedModelStyle • Aspect Ratio: $selectedAspectRatio"
                }
            }
            modelRow.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(modelRow)

        val ratioRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("1:1" to "1:1 Square", "9:16" to "9:16 Reels", "16:9" to "16:9 YouTube").forEach { (ratio, label) ->
            val b = Button(this).apply {
                text = label
                textSize = 11f
                setBackgroundColor(Color.parseColor("#0F172A"))
                setTextColor(Color.parseColor("#38BDF8"))
                setOnClickListener {
                    selectedAspectRatio = ratio
                    studioStatusLabel.text = "Model: $selectedModelStyle • Aspect Ratio: $selectedAspectRatio"
                }
            }
            ratioRow.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(ratioRow)

        imagePreview = ImageView(this).apply {
            minimumHeight = 480
            setPadding(0, 16, 0, 16)
        }
        root.addView(imagePreview)

        val pickGalleryBtn = Button(this).apply {
            text = "Pick Photo / Board Work from Gallery"
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                startActivityForResult(pickIntent, PICK_IMAGE_REQUEST)
            }
        }
        root.addView(pickGalleryBtn)

        val genImgBtn = Button(this).apply {
            text = "Generate HD AI Image ($selectedModelStyle)"
            setBackgroundColor(Color.parseColor("#0284C7"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val prompt = mediaPromptInput.text.toString().ifBlank { "Iron Man flying over futuristic Kolkata Howrah Bridge" }
                consoleOutput.text = "Generating $selectedModelStyle ($selectedAspectRatio) AI Image..."
                JarvisWakeWordService.speakOut("Yes Sir, $selectedModelStyle engine diye apnar HD chobi generate korchi!")
                uiScope.launch {
                    val bmp = creativeStudio.generateProAiImage(prompt, selectedModelStyle, selectedAspectRatio)
                    if (bmp != null) {
                        currentBitmap = bmp
                        imagePreview.setImageBitmap(bmp)
                        consoleOutput.text = "Saved to Xiaomi Gallery -> Pictures/JarvisAI!"
                        JarvisWakeWordService.speakOut("Sir! Apnar chobi ready, Gallery-te save kore diyechi.")
                    } else {
                        consoleOutput.text = "Generation failed. Check internet connection."
                    }
                }
            }
        }
        root.addView(genImgBtn)

        val remixBtn = Button(this).apply {
            text = "AI Image-to-Image Remix (Transform Loaded Photo)"
            setBackgroundColor(Color.parseColor("#0D9488"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val src = currentBitmap
                val editPrompt = mediaPromptInput.text.toString().ifBlank { "Transform into a futuristic cyberpunk superhero portrait" }
                if (src == null) {
                    Toast.makeText(this@MainActivity, "Pick a photo from Gallery or generate one first!", Toast.LENGTH_SHORT).show()
                } else {
                    consoleOutput.text = "Analyzing photo with AI Vision & remixing..."
                    JarvisWakeWordService.speakOut("Sir, apnar chobi analyze kore notun AI avatar banachhi!")
                    uiScope.launch {
                        val remixed = creativeStudio.aiRemixExistingPhoto(src, editPrompt)
                        if (remixed != null) {
                            currentBitmap = remixed
                            imagePreview.setImageBitmap(remixed)
                            consoleOutput.text = "AI Remix Complete & Saved to Gallery!"
                            JarvisWakeWordService.speakOut("Ok, Boss! AI Remix complete, dekhun kemon lagche, Sir!")
                        }
                    }
                }
            }
        }
        root.addView(remixBtn)

        val filterRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("STARK_HUD" to "Stark HUD", "CYBERPUNK_NEON" to "Cyberpunk", "TEAL_ORANGE_CINEMA" to "Cinema LUT").forEach { (preset, titleTxt) ->
            val btn = Button(this).apply {
                text = titleTxt
                textSize = 11f
                setBackgroundColor(Color.parseColor("#1E293B"))
                setTextColor(Color.WHITE)
                setOnClickListener { applyOfflineFilter(preset, captionOverlayInput.text.toString()) }
            }
            filterRow1.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(filterRow1)

        val filterRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("GOLDEN_HOUR" to "Golden Hour", "MATRIX_CYBER" to "Matrix", "NOIR_BW" to "Noir B&W").forEach { (preset, titleTxt) ->
            val btn = Button(this).apply {
                text = titleTxt
                textSize = 11f
                setBackgroundColor(Color.parseColor("#1E293B"))
                setTextColor(Color.WHITE)
                setOnClickListener { applyOfflineFilter(preset, captionOverlayInput.text.toString()) }
            }
            filterRow2.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(filterRow2)

        val genVidBtn = Button(this).apply {
            text = "Render 3-Scene 1080p Full HD AI Video (.MP4)"
            setBackgroundColor(Color.parseColor("#7C3AED"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val topic = mediaPromptInput.text.toString().ifBlank { "Futuristic Iron Man Jarvis defending Kolkata" }
                consoleOutput.text = "Storyboarding 3 AI scenes & rendering 1080p 30fps Full HD MP4 Video..."
                JarvisWakeWordService.speakOut("Yes Sir, 3-scene Full HD 1080p video render shuru korlam!")
                uiScope.launch {
                    val mp4File = creativeStudio.generateMultiSceneFullHdVideo(topic, selectedModelStyle)
                    if (mp4File != null) {
                        consoleOutput.text = "1080p Full HD Video Saved to Gallery (Movies/JarvisAI):\n${mp4File.name}"
                        JarvisWakeWordService.speakOut("Sir! Apnar 1080p Full HD video Gallery-te save hoye geche!")
                    } else {
                        consoleOutput.text = "Video render failed. Check internet connection."
                    }
                }
            }
        }
        root.addView(genVidBtn)

        // =========================================================================
        // 2. VOICE STUDIO SECTION
        // =========================================================================
        val voiceHeader = TextView(this).apply {
            text = "JARVIS VOICE STUDIO (IRON MAN + REALISTIC AI)"
            textSize = 14f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 28, 0, 12)
        }
        root.addView(voiceHeader)

        val hudChimeSwitch = Switch(this).apply {
            text = "Stark HUD Arc-Reactor Sound Effect Before Speaking"
            setTextColor(Color.parseColor("#E2E8F0"))
            isChecked = memoryStore.playHudChime
            setOnCheckedChangeListener { _, isChecked ->
                memoryStore.playHudChime = isChecked
            }
        }
        root.addView(hudChimeSwitch)

        val modeLabel = TextView(this).apply {
            text = "Active Voice Mode: ${memoryStore.voiceMode}"
            setTextColor(Color.parseColor("#F8FAFC"))
            setPadding(0, 12, 0, 8)
        }
        root.addView(modeLabel)

        val btnHybrid = Button(this).apply {
            text = "Use Hybrid Mode (Realistic Cloud + Offline Iron Man)"
            setBackgroundColor(Color.parseColor("#0284C7"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                memoryStore.voiceMode = "HYBRID_AUTO"
                modeLabel.text = "Active Voice Mode: HYBRID_AUTO"
                JarvisWakeWordService.speakOut("Hybrid voice system engaged, Boss.")
            }
        }
        val btnIronMan = Button(this).apply {
            text = "Use Pure Iron Man Sci-Fi Mode (100% Offline, Deep 0.88x)"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                memoryStore.voiceMode = "IRON_MAN_OFFLINE"
                memoryStore.voicePitch = 0.88f
                memoryStore.voiceRate = 1.05f
                modeLabel.text = "Active Voice Mode: IRON_MAN_OFFLINE (Pitch 0.88x)"
                JarvisWakeWordService.speakOut("At your service, Sir. Iron Man cinematic voice calibrated.")
            }
        }
        root.addView(btnHybrid)
        root.addView(btnIronMan)

        val pitchText = TextView(this).apply {
            text = "Voice Depth / Pitch (${String.format("%.2f", memoryStore.voicePitch)}x — Lower = Deeper Iron Man)"
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 16, 0, 4)
        }
        val pitchSeek = SeekBar(this).apply {
            max = 100
            progress = ((memoryStore.voicePitch - 0.5f) * 100).toInt().coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val p = 0.50f + (progress / 100f)
                    memoryStore.voicePitch = p
                    pitchText.text = "Voice Depth / Pitch (${String.format("%.2f", p)}x — Lower = Deeper Iron Man)"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    JarvisWakeWordService.speakOut("Voice pitch calibrated, Boss.")
                }
            })
        }
        root.addView(pitchText)
        root.addView(pitchSeek)

        val geminiKeyInput = EditText(this).apply {
            hint = "Paste Free Gemini API Key (Unlocks Lecture Watcher, Lens & Study Tutor)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            setText(memoryStore.apiKey)
        }
        val elevenKeyInput = EditText(this).apply {
            hint = "Optional: Paste ElevenLabs API Key (Unlocks Core 2 Realistic Voice)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            setText(memoryStore.elevenLabsApiKey)
        }
        val saveKeysBtn = Button(this).apply {
            text = "Encrypt & Save API Keys to Local Hardware Vault"
            setBackgroundColor(Color.parseColor("#0D9488"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                memoryStore.apiKey = geminiKeyInput.text.toString()
                memoryStore.elevenLabsApiKey = elevenKeyInput.text.toString()
                Toast.makeText(this@MainActivity, "Keys Encrypted with AES-256 Locally!", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(geminiKeyInput)
        root.addView(elevenKeyInput)
        root.addView(saveKeysBtn)

        // =========================================================================
        // 3. SECURITY & SYSTEM PERMISSIONS SECTION
        // =========================================================================
        val secHeader = TextView(this).apply {
            text = "SECURITY & SYSTEM PERMISSIONS"
            textSize = 14f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 32, 0, 12)
        }
        root.addView(secHeader)

        val enrollVoiceBtn = Button(this).apply {
            text = "1. Enroll My Voice Print (Owner Biometric Lock)"
            setBackgroundColor(Color.parseColor("#0284C7"))
            setTextColor(Color.WHITE)
            setOnClickListener { enrollOwnerVoiceSample() }
        }
        val accessBtn = Button(this).apply {
            text = "2. Enable Screen & Hardware Control (Accessibility)"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val notifBtn = Button(this).apply {
            text = "3. Enable WhatsApp & Call Auto-Reply (Notification Access)"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
        }
        root.addView(enrollVoiceBtn)
        root.addView(accessBtn)
        root.addView(notifBtn)

        val tuitionInput = EditText(this).apply {
            hint = "Add Tuition/Homework (e.g., Physics Mon 5PM - Chapter 4 Numericals)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
        }
        val saveTuitionBtn = Button(this).apply {
            text = "Save Tuition/Homework to Encrypted Vault"
            setBackgroundColor(Color.parseColor("#0D9488"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val txt = tuitionInput.text.toString().trim()
                if (txt.isNotEmpty()) {
                    memoryStore.addTuitionOrHomework("Tuition/Homework", "Scheduled", txt)
                    tuitionInput.setText("")
                    consoleOutput.text = "Vault Updated:\n${memoryStore.getTuitionsSummary()}"
                }
            }
        }
        root.addView(tuitionInput)
        root.addView(saveTuitionBtn)

        val cmdInput = EditText(this).apply {
            hint = "Talk to Jarvis in English, বাংলা, or Banglish (tui/tumi)..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
        }
        val sendCmdBtn = Button(this).apply {
            text = "Execute Command & Hear Voice"
            setBackgroundColor(Color.parseColor("#2563EB"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val q = cmdInput.text.toString().trim()
                if (q.isNotEmpty()) {
                    uiScope.launch {
                        val response = brain.processOwnerCommand(q)
                        consoleOutput.text = "You: $q\n\nJARVIS: $response"
                        JarvisWakeWordService.speakOut(response)
                    }
                }
            }
        }
        root.addView(cmdInput)
        root.addView(sendCmdBtn)

        consoleOutput = TextView(this).apply {
            text = "JARVIS Pro Console Ready.\n\nEncrypted Tuitions:\n${memoryStore.getTuitionsSummary()}\n\nSaved Reels/Shorts Tricks:\n${memoryStore.getSavedReelsTricks()}"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 14f
            setPadding(16, 32, 16, 32)
        }
        root.addView(consoleOutput)

        scroll.addView(root)
        return scroll
    }

    private fun applyOfflineFilter(preset: String, caption: String) {
        val src = currentBitmap
        if (src == null) {
            Toast.makeText(this, "Pick a photo from Gallery or generate an AI image first!", Toast.LENGTH_SHORT).show()
            return
        }
        uiScope.launch {
            val params = JarvisCreativeStudio.EditParams(
                presetStyle = preset,
                contrast = currentContrast,
                saturation = currentSaturation,
                vignetteStrength = 0.35f,
                bottomCaption = caption
            )
            val edited = creativeStudio.applyProPhotoEdit(src, params)
            currentBitmap = edited
            imagePreview.setImageBitmap(edited)
            consoleOutput.text = "Applied '$preset' LUT + Vignette & Saved to Gallery (Pictures/JarvisAI)!"
            JarvisWakeWordService.speakOut("Sir, $preset filter lagie Gallery-te save kore diyechi!")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            val uri: Uri = data?.data ?: return
            contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                if (bmp != null) {
                    currentBitmap = bmp
                    imagePreview.setImageBitmap(bmp)
                    consoleOutput.text = "Photo loaded from Redmi Note 15 Gallery! You can now convert Board/PPT to Decorated PDF, apply filters, or run AI Remix."
                }
            }
        }
    }

    private fun enrollOwnerVoiceSample() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Please grant Microphone permission first!", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Recording 3 seconds! Say: 'Hey Jarvis, I am your boss'", Toast.LENGTH_LONG).show()
        Thread {
            val sampleRate = 16000
            val bufferSize = sampleRate * 3
            val audioBuffer = ShortArray(bufferSize)
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            recorder.startRecording()
            recorder.read(audioBuffer, 0, bufferSize)
            recorder.stop()
            recorder.release()

            val signature = biometricVerifier.extractVoiceSignature(audioBuffer, sampleRate)
            memoryStore.saveOwnerVoicePrint(signature)
            runOnUiThread {
                consoleOutput.text = "Voice Biometric Enrolled & Encrypted! Only your voice can command Jarvis now."
                JarvisWakeWordService.speakOut("Voice print locked, Boss. I will only obey your voice from now on.")
            }
        }.start()
    }

    private fun startJarvisService() {
        val intent = Intent(this, JarvisWakeWordService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 101)
    }
}
