package com.watermarker.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class MarkMode { LOGO, TEXT, BOTH }

/** ترتیب نه جای ثابت: ردیف‌به‌ردیف از بالا-چپ تا پایین-راست */
enum class Placement {
    AUTO,
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MIDDLE_LEFT, CENTER, MIDDLE_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
}

data class WatermarkSettings(
    val mode: MarkMode = MarkMode.BOTH,
    val text: String = "© My Brand",
    /** عرض واترمارک به‌صورت درصدی از عرض عکس */
    val sizePercent: Float = 22f,
    val opacity: Float = 0.7f,
    val placement: Placement = Placement.AUTO,
    /** تکرار مورب روی کل عکس */
    val tiled: Boolean = false,
    /** در حالت خودکار، از روی چهره‌ها دوری کن */
    val avoidFaces: Boolean = true,
)

object WatermarkEngine {

    const val MAX_SIDE = 4096

    // ---------------------------------------------------------------- decode

    /** بلندترین ضلع عکس اصلی بدون دیکد کامل؛ اگر خوانده نشد، صفر */
    fun sourceLongestSide(context: Context, uri: Uri): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (e: Exception) {
            return 0
        }
        return max(bounds.outWidth, bounds.outHeight).coerceAtLeast(0)
    }

    fun decode(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        if (!m.isIdentity) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }

        val longest = max(bmp.width, bmp.height)
        if (longest > maxSide) {
            val k = maxSide.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * k).roundToInt().coerceAtLeast(1),
                (bmp.height * k).roundToInt().coerceAtLeast(1),
                true
            )
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }
        return bmp
    }

    // ----------------------------------------------------------------- apply

    /**
     * واترمارک را روی یک کپی از [base] می‌اندازد و کپی را برمی‌گرداند.
     * [faces] اگر داده شود (مثلاً کش پیش‌نمایش) دوباره تشخیص چهره انجام نمی‌شود.
     */
    fun apply(
        base: Bitmap,
        logo: Bitmap?,
        s: WatermarkSettings,
        faces: List<RectF>? = null,
    ): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val useLogo = logo != null && s.mode != MarkMode.TEXT
        val useText = s.text.isNotBlank() && s.mode != MarkMode.LOGO
        if (!useLogo && !useText) return out

        val markW = out.width * s.sizePercent / 100f
        val layout = MarkLayout.build(
            markW = markW,
            maxH = out.height * 0.4f,
            logo = if (useLogo) logo else null,
            text = if (useText) s.text.trim() else null,
        )

        // تحلیل باید قبل از کشیدن واترمارک انجام شود
        val analysis = Analysis(out)
        val canvas = Canvas(out)

        if (s.tiled) {
            val mean = analysis.stats(0f, 0f, out.width.toFloat(), out.height.toFloat()).mean
            drawTiled(canvas, out.width, out.height, layout, s.opacity, mean > 150f)
        } else {
            val margin = min(out.width, out.height) * 0.03f
            val (x, y) = if (s.placement == Placement.AUTO) {
                val f = faces ?: if (s.avoidFaces) FaceFinder.detect(out) else emptyList()
                autoSpot(out, analysis, layout.width, layout.height, margin, f)
            } else {
                manualSpot(s.placement, out, layout.width, layout.height, margin)
            }
            val mean = analysis.stats(x, y, layout.width, layout.height).mean
            layout.draw(canvas, x, y, s.opacity, darkMark = mean > 150f)
        }
        layout.release()
        return out
    }

    // --------------------------------------------------------------- analysis

    private class RegionStats(val mean: Float, val std: Float, val grad: Float)

    /** نسخهٔ کوچک‌شدهٔ عکس برای سنجش روشنایی و شلوغی هر ناحیه */
    private class Analysis(img: Bitmap) {
        val scale = min(1f, 256f / max(img.width, img.height))
        val sw = max(2, (img.width * scale).roundToInt())
        val sh = max(2, (img.height * scale).roundToInt())
        val luma: FloatArray
        val grad: FloatArray

        init {
            val small = Bitmap.createScaledBitmap(img, sw, sh, true)
            val px = IntArray(sw * sh)
            small.getPixels(px, 0, sw, 0, 0, sw, sh)
            if (small !== img) small.recycle()

            luma = FloatArray(sw * sh) {
                val c = px[it]
                0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
            }
            grad = FloatArray(sw * sh)
            for (y in 1 until sh - 1) {
                for (x in 1 until sw - 1) {
                    val i = y * sw + x
                    val gx = luma[i + 1] - luma[i - 1]
                    val gy = luma[i + sw] - luma[i - sw]
                    grad[i] = abs(gx) + abs(gy)
                }
            }
        }

        /** آمار ناحیه‌ای که در مختصات عکس اصلی داده شده */
        fun stats(x: Float, y: Float, w: Float, h: Float): RegionStats {
            val rx0 = (x * scale).toInt().coerceIn(0, sw - 1)
            val ry0 = (y * scale).toInt().coerceIn(0, sh - 1)
            val rx1 = ceil((x + w) * scale).toInt().coerceIn(rx0 + 1, sw)
            val ry1 = ceil((y + h) * scale).toInt().coerceIn(ry0 + 1, sh)
            var sum = 0f
            var sumSq = 0f
            var gsum = 0f
            var n = 0
            for (yy in ry0 until ry1) {
                for (xx in rx0 until rx1) {
                    val i = yy * sw + xx
                    val l = luma[i]
                    sum += l
                    sumSq += l * l
                    gsum += grad[i]
                    n++
                }
            }
            val mean = sum / n
            val std = sqrt(max(0f, sumSq / n - mean * mean))
            return RegionStats(mean, std, gsum / n)
        }
    }

    // --------------------------------------------------------------- placement

    private fun gridCoords(img: Bitmap, w: Float, h: Float, margin: Float): Pair<List<Float>, List<Float>> {
        val xs = listOf(margin, (img.width - w) / 2f, img.width - w - margin).map { it.coerceAtLeast(0f) }
        val ys = listOf(margin, (img.height - h) / 2f, img.height - h - margin).map { it.coerceAtLeast(0f) }
        return xs to ys
    }

    private fun manualSpot(p: Placement, img: Bitmap, w: Float, h: Float, margin: Float): Pair<Float, Float> {
        val (xs, ys) = gridCoords(img, w, h, margin)
        val idx = p.ordinal - 1 // AUTO = 0 را نداریم
        return xs[idx % 3] to ys[idx / 3]
    }

    /**
     * هشت جای ممکن (چهار گوشه و وسط چهار لبه) را می‌سنجد.
     * امتیاز = شدت لبه‌ها + نصف انحراف معیار روشنایی + سوگیری کوچک به نفع گوشه‌ها
     * + جریمهٔ سنگین برای هم‌پوشانی با چهره‌ها. امتیاز کمتر یعنی جای بهتر.
     */
    private fun autoSpot(
        img: Bitmap,
        analysis: Analysis,
        w: Float,
        h: Float,
        margin: Float,
        faces: List<RectF>,
    ): Pair<Float, Float> {
        val (xs, ys) = gridCoords(img, w, h, margin)
        val bias = arrayOf(
            floatArrayOf(2f, 4f, 2f),
            floatArrayOf(4f, 0f, 4f),
            floatArrayOf(1f, 4f, 0f),
        )
        var bestX = xs[2]
        var bestY = ys[2]
        var bestScore = Float.MAX_VALUE
        for (iy in 0..2) {
            for (ix in 0..2) {
                if (ix == 1 && iy == 1) continue
                val x = xs[ix]
                val y = ys[iy]
                val st = analysis.stats(x, y, w, h)
                var score = st.grad + 0.5f * st.std + bias[iy][ix]
                score += 400f * faceOverlap(x, y, w, h, faces)
                if (score < bestScore) {
                    bestScore = score
                    bestX = x
                    bestY = y
                }
            }
        }
        return bestX to bestY
    }

    /** نسبت مساحتی از واترمارک که روی چهره‌ها (با کمی حاشیه) می‌افتد، بین ۰ و ۱ */
    private fun faceOverlap(x: Float, y: Float, w: Float, h: Float, faces: List<RectF>): Float {
        if (faces.isEmpty() || w <= 0f || h <= 0f) return 0f
        var total = 0f
        for (f in faces) {
            val padX = f.width() * 0.15f
            val padY = f.height() * 0.15f
            val ix = min(x + w, f.right + padX) - max(x, f.left - padX)
            val iy = min(y + h, f.bottom + padY) - max(y, f.top - padY)
            if (ix > 0f && iy > 0f) total += ix * iy
        }
        return (total / (w * h)).coerceAtMost(1f)
    }

    private fun drawTiled(c: Canvas, w: Int, h: Int, layout: MarkLayout, opacity: Float, dark: Boolean) {
        val diag = hypot(w.toFloat(), h.toFloat())
        val stepX = max(layout.width * 1.8f, 1f)
        val stepY = max(layout.height * 2.6f, 1f)
        c.save()
        c.translate(w / 2f, h / 2f)
        c.rotate(-30f)
        var row = 0
        var y = -diag / 2f
        while (y < diag / 2f) {
            var x = -diag / 2f + if (row % 2 == 1) stepX / 2f else 0f
            while (x < diag / 2f) {
                layout.draw(c, x, y, opacity, dark)
                x += stepX
            }
            y += stepY
            row++
        }
        c.restore()
    }

    // ------------------------------------------------------------ mark layout

    private class MarkLayout(
        private val logo: Bitmap?,
        private val ownsLogo: Boolean,
        private val logoW: Float,
        private val logoH: Float,
        private val text: String?,
        private val textPaint: Paint,
        textW: Float,
        textH: Float,
        private val gap: Float,
    ) {
        val width: Float = max(logoW, textW)
        val height: Float =
            logoH + (if (logo != null && text != null) gap else 0f) + textH

        fun draw(c: Canvas, x: Float, y: Float, opacity: Float, darkMark: Boolean) {
            val alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
            var cy = y
            if (logo != null) {
                val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                    this.alpha = alpha
                }
                val left = x + (width - logoW) / 2f
                c.drawBitmap(logo, null, RectF(left, cy, left + logoW, cy + logoH), p)
                cy += logoH + if (text != null) gap else 0f
            }
            if (text != null) {
                val fg = if (darkMark) Color.BLACK else Color.WHITE
                val shadow = if (darkMark) Color.WHITE else Color.BLACK
                textPaint.color = fg
                textPaint.alpha = alpha
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.setShadowLayer(
                    textPaint.textSize * 0.06f,
                    0f,
                    textPaint.textSize * 0.03f,
                    Color.argb((alpha * 0.5f).roundToInt(), Color.red(shadow), Color.green(shadow), Color.blue(shadow))
                )
                c.drawText(text, x + width / 2f, cy - textPaint.fontMetrics.ascent, textPaint)
            }
        }

        fun release() {
            if (ownsLogo) logo?.recycle()
        }

        companion object {
            fun build(markW: Float, maxH: Float, logo: Bitmap?, text: String?): MarkLayout {
                var lw = 0f
                var lh = 0f
                var scaledLogo: Bitmap? = null
                if (logo != null) {
                    lw = markW
                    lh = markW * logo.height / logo.width
                    if (lh > maxH * 0.7f) {
                        lh = maxH * 0.7f
                        lw = lh * logo.width / logo.height
                    }
                    scaledLogo = downscale(logo, lw.roundToInt().coerceAtLeast(1), lh.roundToInt().coerceAtLeast(1))
                }

                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = min(markW * 0.2f, maxH * 0.3f)
                }
                var tw = 0f
                var th = 0f
                if (text != null) {
                    val measured = paint.measureText(text)
                    if (measured > markW && measured > 0f) paint.textSize *= markW / measured
                    tw = paint.measureText(text)
                    th = paint.fontMetrics.descent - paint.fontMetrics.ascent
                }
                val gap = paint.textSize * 0.25f
                return MarkLayout(
                    logo = scaledLogo,
                    ownsLogo = scaledLogo != null && scaledLogo !== logo,
                    logoW = lw,
                    logoH = lh,
                    text = text,
                    textPaint = paint,
                    textW = tw,
                    textH = th,
                    gap = gap,
                )
            }

            /** کوچک‌کردن مرحله‌ای (نصف‌کردن) تا لبه‌ها دندانه‌دار نشوند. */
            private fun downscale(src: Bitmap, w: Int, h: Int): Bitmap {
                var cur = src
                while (cur.width / 2 >= w && cur.height / 2 >= h) {
                    val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
                    if (cur !== src) cur.recycle()
                    cur = next
                }
                if (cur.width != w || cur.height != h) {
                    val next = Bitmap.createScaledBitmap(cur, w, h, true)
                    if (cur !== src) cur.recycle()
                    cur = next
                }
                return cur
            }
        }
    }
}
