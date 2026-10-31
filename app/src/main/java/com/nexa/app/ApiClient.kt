package com.nexa.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WireMessage(val role: String, val text: String, val imageB64: String? = null)

class ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS) // Fast fail: 5s instead of 15s
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    fun streamChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        system: String,
        history: List<WireMessage>,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        for (m in history) {
            val obj = JSONObject().put("role", m.role)
            if (m.imageB64 != null) {
                val parts = JSONArray()
                if (m.text.isNotBlank()) parts.put(JSONObject().put("type", "text").put("text", m.text))
                parts.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${m.imageB64}")))
                obj.put("content", parts)
            } else {
                obj.put("content", m.text)
            }
            messages.put(obj)
        }
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("stream", true)
            .put("temperature", 0.9)
            .put("max_tokens", 4096)

        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://localhost")
            .addHeader("X-Title", "NEXA")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        Thread {
            try {
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string()?.take(150)?.replace("\n", " ") ?: "No body"
                    onError("HTTP ${resp.code}: $errBody")
                    return@Thread
                }
                resp.body?.source()?.use { src ->
                    while (!src.exhausted()) {
                        val line = src.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        try {
                            val tok = JSONObject(data).getJSONArray("choices").getJSONObject(0).optJSONObject("delta")?.optString("content").orEmpty()
                            if (tok.isNotEmpty()) onToken(tok)
                        } catch (_: Exception) { }
                    }
                }
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "network error")
            }
        }.start()
    }
}
