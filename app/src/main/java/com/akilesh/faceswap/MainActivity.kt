package com.akilesh.faceswap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private var sourceBitmap: Bitmap? = null
    private var targetBitmap: Bitmap? = null
    private lateinit var sourcePreview: ImageView
    private lateinit var targetPreview: ImageView
    private lateinit var resultPreview: ImageView
    private lateinit var status: TextView

    private val pickSource = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(uri, true)
    }
    private val pickTarget = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(uri, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)

        content.addView(TextView(this).apply {
            text = "Face Swap Studio"
            textSize = 26f
            setTextColor(0xFF201A2C.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        })
        content.addView(TextView(this).apply {
            text = "Choose a face photo and a destination photo. Processing runs on this device."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })

        sourcePreview = imagePreview()
        targetPreview = imagePreview()
        resultPreview = imagePreview().apply { visibility = View.GONE }

        content.addView(sectionLabel("1. Face to use"))
        content.addView(sourcePreview)
        content.addView(actionButton("Choose face photo") { pickSource.launch("image/*") })
        content.addView(sectionLabel("2. Destination photo"))
        content.addView(targetPreview)
        content.addView(actionButton("Choose destination photo") { pickTarget.launch("image/*") })
        content.addView(actionButton("Create face swap") { createSwap() })
        content.addView(sectionLabel("Result"))
        content.addView(resultPreview)
        content.addView(actionButton("Save result to Photos") { saveResult() })
        status = TextView(this).apply {
            text = "Ready. Use photos you own or have permission to edit."
            textSize = 13f
            setPadding(4, 16, 4, 20)
        }
        content.addView(status)
    }

    private fun sectionLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 18f
        setTextColor(0xFF352B45.toInt())
        setPadding(0, 18, 0, 8)
    }

    private fun imagePreview() = ImageView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(220)
        ).apply { bottomMargin = dp(8) }
        scaleType = ImageView.ScaleType.FIT_CENTER
        setBackgroundColor(0xFFF1EFF5.toInt())
        contentDescription = "Selected photo preview"
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun loadImage(uri: Uri, isSource: Boolean) {
        try {
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: throw IllegalArgumentException("Could not read image")
            val scaled = downscale(bitmap, 1800)
            if (isSource) {
                sourceBitmap = scaled
                sourcePreview.setImageBitmap(scaled)
            } else {
                targetBitmap = scaled
                targetPreview.setImageBitmap(scaled)
            }
            status.text = "Photo loaded. Select both photos, then create the swap."
        } catch (e: Exception) {
            status.text = "Could not load that image: ${e.message}"
        }
    }

    private fun downscale(bitmap: Bitmap, maxSide: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }

    private fun createSwap() {
        val source = sourceBitmap
        val target = targetBitmap
        if (source == null || target == null) {
            status.text = "Choose both photos first."
            return
        }
        status.text = "Detecting faces on device…"
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        val detector = FaceDetection.getClient(options)
        val sourceImage = InputImage.fromBitmap(source, 0)
        val targetImage = InputImage.fromBitmap(target, 0)

        detector.process(sourceImage)
            .addOnSuccessListener { sourceFaces ->
                if (sourceFaces.isEmpty()) {
                    status.text = "No face found in the face photo."
                    detector.close()
                    return@addOnSuccessListener
                }
                detector.process(targetImage)
                    .addOnSuccessListener { targetFaces ->
                        if (targetFaces.isEmpty()) {
                            status.text = "No face found in the destination photo."
                        } else {
                            val output = compositeFace(source, target, sourceFaces.maxBy { it.boundingBox.width() * it.boundingBox.height() },
                                targetFaces.maxBy { it.boundingBox.width() * it.boundingBox.height() })
                            resultPreview.setImageBitmap(output)
                            resultPreview.visibility = View.VISIBLE
                            status.text = "Preview created. This is a basic face-region composite, not a photorealistic AI identity swap."
                        }
                        detector.close()
                    }
                    .addOnFailureListener { status.text = "Destination face detection failed: ${it.localizedMessage}"; detector.close() }
            }
            .addOnFailureListener { status.text = "Face detection failed: ${it.localizedMessage}"; detector.close() }
    }

    private fun compositeFace(source: Bitmap, target: Bitmap, sourceFace: Face, targetFace: Face): Bitmap {
        val sb = sourceFace.boundingBox
        val tb = targetFace.boundingBox
        val sx = (sb.left - sb.width() * 0.18f).toInt().coerceIn(0, source.width - 1)
        val sy = (sb.top - sb.height() * 0.22f).toInt().coerceIn(0, source.height - 1)
        val sw = (sb.width() * 1.36f).toInt().coerceAtLeast(1).coerceAtMost(source.width - sx)
        val sh = (sb.height() * 1.44f).toInt().coerceAtLeast(1).coerceAtMost(source.height - sy)
        val crop = Bitmap.createBitmap(source, sx, sy, sw, sh)
        val out = target.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val rect = RectF(
            tb.left - tb.width() * 0.18f,
            tb.top - tb.height() * 0.22f,
            tb.right + tb.width() * 0.18f,
            tb.bottom + tb.height() * 0.22f
        )
        val scaled = Bitmap.createScaledBitmap(crop, rect.width().toInt().coerceAtLeast(1), rect.height().toInt().coerceAtLeast(1), true)
        val layer = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val layerCanvas = Canvas(layer)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        layerCanvas.drawOval(RectF(0f, 0f, scaled.width.toFloat(), scaled.height.toFloat()), maskPaint)
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        layerCanvas.drawBitmap(scaled, 0f, 0f, imagePaint)
        imagePaint.xfermode = null
        val featherPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 220 }
        canvas.drawBitmap(layer, null, rect, featherPaint)
        return out
    }

    private fun saveResult() {
        val image = (resultPreview.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        if (image == null) {
            status.text = "Create a result first."
            return
        }
        val name = "FaceSwap_${System.currentTimeMillis()}.jpg"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FaceSwapStudio")
        }
        val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            status.text = "Could not create image file."
            return
        }
        val success = contentResolver.openOutputStream(uri)?.use { image.compress(Bitmap.CompressFormat.JPEG, 95, it) } ?: false
        status.text = if (success) "Saved to Pictures/FaceSwapStudio." else "Could not save image."
        Toast.makeText(this, status.text, Toast.LENGTH_SHORT).show()
    }
}
