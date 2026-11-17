package com.nova.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

/**
 * Offline AI dimaag. Ek MediaPipe-compatible .task model ko
 * /Android/data/com.nova.ai/files/model.task naam se rakhiye.
 */
class LocalLlm(private val ctx: Context) {
    private val file = File(ctx.getExternalFilesDir(null), "model.task")
    private var engine: LlmInference? = null

    fun available() = file.exists()
    fun path(): String = file.absolutePath

    @Synchronized
    fun ask(prompt: String): String {
        if (!available()) return ""
        if (engine == null) {
            val opts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(512)
                .build()
            engine = LlmInference.createFromOptions(ctx, opts)
        }
        return engine!!.generateResponse(prompt).trim()
    }
}
