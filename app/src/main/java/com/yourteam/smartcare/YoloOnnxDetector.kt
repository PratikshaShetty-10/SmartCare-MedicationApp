
package com.yourteam.smartcare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import android.graphics.Paint
import android.graphics.RectF

/**
 * Runs the SmartCare YOLO model locally using ONNX Runtime.
 *
 * Model classes:
 * 0 = broken_tablet
 * 1 = empty_pocket
 * 2 = filled_tablet
 *
 * Input: RGB float tensor [1, 3, 640, 640], normalized to [0, 1].
 * Output: [1, 7, 8400] = box coordinates + 3 class scores.
 */
object YoloOnnxDetector : Closeable {

    private const val TAG = "YoloOnnxDetector"
    private const val MODEL_FILE = "best.onnx"

    private const val INPUT_SIZE = 640
    private const val CONFIDENCE_THRESHOLD = 0.25f
    private const val NMS_THRESHOLD = 0.45f
    private const val CLASS_COUNT = 3

    private val classNames = listOf(
        "broken_tablet",
        "empty_pocket",
        "filled_tablet"
    )

    private val environment: OrtEnvironment by lazy {
        OrtEnvironment.getEnvironment()
    }

    @Volatile
    private var session: OrtSession? = null

    @Volatile
    private var inputName: String? = null

    data class Detection(
        val classId: Int,
        val label: String,
        val confidence: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    data class DetectionCounts(
        val brokenTablets: Int,
        val emptyPockets: Int,
        val filledTablets: Int,
        val detections: List<Detection>
    )

    private data class LetterboxInfo(
        val scale: Float,
        val left: Int,
        val top: Int,
        val resizedWidth: Int,
        val resizedHeight: Int
    )

    /**
     * Loads the model once. The original root-level model is not modified.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (session != null) return

        val modelBytes = context.applicationContext.assets
            .open(MODEL_FILE)
            .use { it.readBytes() }

        val options = OrtSession.SessionOptions()

        try {
            options.setIntraOpNumThreads(2)

            val newSession = environment.createSession(modelBytes, options)

            val name = newSession.inputNames.firstOrNull()
                ?: throw IllegalStateException("The ONNX model has no input")

            session = newSession
            inputName = name

            Log.i(
                TAG,
                "Model loaded. Input=$name, outputs=${newSession.outputNames}"
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Could not initialize $MODEL_FILE: ${e.message}",
                e
            )
        } finally {
            options.close()
        }
    }

    /**
     * Runs inference on a captured camera bitmap.
     * Call from Dispatchers.IO, not the main thread.
     */
    @Synchronized
    fun detect(context: Context, bitmap: Bitmap): DetectionCounts {
        initialize(context)

        val currentSession = session
            ?: throw IllegalStateException("Model session is unavailable")

        val currentInputName = inputName
            ?: throw IllegalStateException("Model input name is unavailable")

        val (inputData, letterbox) = preprocess(bitmap)

        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(inputData),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        ).use { inputTensor ->

            currentSession.run(
                mapOf(currentInputName to inputTensor)
            ).use { result ->

                val output = result[0].value as? Array<Array<FloatArray>>
                    ?: throw IllegalStateException(
                        "Unexpected YOLO output type: ${result[0].value.javaClass.name}"
                    )

                if (output.isEmpty() ||
                    output[0].size != 4 + CLASS_COUNT
                ) {
                    throw IllegalStateException(
                        "Unexpected YOLO output shape. Expected [1, 7, anchors]"
                    )
                }

                val detections = decodeOutput(
                    output = output[0],
                    originalWidth = bitmap.width,
                    originalHeight = bitmap.height,
                    letterbox = letterbox
                )

                val finalDetections = applyClassWiseNms(detections)

                val counts = DetectionCounts(
                    brokenTablets = finalDetections.count {
                        it.classId == 0
                    },
                    emptyPockets = finalDetections.count {
                        it.classId == 1
                    },
                    filledTablets = finalDetections.count {
                        it.classId == 2
                    },
                    detections = finalDetections
                )

                Log.i(
                    TAG,
                    "Detections: broken=${counts.brokenTablets}, " +
                            "empty=${counts.emptyPockets}, " +
                            "filled=${counts.filledTablets}"
                )

                return counts
            }
        }
    }


