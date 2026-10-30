package com.nexa.app

data class Provider(
    val name: String,
    val baseUrl: String,
    val model: String,
    val visionModel: String?
)

object Providers {
    fun build(groq: String, deepseek: String): Map<String, Provider> = mapOf(
        "groq" to Provider(
            name = "groq",
            baseUrl = "https://api.groq.com/openai/v1",
            model = "llama-3.3-70b-versatile",
            visionModel = "meta-llama/llama-4-scout-17b-16e-instruct"
        ),
        "deepseek" to Provider(
            name = "deepseek",
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-chat",
            visionModel = null
        ),
        "openrouter" to Provider(
            name = "openrouter",
            baseUrl = "https://openrouter.ai/api/v1",
            model = "cognitivecomputations/dolphin-mixtral-8x22b",
            visionModel = "meta-llama/llama-3.2-90b-vision-instruct"
        )
    )

    fun chain(forced: String, keyAvailable: (String) -> Boolean): List<String> {
        val base = listOf("groq", "deepseek", "openrouter")
        val start = if (forced != "auto" && forced in base) listOf(forced) + base.filter { it != forced }
                    else base
        return start.filter { keyAvailable(it) }
    }

    fun visionChain(forced: String, keyAvailable: (String) -> Boolean): List<String> {
        val order = if (forced == "auto" || forced == "deepseek") listOf("groq", "openrouter")
                    else listOf(forced) + listOf("groq", "openrouter").filter { it != forced }
        return order.filter { keyAvailable(it) }
    }
}
