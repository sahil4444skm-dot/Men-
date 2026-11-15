package com.example.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiHelper {

    private const val MODEL = "gemini-2.0-flash"

    fun ask(ctx: Context, question: String): String {
        val key = ctx.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
            .getString("api_key", "") ?: ""
        if (key.isBlank()) return "Pehle Jarvis app mein Gemini API key save kijiye."

        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            val prompt = "Tum Jarvis ho, ek Hindi-English voice assistant. " +
                    "Jawab saaf, chhota aur Hinglish mein do, zyada se zyada 3-4 line mein. " +
                    "Sawal: $question"

            val body = JSONObject().put(
                "contents", JSONArray().put(
                    JSONObject().put(
                        "parts", JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val response = (if (code in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader().readText()
            if (code !in 200..299) return "AI se jawab nahi mila. API key check kijiye."

            JSONObject(response).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getString("text").trim()
        } catch (e: Exception) {
            "Internet ya AI mein dikkat aayi. Internet on hai ya nahi, check kijiye."
        }
    }
}
