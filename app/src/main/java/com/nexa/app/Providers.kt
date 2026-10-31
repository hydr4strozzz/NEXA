package com.nexa.app

data class Provider(
    val name: String,
    val baseUrl: String,
    val model: String,
    val visionModel: String?
)

object Providers {
    fun build(groq: String, deepseek: String, gemini: String, openrouter: String): Map<String, Provider> = mapOf(
        "groq" to Provider(
            name = "groq",
            baseUrl = "https://api.groq.com/openai/v1",
            model = "llama-3.1-8b-instant", // Blazing fast
            visionModel = "llama-3.2-90b-vision-preview"
        ),
        "gemini" to Provider(
            name = "gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
            model = "gemini-1.5-flash-latest",
            visionModel = "gemini-1.5-flash-latest"
        ),
        "openrouter" to Provider(
            name = "openrouter",
            baseUrl = "https://openrouter.ai/api/v1",
            model = "cognitivecomputations/dolphin-mixtral-8x22b",
            visionModel = "openrouter/auto"
        ),
        "pollinations" to Provider(
            name = "pollinations",
            baseUrl = "https://text.pollinations.ai/openai",
            model = "openai-fast",
            visionModel = "openai"
        )
    )

    fun chain(keyAvailable: (String) -> Boolean): List<String> {
        return listOf("groq", "gemini", "openrouter", "pollinations").filter { keyAvailable(it) }
    }

    fun visionChain(keyAvailable: (String) -> Boolean): List<String> {
        return listOf("groq", "gemini", "pollinations").filter { keyAvailable(it) }
    }
}
