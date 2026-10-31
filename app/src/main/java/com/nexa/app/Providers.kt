package com.nexa.app

data class Provider(
    val name: String,
    val baseUrl: String,
    val model: String,
    val visionModel: String?
)

object Providers {
    fun build(groq: String, deepseek: String, gemini: String, openrouter: String): Map<String, Provider> = mapOf(
        "openrouter" to Provider(
            name = "openrouter",
            baseUrl = "https://openrouter.ai/api/v1",
            model = "cognitivecomputations/dolphin-mixtral-8x22b", // True uncensored model
            visionModel = "openrouter/auto"
        ),
        "pollinations" to Provider(
            name = "pollinations",
            baseUrl = "https://text.pollinations.ai/openai",
            model = "mistral", // Pollinations Mistral is slightly less censored than OpenAI
            visionModel = "openai"
        )
    )

    // OpenRouter first. If it fails/rate-limits, fallback to Pollinations Mistral.
    // We removed Groq & Gemini because their safety filters are too strict.
    fun chain(keyAvailable: (String) -> Boolean): List<String> {
        return listOf("openrouter", "pollinations").filter { keyAvailable(it) }
    }

    fun visionChain(keyAvailable: (String) -> Boolean): List<String> {
        return listOf("openrouter", "pollinations").filter { keyAvailable(it) }
    }
}