    /**
     * Creates a copy of the captured image with detection boxes,
     * class labels, and confidence scores drawn over it.
     *
     * The original bitmap is not modified.
     */
    fun drawDetections(
        bitmap: Bitmap,
        detections: List<Detection>
    ): Bitmap {
        val outputBitmap = bitmap.copy(
            Bitmap.Config.ARGB_8888,
            true
        ) ?: throw IllegalStateException(
            "Could not create annotated image"
        )

        val canvas = Canvas(outputBitmap)

        val scale = min(
            bitmap.width.toFloat(),
            bitmap.height.toFloat()
        )

        val strokeWidth = max(3f, scale * 0.006f)
        val textSize = max(22f, scale * 0.035f)
        val padding = textSize * 0.25f

        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setStyle(Paint.Style.STROKE)
            setStrokeWidth(strokeWidth)
        }

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setStyle(Paint.Style.FILL)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setStyle(Paint.Style.FILL)
            setColor(Color.WHITE)
            setTextSize(textSize)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }

        for (detection in detections) {
            val color = when (detection.classId) {
                0 -> Color.RED
                1 -> Color.YELLOW
                2 -> Color.GREEN
                else -> Color.CYAN
            }

            val left = detection.left.coerceIn(
                0f, bitmap.width.toFloat()
            )
            val top = detection.top.coerceIn(
                0f, bitmap.height.toFloat()
            )
            val right = detection.right.coerceIn(
                0f, bitmap.width.toFloat()
            )
            val bottom = detection.bottom.coerceIn(
                0f, bitmap.height.toFloat()
            )

            if (right <= left || bottom <= top) continue

            boxPaint.color = color
            canvas.drawRect(
                left, top, right, bottom,
                boxPaint
            )

            val label = "${detection.label.replace('_', ' ')} " +
                    "${(detection.confidence * 100).toInt()}%"

            val textWidth = textPaint.measureText(label)
            val labelHeight = textSize + padding * 2

            val labelLeft = left.coerceAtMost(
                (bitmap.width - textWidth - padding * 2)
                    .coerceAtLeast(0f)
            )

            val labelTop = if (top >= labelHeight) {
                top - labelHeight
            } else {
                top.coerceAtMost(
                    (bitmap.height - labelHeight).coerceAtLeast(0f)
                )
            }

            backgroundPaint.color = color

            canvas.drawRoundRect(
                RectF(
                    labelLeft,
                    labelTop,
                    (labelLeft + textWidth + padding * 2)
                        .coerceAtMost(bitmap.width.toFloat()),
                    (labelTop + labelHeight)
                        .coerceAtMost(bitmap.height.toFloat())
                ),
                6f,
                6f,
                backgroundPaint
            )

            canvas.drawText(
                label,
                labelLeft + padding,
                labelTop + padding + textSize,
                textPaint
            )
        }

        return outputBitmap
    }

    /**
     * Letterbox to 640 x 640 using RGB channels and ImageNet-independent
     * YOLO normalization: pixel / 255.0.
     */
    private fun preprocess(bitmap: Bitmap): Pair<FloatArray, LetterboxInfo> {
        require(bitmap.width > 0 && bitmap.height > 0) {
            "The captured image is empty"
        }

        val scale = min(
            INPUT_SIZE.toFloat() / bitmap.width,
            INPUT_SIZE.toFloat() / bitmap.height
        )

        val resizedWidth = (bitmap.width * scale).toInt()
            .coerceIn(1, INPUT_SIZE)

        val resizedHeight = (bitmap.height * scale).toInt()
            .coerceIn(1, INPUT_SIZE)

        val left = (INPUT_SIZE - resizedWidth) / 2
        val top = (INPUT_SIZE - resizedHeight) / 2

        val resized = Bitmap.createScaledBitmap(
            bitmap,
            resizedWidth,
            resizedHeight,
            true
        )

        val letterboxed = Bitmap.createBitmap(
            INPUT_SIZE,
            INPUT_SIZE,
            Bitmap.Config.ARGB_8888
        )

        try {
            Canvas(letterboxed).apply {
                drawColor(Color.rgb(114, 114, 114))
                drawBitmap(resized, left.toFloat(), top.toFloat(), null)
            }

            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            letterboxed.getPixels(
                pixels,
                0,
                INPUT_SIZE,
                0,
                0,
                INPUT_SIZE,
                INPUT_SIZE
            )

            val planeSize = INPUT_SIZE * INPUT_SIZE
            val tensor = FloatArray(3 * planeSize)

            for (i in pixels.indices) {
                val pixel = pixels[i]

                tensor[i] =
                    ((pixel shr 16) and 0xFF) / 255.0f

                tensor[planeSize + i] =
                    ((pixel shr 8) and 0xFF) / 255.0f

                tensor[2 * planeSize + i] =
                    (pixel and 0xFF) / 255.0f
            }

            return tensor to LetterboxInfo(
                scale = scale,
                left = left,
                top = top,
                resizedWidth = resizedWidth,
                resizedHeight = resizedHeight
            )
        } finally {
            if (resized !== bitmap && resized !== letterboxed) {
                resized.recycle()
            }
            letterboxed.recycle()
        }
    }

    /**
     * Decodes output layout [7, anchors].
     * Each anchor contains cx, cy, width, height, and 3 class scores.
     */
    private fun decodeOutput(
        output: Array<FloatArray>,
        originalWidth: Int,
        originalHeight: Int,
        letterbox: LetterboxInfo
    ): List<Detection> {
        val anchorCount = output[0].size
        val candidates = ArrayList<Detection>()

        for (anchor in 0 until anchorCount) {
            var bestClass = -1
            var bestScore = 0f

            for (classId in 0 until CLASS_COUNT) {
                val score = output[4 + classId][anchor]

                if (score > bestScore) {
                    bestScore = score
                    bestClass = classId
                }
            }

            if (bestClass < 0 ||
                bestScore < CONFIDENCE_THRESHOLD
            ) {
                continue
            }

            val cx = output[0][anchor]
            val cy = output[1][anchor]
            val width = output[2][anchor]
            val height = output[3][anchor]

            if (!cx.isFinite() || !cy.isFinite() ||
                !width.isFinite() || !height.isFinite() ||
                width <= 0f || height <= 0f
            ) {
                continue
            }

            val scale = letterbox.scale

            val left = ((cx - width / 2f - letterbox.left) / scale)
                .coerceIn(0f, originalWidth.toFloat())

            val top = ((cy - height / 2f - letterbox.top) / scale)
                .coerceIn(0f, originalHeight.toFloat())

            val right = ((cx + width / 2f - letterbox.left) / scale)
                .coerceIn(0f, originalWidth.toFloat())

            val bottom = ((cy + height / 2f - letterbox.top) / scale)
                .coerceIn(0f, originalHeight.toFloat())

            if (right <= left || bottom <= top) continue

            candidates.add(
                Detection(
                    classId = bestClass,
                    label = classNames[bestClass],
                    confidence = bestScore,
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom
                )
            )
        }

        return candidates
    }

    /**
     * Greedy NMS independently per class, matching the Python test approach.
     */
    private fun applyClassWiseNms(
        detections: List<Detection>
    ): List<Detection> {
        val kept = ArrayList<Detection>()

        for (classId in 0 until CLASS_COUNT) {
            val remaining = detections
                .filter { it.classId == classId }
                .sortedByDescending { it.confidence }
                .toMutableList()

            while (remaining.isNotEmpty()) {
                val best = remaining.removeAt(0)
                kept.add(best)

                val iterator = remaining.iterator()

                while (iterator.hasNext()) {
                    val candidate = iterator.next()

                    if (intersectionOverUnion(best, candidate) > NMS_THRESHOLD) {
                        iterator.remove()
                    }
                }
            }
        }

        return kept.sortedByDescending { it.confidence }
    }

    private fun intersectionOverUnion(
        first: Detection,
        second: Detection
    ): Float {
        val intersectionLeft = max(first.left, second.left)
        val intersectionTop = max(first.top, second.top)
        val intersectionRight = min(first.right, second.right)
        val intersectionBottom = min(first.bottom, second.bottom)

        val intersectionWidth = max(
            0f,
            intersectionRight - intersectionLeft
        )

        val intersectionHeight = max(
            0f,
            intersectionBottom - intersectionTop
        )

        val intersection = intersectionWidth * intersectionHeight

        val firstArea =
            (first.right - first.left) * (first.bottom - first.top)

        val secondArea =
            (second.right - second.left) * (second.bottom - second.top)

        val union = firstArea + secondArea - intersection

        return if (union <= 0f) 0f else intersection / union
    }

    @Synchronized
    override fun close() {
        session?.close()
        session = null
        inputName = null
    }
}