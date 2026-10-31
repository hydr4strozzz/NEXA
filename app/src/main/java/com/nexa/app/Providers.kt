package com.nexa.app

data class Provider(
    val name: String,
    val baseUrl: String,
    val model: String,
    val visionModel: String?
)

object Providers {
    fun build(groq: String, deepseek: String, gemini: String): Map<String, Provider> = mapOf(
        "groq" to Provider(
            name = "groq",
            baseUrl = "https://api.groq.com/openai/v1",
            model = "llama-3.3-70b-versatile",
            visionModel = "llama-3.2-90b-vision-preview"
        ),
        "gemini" to Provider(
            name = "gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
            model = "gemini-1.5-flash-latest",
            visionModel = "gemini-1.5-flash-latest"
        ),
        "pollinations" to Provider(
            name = "pollinations",
            baseUrl = "https://text.pollinations.ai/openai",
            model = "openai",
            visionModel = "openai"
        ),
        "deepseek" to Provider(
            name = "deepseek",
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-chat",
            visionModel = null
        )
    )

    // Fast, Smart, Unlimited, Paid
    fun chain(keyAvailable: (String) -> Boolean): List<String> {
        return listOf("groq", "gemini", "pollinations", "deepseek").filter { keyAvailable(it) }
    }

    fun visionChain(keyAvailable: (String) -> Boolean): List<String> {
        return listOf("groq", "gemini", "pollinations").filter { keyAvailable(it) }
    }
}
