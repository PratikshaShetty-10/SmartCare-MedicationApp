
package com.yourteam.smartcare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.min

class ExpiryDateRegionDetector {

    companion object {
        private const val MODEL_FILE = "expiry_date_detector.onnx"
        private const val INPUT_SIZE = 416
        private const val CONFIDENCE_THRESHOLD = 0.25f
    }

    data class DateRegion(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val confidence: Float
    )

    data class DetectionResult(
        val region: DateRegion?,
        val croppedBitmap: Bitmap?,
        val message: String
    )

    private val environment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var inputName: String? = null

    private data class LetterboxInfo(
        val scale: Float,
        val left: Int,
        val top: Int
    )

    @Synchronized
    fun initialize(context: Context) {
        if (session != null) return

        val modelBytes = context.applicationContext.assets
            .open(MODEL_FILE)
            .use { it.readBytes() }

        val options = OrtSession.SessionOptions()

        try {
            val newSession = environment.createSession(modelBytes, options)
            session = newSession
            inputName = newSession.inputNames.firstOrNull()
                ?: throw IllegalStateException("The model has no input")
        } catch (e: Exception) {
            session?.close()
            session = null
            inputName = null
            throw IllegalStateException(
                "Could not initialize $MODEL_FILE",
                e
            )
        } finally {
            options.close()
        }
    }

    @Synchronized
    fun detect(context: Context, bitmap: Bitmap): DetectionResult {
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return DetectionResult(
                null, null, "Invalid image. Please capture it again."
            )
        }

        initialize(context)

        val activeSession = session
            ?: throw IllegalStateException("Detector is not initialized")
        val activeInputName = inputName
            ?: throw IllegalStateException("Model input is unavailable")

        val (inputData, letterbox) = preprocess(bitmap)

        val detections = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(inputData),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        ).use { inputTensor ->
            activeSession.run(mapOf(activeInputName to inputTensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val batch = result[0].value as? Array<Array<FloatArray>>
                    ?: throw IllegalStateException(
                        "Unexpected expiry model output format"
                    )

                batch.firstOrNull()
                    ?: throw IllegalStateException("The model returned no output")
            }
        }

        val region = decodeBestRegion(
            detections,
            bitmap.width,
            bitmap.height,
            letterbox
        )

        if (region == null) {
            return DetectionResult(
                null,
                null,
                "Expiry-date region not detected. Try a clearer, closer photo."
            )
        }

        val cropped = Bitmap.createBitmap(
            bitmap,
            region.left,
            region.top,
            region.right - region.left,
            region.bottom - region.top
        )

        return DetectionResult(region, cropped, "Date region detected")
    }

    private fun preprocess(bitmap: Bitmap): Pair<FloatArray, LetterboxInfo> {
        val scale = min(
            INPUT_SIZE.toFloat() / bitmap.width,
            INPUT_SIZE.toFloat() / bitmap.height
        )

        val resizedWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val resizedHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)

        val left = (INPUT_SIZE - resizedWidth) / 2
        val top = (INPUT_SIZE - resizedHeight) / 2

        val resized = Bitmap.createScaledBitmap(
            bitmap, resizedWidth, resizedHeight, true
        )

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE) {
            Color.rgb(114, 114, 114)
        }

        val resizedPixels = IntArray(resizedWidth * resizedHeight)
        resized.getPixels(
            resizedPixels, 0, resizedWidth,
            0, 0, resizedWidth, resizedHeight
        )

        for (y in 0 until resizedHeight) {
            System.arraycopy(
                resizedPixels,
                y * resizedWidth,
                pixels,
                (y + top) * INPUT_SIZE + left,
                resizedWidth
            )
        }

        val planeSize = INPUT_SIZE * INPUT_SIZE
        val input = FloatArray(3 * planeSize)

        for (i in pixels.indices) {
            input[i] = Color.red(pixels[i]) / 255.0f
            input[planeSize + i] = Color.green(pixels[i]) / 255.0f
            input[2 * planeSize + i] = Color.blue(pixels[i]) / 255.0f
        }

        if (resized !== bitmap) {
            resized.recycle()
        }

        return input to LetterboxInfo(scale, left, top)
    }

    private fun decodeBestRegion(
        output: Array<FloatArray>,
        originalWidth: Int,
        originalHeight: Int,
        letterbox: LetterboxInfo
    ): DateRegion? {
        if (output.size != 5) {
            throw IllegalStateException(
                "Expected 5 output channels, got ${output.size}"
            )
        }

        val anchorCount = output[0].size
        if (anchorCount == 0 || output.any { it.size != anchorCount }) {
            throw IllegalStateException("Invalid detector output dimensions")
        }

        var best: DateRegion? = null
        var bestConfidence = CONFIDENCE_THRESHOLD

        for (i in 0 until anchorCount) {
            val confidence = output[4][i]

            if (!confidence.isFinite() || confidence <= bestConfidence) {
                continue
            }

            val cx = output[0][i]
            val cy = output[1][i]
            val width = output[2][i]
            val height = output[3][i]

            if (
                !cx.isFinite() || !cy.isFinite() ||
                !width.isFinite() || !height.isFinite() ||
                width <= 0f || height <= 0f
            ) {
                continue
            }

            val left = (
                    (cx - width / 2f - letterbox.left) / letterbox.scale
                    ).toInt().coerceIn(0, originalWidth)

            val top = (
                    (cy - height / 2f - letterbox.top) / letterbox.scale
                    ).toInt().coerceIn(0, originalHeight)

            val right = (
                    (cx + width / 2f - letterbox.left) / letterbox.scale
                    ).toInt().coerceIn(0, originalWidth)

            val bottom = (
                    (cy + height / 2f - letterbox.top) / letterbox.scale
                    ).toInt().coerceIn(0, originalHeight)

            if (right <= left || bottom <= top) continue

            bestConfidence = confidence
            best = DateRegion(
                left, top, right, bottom, confidence
            )
        }

        return best
    }

    @Synchronized
    fun close() {
        session?.close()
        session = null
        inputName = null
    }
}
