package com.nova.ai

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Photo ko on-device samajhta hai: objects + likha hua text. */
object Vision {
    suspend fun describe(bmp: Bitmap): String {
        val img = InputImage.fromBitmap(bmp, 0)
        val labels = try {
            ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS).process(img).await()
                .take(6).joinToString(", ") { it.text }
        } catch (e: Exception) { "" }
        val text = try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img).await().text.trim()
        } catch (e: Exception) { "" }
        return buildString {
            if (labels.isNotEmpty()) append("Dikhne wali cheezein: $labels. ")
            if (text.isNotEmpty()) append("Photo mein likha text: $text")
            if (isEmpty()) append("Kuch saaf pehchana nahi gaya.")
        }
    }
}
