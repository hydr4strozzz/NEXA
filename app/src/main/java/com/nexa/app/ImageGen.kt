package com.nexa.app

import java.net.URLEncoder

object ImageGen {
    fun pollinations(prompt: String, width: Int = 1024, height: Int = 1024): String {
        val seed = (System.currentTimeMillis() % 1000000)
        val enc = URLEncoder.encode(prompt, "UTF-8")
        return "https://image.pollinations.ai/prompt/$enc?width=$width&height=$height&nologo=true&seed=$seed&model=flux"
    }

    private val triggers = listOf(
        "/img ", "/image ", "/draw ", "/gen ",
        "generate image", "create image", "make image", "draw me", "draw a ",
        "image undak", "image create", "image generate", "oru image", "padam undak", "padam varak",
        "image thaa", "padam thaa", "photo thaa", "image venam", "padam venam"
    )

    fun isImageRequest(text: String): Boolean {
        val t = text.lowercase().trim()
        return triggers.any { t.startsWith(it) || t.contains(it) }
    }

    fun extractPrompt(text: String): String {
        var t = text.trim()
        for (p in listOf("/img ", "/image ", "/draw ", "/gen ")) {
            if (t.startsWith(p, ignoreCase = true)) { t = t.substring(p.length); break }
        }
        return t.ifBlank { text }
    }
}
