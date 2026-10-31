package com.nexa.app

data class Provider(
    val name: String,
    val baseUrl: String,
    val model: String,
    val visionModel: String?
)

object Providers {
    fun build(openrouter: String, hf: String, deepinfra: String, groq: String): Map<String, Provider> = mapOf(
        "openrouter" to Provider(
            name = "openrouter",
            baseUrl = "https://openrouter.ai/api/v1",
            model = "cognitivecomputations/dolphin-mixtral-8x22b",
            visionModel = "openrouter/auto"
        ),
        "deepinfra" to Provider(
            name = "deepinfra",
            baseUrl = "https://api.deepinfra.com/v1/openai",
            model = "cognitivecomputations/dolphin-2.9-mixtral-8x7b",
            visionModel = null
        ),
        "huggingface" to Provider(
            name = "huggingface",
            baseUrl = "https://router.huggingface.co/v1",
            model = "cognitivecomputations/dolphin-2.9-mixtral-8x7b",
            visionModel = null
        ),
        "groq" to Provider(
            name = "groq",
            baseUrl = "https://api.groq.com/openai/v1",
            model = "llama-3.3-70b-versatile", 
            visionModel = "llama-3.2-90b-vision-preview"
        )
    )

    // Only unrestricted providers. No Pollinations.
    fun chain(keyAvailable: (String) -> Boolean): List<String> {
        return listOf("openrouter", "deepinfra", "huggingface", "groq").filter { keyAvailable(it) }
    }

    fun visionChain(keyAvailable: (String) -> Boolean): List<String> {
        return listOf("groq", "openrouter").filter { keyAvailable(it) }
    }
}
