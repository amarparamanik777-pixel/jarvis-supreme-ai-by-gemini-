package com.jarvis.supreme

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * JARVIS LIVE LENS (Camera + Voice Multimodal Assistant like Gemini Live):
 * Point your Redmi Note 15 5G camera at:
 *  - Any classroom board work or PPT slide -> Turns it into a Decorated Color-Coded PDF!
 *  - Any Physics/Math problem in your notebook -> Solves it step-by-step and explains it aloud in English, বাংলা, or Banglish!
 *  - Any electronic component, gadget, or object -> Tells you what it is, how it works, and how to build/fix it!
 */
class JarvisLensActivity : AppCompatActivity() {

    private lateinit var memoryStore: EncryptedMemoryStore
    private lateinit var docEngine: JarvisDocumentAndLectureEngine
    private lateinit var lensImageView: ImageView
    private lateinit var lensOutputText: TextView
    private lateinit var questionInput: EditText
    private var capturedFrame: Bitmap? = null
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private val CAMERA_CAPTURE_REQ = 301
    private val VOICE_PROMPT_REQ = 302

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        memoryStore = EncryptedMemoryStore(this)
        docEngine = JarvisDocumentAndLectureEngine(this, memoryStore)

        setContentView(buildLensUI())
        JarvisWakeWordService.speakOut("Jarvis Lens activated, Boss! Point your camera at any board work, book page, or object and ask me anything.")
    }

    private fun buildLensUI(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#090D16"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 56, 42, 56)
        }

        val title = TextView(this).apply {
            text = "JARVIS LIVE LENS (CAMERA + VOICE AI)"
            textSize = 22f
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val sub = TextView(this).apply {
            text = "Snap any Board Work, PPT, Book Page, or Object & Talk Live in English, বাংলা, or Banglish"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 8, 0, 28)
        }
        root.addView(title)
        root.addView(sub)

        lensImageView = ImageView(this).apply {
            minimumHeight = 540
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(12, 12, 12, 12)
        }
        root.addView(lensImageView)

        val captureBtn = Button(this).apply {
            text = "1. Capture Live Camera Frame (Jarvis Eye)"
            setBackgroundColor(Color.parseColor("#0284C7"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val camIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(camIntent, CAMERA_CAPTURE_REQ)
            }
        }
        root.addView(captureBtn)

        questionInput = EditText(this).apply {
            hint = "Ask about this view (e.g., Explain this board work in Bangla / Solve this math)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
        }
        root.addView(questionInput)

        val voiceAskBtn = Button(this).apply {
            text = "2. Speak Question to Jarvis Lens (Voice + Camera)"
            setBackgroundColor(Color.parseColor("#2563EB"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Jarvis about what the camera sees...")
                }
                startActivityForResult(speechIntent, VOICE_PROMPT_REQ)
            }
        }
        root.addView(voiceAskBtn)

        val analyzeBtn = Button(this).apply {
            text = "3. Analyze Camera View & Speak Explanation"
            setBackgroundColor(Color.parseColor("#0D9488"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                runLensAnalysis(questionInput.text.toString().ifBlank { "Explain everything visible in this image clearly in Banglish/English and solve any problem shown." })
            }
        }
        root.addView(analyzeBtn)

        val boardToPdfBtn = Button(this).apply {
            text = "4. Convert This Board Work / PPT Frame into Decorated PDF"
            setBackgroundColor(Color.parseColor("#7C3AED"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val bmp = capturedFrame
                if (bmp == null) {
                    JarvisWakeWordService.speakOut("Sir, agey camera diye board ba PPT-r chobi tule nin!")
                } else {
                    lensOutputText.text = "Converting Board Work / PPT into Color-Coded Decorated PDF..."
                    JarvisWakeWordService.speakOut("Board-er lekha ar diagram pore Decorated PDF banachhi Boss!")
                    uiScope.launch {
                        val (notes, pdfFile) = docEngine.convertBoardOrPptPhotoToPdf(bmp, questionInput.text.toString())
                        lensOutputText.text = "Decorated PDF Saved: ${pdfFile?.absolutePath}\n\n$notes"
                        JarvisWakeWordService.speakOut("Boss! Board work theke full decorated PDF ready hoye geche!")
                    }
                }
            }
        }
        root.addView(boardToPdfBtn)

        lensOutputText = TextView(this).apply {
            text = "Jarvis Lens Ready. Tap '1. Capture Live Camera Frame' to start."
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 14f
            setPadding(12, 28, 12, 28)
        }
        root.addView(lensOutputText)

        scroll.addView(root)
        return scroll
    }

    private fun runLensAnalysis(query: String) {
        lensOutputText.text = "Jarvis Lens analyzing visual frame..."
        uiScope.launch {
            val ans = docEngine.callGeminiTextOrVision(
                "You are JARVIS Live Lens looking through the user's Redmi Note 15 5G camera. Answer naturally like a smart friend and tutor: $query",
                capturedFrame
            )
            lensOutputText.text = ans
            JarvisWakeWordService.speakOut(ans.take(320))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_CAPTURE_REQ && resultCode == Activity.RESULT_OK) {
            val bmp = data?.extras?.get("data") as? Bitmap
            if (bmp != null) {
                capturedFrame = bmp
                lensImageView.setImageBitmap(bmp)
                JarvisWakeWordService.speakOut("Frame locked, Sir! Ebar bolun ei chobi-ti niye ki korbo, Boss?")
            }
        } else if (requestCode == VOICE_PROMPT_REQ && resultCode == Activity.RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            if (spoken.isNotBlank()) {
                questionInput.setText(spoken)
                runLensAnalysis(spoken)
            }
        }
    }
}
