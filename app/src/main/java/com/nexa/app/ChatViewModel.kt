package com.nexa.app

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import org.json.JSONObject

data class Message(
    val role: String,
    val content: String,
    val imageB64: String? = null,
    val imageUrl: String? = null,
    val pending: Boolean = false,
    val provider: String? = null
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val messages = mutableStateListOf<Message>()
    private val api = ApiClient()
    private val prefs = app.getSharedPreferences("nexa", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    private val bundledGroq: String
    private val bundledDeepseek: String

    init {
        var g = ""; var d = ""
        try {
            val json = app.assets.open("config.json").bufferedReader().use { it.readText() }
            val j = JSONObject(json)
            g = j.optString("groq", ""); d = j.optString("deepseek", "")
        } catch (_: Exception) { }
        bundledGroq = g; bundledDeepseek = d
    }

    private fun groqKey() = prefs.getString("groqKey", "").let { if (it.isNullOrBlank()) bundledGroq else it }
    private fun deepseekKey() = prefs.getString("deepseekKey", "").let { if (it.isNullOrBlank()) bundledDeepseek else it }

    private fun providerMap() = Providers.build(groqKey(), deepseekKey())

    private fun keyAvailable(name: String): Boolean = when (name) {
        "groq" -> groqKey().isNotBlank()
        "deepseek" -> deepseekKey().isNotBlank()
        "openrouter" -> !prefs.getString("orKey", "").isNullOrBlank()
        else -> false
    }

    private fun keyFor(name: String): String = when (name) {
        "groq" -> groqKey()
        "deepseek" -> deepseekKey()
        "openrouter" -> prefs.getString("orKey", "").orEmpty()
        else -> ""
    }

    fun send(text: String, imageB64: String?, fileText: String?) {
        if (text.isBlank() && imageB64 == null && fileText == null) return

        if (ImageGen.isImageRequest(text) && imageB64 == null) {
            val prompt = ImageGen.extractPrompt(text)
            val url = ImageGen.pollinations(prompt)
            messages.add(Message("user", text))
            messages.add(Message("assistant", "generating: $prompt", imageUrl = url, provider = "pollinations"))
            return
        }

        val userText = buildString {
            if (fileText != null && fileText.isNotBlank()) {
                append("[file content]\n").append(fileText.take(20000)).append("\n[/file]\n\n")
            }
            append(text)
        }.trim().ifBlank { text }

        val forced = prefs.getString("forceProvider", "auto") ?: "auto"
        messages.add(Message("user", text.ifBlank { "[file]" }, imageB64))
        messages.add(Message("assistant", "", pending = true))
        val idx = messages.lastIndex

        val chain = if (imageB64 != null) Providers.visionChain(forced, ::keyAvailable)
                    else Providers.chain(forced, ::keyAvailable)

        if (chain.isEmpty()) {
            messages[idx] = messages[idx].copy(content = "[no provider key - open settings]", pending = false)
            return
        }

        val history = messages.filter { !it.pending }.mapIndexed { i, m ->
            if (i == messages.size - 2 && userText.isNotBlank())
                WireMessage(m.role, if (m.role == "user") userText else m.content, m.imageB64)
            else
                WireMessage(m.role, m.content, m.imageB64)
        }

        tryChain(chain, 0, history, idx)
    }

    private fun tryChain(chain: List<String>, pos: Int, history: List<WireMessage>, idx: Int) {
        if (pos >= chain.size) {
            main.post {
                val cur = messages[idx]
                if (cur.content.isBlank()) messages[idx] = cur.copy(content = "[all providers failed]", pending = false)
                else messages[idx] = cur.copy(pending = false)
            }
            return
        }
        val name = chain[pos]
        val p = providerMap()[name] ?: run { tryChain(chain, pos + 1, history, idx); return }
        val apiKey = keyFor(name)
        if (apiKey.isBlank()) { tryChain(chain, pos + 1, history, idx); return }

        val hasImage = history.lastOrNull()?.imageB64 != null
        val model = if (hasImage && p.visionModel != null) p.visionModel else p.model

        var gotToken = false

        api.streamChat(
            baseUrl = p.baseUrl,
            apiKey = apiKey,
            model = model,
            system = Persona.systemPrompt,
            history = history,
            onToken = { tok ->
                gotToken = true
                main.post {
                    val cur = messages[idx]
                    messages[idx] = cur.copy(content = cur.content + tok, pending = false, provider = name)
                }
            },
            onDone = { main.post { messages[idx] = messages[idx].copy(pending = false) } },
            onError = {
                if (!gotToken) {
                    main.post { messages[idx] = messages[idx].copy(content = "", provider = null) }
                    tryChain(chain, pos + 1, history, idx)
                } else {
                    main.post { messages[idx] = messages[idx].copy(pending = false) }
                }
            }
        )
    }

    fun prefs(): android.content.SharedPreferences = prefs
}
