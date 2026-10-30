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
        )
    )

    fun chain(keyAvailable: (String) -> Boolean): List<String> {
        return listOf("groq", "deepseek").filter { keyAvailable(it) }
    }

    fun visionChain(keyAvailable: (String) -> Boolean): List<String> {
        return listOf("groq").filter { keyAvailable(it) }
    }
}
