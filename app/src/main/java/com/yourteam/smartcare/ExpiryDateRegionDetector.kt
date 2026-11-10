package com.yourteam.smartcare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ExpiryDateRegionDetector {

    companion object {
        private const val MODEL_FILE = "expiry_date_detector.onnx"
        private const val INPUT_SIZE = 416
        private const val CONFIDENCE_THRESHOLD = 0.25f
        private const val TAG = "ExpiryDetector"
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

    private data class Box(
        val cx: Float,
        val cy: Float,
        val w: Float,
        val h: Float,
        val confidence: Float
    ) {
        val left: Float
            get() = cx - w / 2f

        val right: Float
            get() = cx + w / 2f

        val top: Float
            get() = cy - h / 2f

        val bottom: Float
            get() = cy + h / 2f
    }

    @Synchronized
    fun initialize(context: Context) {
        if (session != null) return

        val modelBytes = context.applicationContext.assets
            .open(MODEL_FILE)
            .use { it.readBytes() }

        val options = OrtSession.SessionOptions()

        try {
            val newSession =
                environment.createSession(modelBytes, options)

            session = newSession

            inputName = newSession.inputNames.firstOrNull()
                ?: throw IllegalStateException(
                    "The model has no input"
                )
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
    fun detect(
        context: Context,
        bitmap: Bitmap
    ): DetectionResult {

        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return DetectionResult(
                null,
                null,
                "Invalid image. Please capture it again."
            )
        }

        initialize(context)

        val activeSession = session
            ?: throw IllegalStateException(
                "Detector is not initialized"
            )

        val activeInputName = inputName
            ?: throw IllegalStateException(
                "Model input is unavailable"
            )

        val (inputData, letterbox) = preprocess(bitmap)

        val detections = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(inputData),
            longArrayOf(
                1L,
                3L,
                INPUT_SIZE.toLong(),
                INPUT_SIZE.toLong()
            )
        ).use { inputTensor ->

            activeSession.run(
                mapOf(activeInputName to inputTensor)
            ).use { result ->

                @Suppress("UNCHECKED_CAST")
                val batch =
                    result[0].value as? Array<Array<FloatArray>>
                        ?: throw IllegalStateException(
                            "Unexpected expiry model output format"
                        )

                batch.firstOrNull()
                    ?: throw IllegalStateException(
                        "The model returned no output"
                    )
            }
        }

        val region = decodeBestRegion(
            output = detections,
            originalWidth = bitmap.width,
            originalHeight = bitmap.height,
            letterbox = letterbox
        )

        if (region == null) {
            return DetectionResult(
                null,
                null,
                "Expiry-date region not detected. Try a clearer, closer photo."
            )
        }

        val cropWidth = region.right - region.left
        val cropHeight = region.bottom - region.top

        if (cropWidth <= 0 || cropHeight <= 0) {
            return DetectionResult(
                null,
                null,
                "Detected region is invalid. Please try again."
            )
        }

        Log.d(
            TAG,
            "Selected box: confidence=${region.confidence}, " +
                    "left=${region.left}, top=${region.top}, " +
                    "right=${region.right}, bottom=${region.bottom}, " +
                    "crop=${cropWidth}x${cropHeight}"
        )

        val cropped = Bitmap.createBitmap(
            bitmap,
            region.left,
            region.top,
            cropWidth,
            cropHeight
        )

        return DetectionResult(
            region,
            cropped,
            "Date region detected"
        )
    }

    private fun preprocess(
        bitmap: Bitmap
    ): Pair<FloatArray, LetterboxInfo> {

        val scale = min(
            INPUT_SIZE.toFloat() / bitmap.width,
            INPUT_SIZE.toFloat() / bitmap.height
        )

        val resizedWidth =
            (bitmap.width * scale).toInt().coerceAtLeast(1)

        val resizedHeight =
            (bitmap.height * scale).toInt().coerceAtLeast(1)

        val left = (INPUT_SIZE - resizedWidth) / 2
        val top = (INPUT_SIZE - resizedHeight) / 2

        val resized = Bitmap.createScaledBitmap(
            bitmap,
            resizedWidth,
            resizedHeight,
            true
        )

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE) {
            Color.rgb(114, 114, 114)
        }

        val resizedPixels =
            IntArray(resizedWidth * resizedHeight)

        resized.getPixels(
            resizedPixels,
            0,
            resizedWidth,
            0,
            0,
            resizedWidth,
            resizedHeight
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
            input[planeSize + i] =
                Color.green(pixels[i]) / 255.0f
            input[2 * planeSize + i] =
                Color.blue(pixels[i]) / 255.0f
        }

        if (resized !== bitmap) {
            resized.recycle()
        }

        return input to LetterboxInfo(
            scale = scale,
            left = left,
            top = top
        )
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

        if (
            anchorCount == 0 ||
            output.any { it.size != anchorCount }
        ) {
            throw IllegalStateException(
                "Invalid detector output dimensions"
            )
        }

        Log.d(
            TAG,
            "Image=${originalWidth}x${originalHeight}, " +
                    "scale=${letterbox.scale}, " +
                    "padLeft=${letterbox.left}, " +
                    "padTop=${letterbox.top}"
        )

        Log.d(
            TAG,
            "Output ranges: " +
                    "cx=${output[0].minOrNull()}..${output[0].maxOrNull()}, " +
                    "cy=${output[1].minOrNull()}..${output[1].maxOrNull()}, " +
                    "w=${output[2].minOrNull()}..${output[2].maxOrNull()}, " +
                    "h=${output[3].minOrNull()}..${output[3].maxOrNull()}, " +
                    "confidence=${output[4].minOrNull()}..${output[4].maxOrNull()}"
        )

        val candidates = mutableListOf<Box>()

        for (i in 0 until anchorCount) {
            val confidence = output[4][i]
            val cx = output[0][i]
            val cy = output[1][i]
            val width = output[2][i]
            val height = output[3][i]

            if (
                !confidence.isFinite() ||
                confidence < CONFIDENCE_THRESHOLD ||
                !cx.isFinite() ||
                !cy.isFinite() ||
                !width.isFinite() ||
                !height.isFinite() ||
                width <= 0f ||
                height <= 0f ||
                cx < 0f ||
                cy < 0f ||
                cx >= INPUT_SIZE ||
                cy >= INPUT_SIZE
            ) {
                continue
            }

            candidates.add(
                Box(
                    cx = cx,
                    cy = cy,
                    w = width,
                    h = height,
                    confidence = confidence
                )
            )
        }

        if (candidates.isEmpty()) {
            Log.d(TAG, "No valid detections found")
            return null
        }

        val topPredictions = candidates
            .sortedByDescending { it.confidence }
            .take(10)

        topPredictions.forEachIndexed { index, box ->
            Log.d(
                TAG,
                "Prediction[$index]: confidence=${box.confidence}, " +
                        "cx=${box.cx}, cy=${box.cy}, " +
                        "w=${box.w}, h=${box.h}"
            )
        }

        // Remove overlapping duplicate predictions.
        val sorted = candidates.sortedByDescending {
            it.confidence
        }

        val filtered = mutableListOf<Box>()

        for (candidate in sorted) {
            val duplicate = filtered.any { kept ->
                val intersectionW = max(
                    0f,
                    min(candidate.right, kept.right) -
                            max(candidate.left, kept.left)
                )

                val intersectionH = max(
                    0f,
                    min(candidate.bottom, kept.bottom) -
                            max(candidate.top, kept.top)
                )

                val intersection =
                    intersectionW * intersectionH

                val union =
                    candidate.w * candidate.h +
                            kept.w * kept.h -
                            intersection

                union > 0f &&
                        intersection / union > 0.40f
            }

            if (!duplicate) {
                filtered.add(candidate)
            }
        }

        // Group detections that are approximately on the same line.
        val lines = mutableListOf<MutableList<Box>>()

        for (box in filtered.sortedBy { it.cy }) {
            val matchingLine = lines.firstOrNull { line ->
                val averageY =
                    line.map { it.cy }.average().toFloat()

                val averageHeight =
                    line.map { it.h }.average().toFloat()

                abs(box.cy - averageY) <=
                        max(box.h, averageHeight) * 1.2f
            }

            if (matchingLine != null) {
                matchingLine.add(box)
            } else {
                lines.add(mutableListOf(box))
            }
        }

        if (lines.isEmpty()) return null

        // Prefer the line with the widest horizontal coverage.
        val selectedLine = lines.maxByOrNull { line ->
            val lineLeft = line.minOf { it.left }
            val lineRight = line.maxOf { it.right }
            val span = lineRight - lineLeft

            span * (1f + 0.15f * (line.size - 1))
        } ?: return null

        val modelLeft = selectedLine.minOf { it.left }
        val modelTop = selectedLine.minOf { it.top }
        val modelRight = selectedLine.maxOf { it.right }
        val modelBottom = selectedLine.maxOf { it.bottom }

        val lineWidth = modelRight - modelLeft
        val lineHeight = modelBottom - modelTop

        val padX = max(8f, lineWidth * 0.08f)
        val padY = max(10f, lineHeight * 0.35f)

        val left = (
                (modelLeft - padX - letterbox.left) /
                        letterbox.scale
                ).toInt().coerceIn(0, originalWidth)

        val top = (
                (modelTop - padY - letterbox.top) /
                        letterbox.scale
                ).toInt().coerceIn(0, originalHeight)

        val right = (
                (modelRight + padX - letterbox.left) /
                        letterbox.scale
                ).toInt().coerceIn(0, originalWidth)

        val bottom = (
                (modelBottom + padY - letterbox.top) /
                        letterbox.scale
                ).toInt().coerceIn(0, originalHeight)

        if (right <= left || bottom <= top) {
            Log.d(TAG, "Merged region has invalid dimensions")
            return null
        }

        val confidence =
            selectedLine.maxOf { it.confidence }

        Log.d(
            TAG,
            "Candidates=${candidates.size}, " +
                    "after NMS=${filtered.size}, " +
                    "lineBoxes=${selectedLine.size}"
        )

        Log.d(
            TAG,
            "Merged crop=${right - left}x${bottom - top}, " +
                    "confidence=$confidence, " +
                    "bounds=[$left,$top,$right,$bottom]"
        )

        return DateRegion(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            confidence = confidence
        )
    }

    @Synchronized
    fun close() {
        try {
            session?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing expiry detector", e)
        } finally {
            session = null
            inputName = null
        }
    }
}