package com.zenith.assistant

/**
 * Plug-in point for the on-device language model (e.g. llama.cpp via JNI).
 * No model is bundled yet, so NoModelEngine reports "not ready" honestly
 * instead of faking AI answers.
 */
interface LlmEngine {
    fun isReady(): Boolean
    fun generate(prompt: String): String
}

class NoModelEngine : LlmEngine {
    override fun isReady() = false
    override fun generate(prompt: String): String = error("No local model is loaded")
}
