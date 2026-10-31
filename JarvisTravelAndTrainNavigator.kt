package com.jarvis.supreme

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * JARVIS SUPREME TRAVEL, INDIAN RAILWAYS & LAST-MILE (TOTO / AUTO / TAXI / WALK) NAVIGATOR:
 *
 * 1. Initial Destination Discovery (`planTripDiscovery`):
 *    - Opens Google Maps route preview from your current location (defaulting to Durgapur, West Bengal or live GPS).
 *    - Calculates exact road & rail distance in kilometres and estimated travel duration.
 *    - Proactively asks: "Sir, [Destination] is X km from your location. How many members are going, and which transport/vehicle are you planning to take?"
 *    - Recommends the optimal transport (Bike, Car, Train, Bus) based on distance and group size.
 *
 * 2. Complete Train & Station-by-Station Route Intelligence (`planCompleteTrainJourney`):
 *    - Identifies nearest boarding station (e.g., Durgapur Jn [DGR] / Asansol [ASN]) and destination station.
 *    - Lists train timings, train numbers/names, total number of intermediate stations crossed,
 *      every intermediate station name + crossing time, and the exact railway route/line (e.g., Howrah-Bardhaman-Durgapur Main Line).
 *    - Provides exact Last-Mile Navigation after alighting at the destination station:
 *      • Distance & reaching time from the destination railway station to your final spot.
 *      • Recommends whether to walk (<1 km), take a Toto / E-Rickshaw (1–5 km), Auto-Rickshaw, or Taxi/Cab (>5 km or with luggage/family).
 */
class JarvisTravelAndTrainNavigator(
    private val context: Context,
    private val memoryStore: EncryptedMemoryStore,
    private val hardwareController: JarvisHardwareController
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    /**
     * Launches Google Maps Directions to the target destination and returns a full interactive travel briefing.
     */
    suspend fun planTripAndTrainIntelligence(
        userQuery: String,
        currentCityOrGps: String = "Durgapur, West Bengal"
    ): String = withContext(Dispatchers.IO) {
        val lower = userQuery.lowercase()

        // Extract destination if user says "I want to go to [Place]" or "ami [Place] jabo"
        val destination = userQuery
            .replace(Regex("^(jarvis|i want to go to|going to|take me to|navigate to|ami|jete chai|jabo)\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { "Kolkata" }

        // If user explicitly asks to open Maps or starts a new trip query, open Google Maps route view
        if (lower.contains("go to") || lower.contains("maps") || lower.contains("jabo") || lower.contains("jete chai")) {
            hardwareController.ensureInternetThenRun {
                val encodedDest = URLEncoder.encode(destination, "UTF-8")
                val mapIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encodedDest")
                ).apply {
                    setPackage("com.google.android.apps.maps")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(mapIntent)
                } catch (_: Exception) {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encodedDest")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(browserIntent)
                }
            }
        }

        val prompt = """
            You are JARVIS Supreme Travel & Indian Railways Navigator for a user currently located in $currentCityOrGps, India.
            User travel query: "$userQuery"

            Provide a crisp, structured, highly accurate travel & railway intelligence briefing addressing the user as "Sir" (and "Boss"):
            1. DISTANCE & TRIP CHECK:
               - Exact approximate distance in kilometres (km) from $currentCityOrGps to the destination and estimated road vs. train time.
               - If the user has not yet specified group size or vehicle, ask: "Sir, how many members are travelling with you, and which vehicle/transport do you prefer?"
               - Recommend which transport/vehicle Sir should prepare (e.g., Bike/Scooter for <15 km solo, Car/Cab for 3-5 family members, or Express/Local Train for intercity travel).
            2. INDIAN RAILWAYS FULL ROUTE & STATION BREAKDOWN (Include whenever train is mentioned or distance > 20 km):
               - Nearest Boarding Station (e.g., Durgapur Jn - DGR) & Reaching/Destination Station.
               - Key Train Names, Numbers & Departure/Arrival Timings.
               - Exact Railway Route Corridor (which line/junctions the train goes through).
               - Total Number of Stations the train will cross, plus the ordered list of Intermediate Crossing Stations with approximate crossing/halt times.
            3. LAST-MILE STATION-TO-SPOT TRANSPORT (TOTO / AUTO / TAXI / WALKING):
               - Exact distance (km) and reaching time from the Reaching Railway Station to the final destination spot.
               - Clear recommendation on whether Sir should Walk (<800m), take a Toto / E-Rickshaw (short local Bazar/town hops 1-4 km), Auto-Rickshaw, or book a Taxi/Cab (for longer distance or group travel), with estimated fare range in INR.
        """.trimIndent()

        queryTravelLLM(prompt, destination, currentCityOrGps)
    }

    private fun queryTravelLLM(prompt: String, destination: String, origin: String): String {
        val apiKey = memoryStore.apiKey
        if (apiKey.isBlank()) {
            return """
                Right away, Sir. Opening Google Maps from $origin to $destination.
                • Distance & Vehicle Check: Sir, how many members are going with you, and which transport (Train, Car, Bike, or Bus) are you planning to take?
                • Train & Last-Mile Ready: If you are going by train from Durgapur Jn (DGR), tell me your exact destination spot—I will list the train timings, total crossing stations, route junctions, and whether you should take a Toto, Auto, Taxi, or walk after reaching the station! (Paste your free Gemini API key on the dashboard for live station-by-station timetables).
            """.trimIndent()
        }

        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                })
            }
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val str = resp.body?.string() ?: ""
                val json = JSONObject(str)
                json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()
            }
        } catch (e: Exception) {
            "Right away, Sir. Google Maps route to $destination is open. How many members are travelling with you, Boss?"
        }
    }
}
