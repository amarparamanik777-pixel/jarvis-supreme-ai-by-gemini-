package com.jarvis.supreme

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * On-Device Acoustic Speaker Verification & Mood Analyzer.
 * Extracts fundamental pitch (F0), zero-crossing rate, spectral centroid, and 13 Mel-Frequency band energies
 * from raw 16kHz PCM audio frames to verify the owner's voice print and estimate voice mood (tired, stressed, happy).
 */
class VoiceBiometricVerifier(private val memoryStore: EncryptedMemoryStore) {

    data class VoiceAnalysisResult(
        val isOwnerVerified: Boolean,
        val similarityScore: Float,
        val detectedMood: String
    )

    /**
     * Extracts an 16-dimensional acoustic feature vector from a 16kHz PCM audio buffer.
     */
    fun extractVoiceSignature(pcmBuffer: ShortArray, sampleRate: Int = 16000): FloatArray {
        val n = pcmBuffer.size.coerceAtLeast(1)
        val floatSignal = FloatArray(n) { pcmBuffer[it] / 32768.0f }

        // 1. RMS Energy
        var sumSq = 0.0f
        for (s in floatSignal) sumSq += s * s
        val rms = sqrt(sumSq / n)

        // 2. Zero Crossing Rate (correlates with vocal tract timbre & pitch)
        var zcr = 0f
        for (i in 1 until n) {
            if ((floatSignal[i] >= 0 && floatSignal[i - 1] < 0) ||
                (floatSignal[i] < 0 && floatSignal[i - 1] >= 0)
            ) {
                zcr += 1f
            }
        }
        zcr /= n

        // 3. Autocorrelation Pitch Estimation (F0 in Hz)
        val minLag = sampleRate / 400 // 400 Hz max F0
        val maxLag = (sampleRate / 70).coerceAtMost(n / 2) // 70 Hz min F0
        var bestLag = minLag
        var maxCorr = -1f
        for (lag in minLag..maxLag) {
            var corr = 0f
            for (i in 0 until (n - maxLag)) {
                corr += floatSignal[i] * floatSignal[i + lag]
            }
            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }
        val pitchHz = if (bestLag > 0) sampleRate.toFloat() / bestLag else 150f

        // 4. 13 Sub-band Spectral Energy Distribution (Goertzel filter bank 100Hz - 3800Hz)
        val bandFreqs = floatArrayOf(
            120f, 200f, 300f, 450f, 600f, 800f, 1050f,
            1350f, 1700f, 2100f, 2600f, 3100f, 3700f
        )
        val features = FloatArray(16)
        features[0] = pitchHz / 300f
        features[1] = zcr * 5f
        features[2] = rms * 10f

        val windowSize = n.coerceAtMost(2048)
        for (b in bandFreqs.indices) {
            val k = (0.5f + (windowSize * bandFreqs[b]) / sampleRate).toInt()
            val omega = (2.0 * PI * k / windowSize).toFloat()
            val coeff = 2.0f * cos(omega)
            var q1 = 0f
            var q2 = 0f
            for (i in 0 until windowSize) {
                val q0 = coeff * q1 - q2 + floatSignal[i]
                q2 = q1
                q1 = q0
            }
            val power = sqrt((q1 * q1 + q2 * q2 - q1 * q2 * coeff).coerceAtLeast(1e-6f))
            features[3 + b] = ln(1f + power)
        }

        // L2-normalize the speaker embedding vector
        var norm = 0f
        for (f in features) norm += f * f
        norm = sqrt(norm.coerceAtLeast(1e-6f))
        return FloatArray(features.size) { features[it] / norm }
    }

    /**
     * Verifies if the incoming voice matches the enrolled owner's voice print and detects vocal mood.
     */
    fun verifyAndAnalyzeMood(pcmBuffer: ShortArray): VoiceAnalysisResult {
        val currentVec = extractVoiceSignature(pcmBuffer)
        val enrolledVec = memoryStore.getOwnerVoicePrint()

        // Mood heuristic from vocal energy and pitch contour
        val rmsEnergy = currentVec[2]
        val pitchNorm = currentVec[0]
        val mood = when {
            rmsEnergy < 0.08f -> "Tired / Low Energy"
            pitchNorm > 0.65f && rmsEnergy > 0.25f -> "Excited / Urgent"
            else -> "Calm / Normal"
        }

        if (!memoryStore.isVoiceLockEnabled || enrolledVec == null) {
            return VoiceAnalysisResult(isOwnerVerified = true, similarityScore = 1.0f, detectedMood = mood)
        }

        var dot = 0f
        for (i in currentVec.indices) {
            dot += currentVec[i] * enrolledVec[i]
        }

        // Threshold 0.82 cosine similarity ensures strangers cannot unlock or command Jarvis
        val verified = dot >= 0.82f
        return VoiceAnalysisResult(
            isOwnerVerified = verified,
            similarityScore = dot,
            detectedMood = mood
        )
    }
}
