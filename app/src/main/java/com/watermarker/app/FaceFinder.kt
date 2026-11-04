package com.watermarker.app

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** تشخیص چهره با ML Kit (مدل داخل خود اپ است و آفلاین کار می‌کند). فقط روی ترد پس‌زمینه صدا بزن. */
object FaceFinder {

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )
    }

    /** مستطیل چهره‌ها در مختصات خود [img]. اگر چیزی پیدا نشود یا خطا بدهد، لیست خالی. */
    fun detect(img: Bitmap): List<RectF> {
        return try {
            val scale = min(1f, 960f / max(img.width, img.height))
            val small = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    img,
                    (img.width * scale).roundToInt().coerceAtLeast(1),
                    (img.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            } else img
            val faces = Tasks.await(detector.process(InputImage.fromBitmap(small, 0)))
            if (small !== img) small.recycle()
            faces.map {
                val b = it.boundingBox
                RectF(b.left / scale, b.top / scale, b.right / scale, b.bottom / scale)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
