package com.watermarker.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.roundToInt

/** [where] جایی است که عکس‌ها ذخیره شدند، برای پیام نهایی */
data class ExportResult(val done: Int, val failed: Int, val where: String = "")

object Exporter {

    const val GALLERY_DIR = "Watermarker"

    // ------------------------------------------------------------------ export

    suspend fun export(
        context: Context,
        photos: List<Uri>,
        logo: Bitmap?,
        settings: WatermarkSettings,
        out: OutputSettings,
        onProgress: (Float) -> Unit,
    ): ExportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val folder = if (out.destination == Destination.FOLDER) out.folderUri?.let(Uri::parse) else null
        val sink: Sink = when {
            folder != null -> FolderSink(context, folder)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> MediaStoreSink(context)
            else -> LegacySink(context)
        }
        val where = if (folder != null) {
            folderLabel(context, folder) ?: "پوشهٔ انتخاب‌شده"
        } else {
            "گالری (Pictures/$GALLERY_DIR)"
        }

        var done = 0
        var failed = 0
        val usedNames = HashSet<String>()

        photos.forEachIndexed { index, uri ->
            try {
                val longest = WatermarkEngine.sourceLongestSide(context, uri)
                val base = if (longest <= 0) null else {
                    WatermarkEngine.decode(context, uri, targetSide(out, longest))
                }
                if (base == null) {
                    failed++
                } else {
                    val result = WatermarkEngine.apply(base, logo, settings)
                    base.recycle()

                    val format = resolveFormat(out.format, resolver.getType(uri))
                    val name = uniqueName(
                        usedNames, displayName(context, uri, index), out.suffix, format.ext
                    )
                    val ok = try {
                        write(sink, result, name, format, out.quality)
                    } finally {
                        result.recycle()
                    }
                    if (ok) done++ else failed++
                }
            } catch (e: Exception) {
                failed++
            } catch (e: OutOfMemoryError) {
                failed++
            }
            onProgress((index + 1).toFloat() / photos.size)
        }
        ExportResult(done, failed, where)
    }

    /** بلندترین ضلع خروجی برای عکسی که بلندترین ضلعش [longest] است */
    fun targetSide(out: OutputSettings, longest: Int): Int = when (out.sizeMode) {
        SizeMode.MAX_SIDE -> out.maxSide
        SizeMode.PERCENT -> (longest * out.percent / 100.0).roundToInt()
    }.coerceAtLeast(1)

    // ------------------------------------------------------------------ format

    private enum class Fmt(val ext: String, val mime: String) {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp"),
    }

    private fun resolveFormat(f: OutFormat, sourceMime: String?): Fmt = when (f) {
        OutFormat.JPEG -> Fmt.JPEG
        OutFormat.PNG -> Fmt.PNG
        OutFormat.WEBP -> Fmt.WEBP
        OutFormat.ORIGINAL -> when (sourceMime) {
            "image/png" -> Fmt.PNG
            "image/webp" -> Fmt.WEBP
            else -> Fmt.JPEG
        }
    }

    @Suppress("DEPRECATION")
    private fun compress(bmp: Bitmap, fmt: Fmt, quality: Int, stream: OutputStream) {
        when (fmt) {
            Fmt.PNG -> bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
            Fmt.JPEG -> {
                // JPEG شفافیت ندارد؛ بدون پس‌زمینه، بخش شفاف سیاه می‌شد
                val flat = if (bmp.hasAlpha()) flattenOnWhite(bmp) else bmp
                flat.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                if (flat !== bmp) flat.recycle()
            }
            Fmt.WEBP -> {
                val f = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
                bmp.compress(f, quality, stream)
            }
        }
    }

    private fun flattenOnWhite(src: Bitmap): Bitmap {
        val flat = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(flat)
        c.drawColor(Color.WHITE)
        c.drawBitmap(src, 0f, 0f, null)
        return flat
    }

    private fun write(sink: Sink, bmp: Bitmap, name: String, fmt: Fmt, quality: Int): Boolean {
        val target = sink.create(name, fmt.mime) ?: return false
        return try {
            BufferedOutputStream(target.stream).use { compress(bmp, fmt, quality, it) }
            target.commit()
            true
        } catch (e: Exception) {
            runCatching { target.stream.close() }
            target.abort()
            false
        }
    }

    // ------------------------------------------------------------------- names

    private fun displayName(context: Context, uri: Uri, index: Int): String {
        val fallback = "photo_${index + 1}"
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.') else null
                } ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }

    private fun clean(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun uniqueName(used: MutableSet<String>, base: String, suffix: String, ext: String): String {
        val stem = clean(base) + clean(suffix)
        var candidate = "$stem.$ext"
        var n = 2
        while (!used.add(candidate)) {
            candidate = "${stem}_$n.$ext"
            n++
        }
        return candidate
    }

    // ------------------------------------------------------------------ folder

    private fun treeDocument(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    /** هنوز اجازهٔ نوشتن در پوشه را داریم؟ (ممکن است پوشه پاک یا اجازه پس گرفته شده باشد) */
    fun isFolderUsable(context: Context, tree: Uri): Boolean {
        val granted = context.contentResolver.persistedUriPermissions
            .any { it.uri == tree && it.isWritePermission }
        if (!granted) return false
        return try {
            context.contentResolver.query(
                treeDocument(tree),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null
            )?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun folderLabel(context: Context, tree: Uri): String? = try {
        context.contentResolver.query(
            treeDocument(tree),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------------- sinks

    private class Target(
        val stream: OutputStream,
        val commit: () -> Unit,
        val abort: () -> Unit,
    )

    private interface Sink {
        fun create(name: String, mime: String): Target?
    }

    /** اندروید ۱۰ به بالا: MediaStore، بدون نیاز به مجوز */
    @RequiresApi(Build.VERSION_CODES.Q)
    private class MediaStoreSink(context: Context) : Sink {
        private val resolver = context.contentResolver

        override fun create(name: String, mime: String): Target? {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$GALLERY_DIR")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            val stream = try {
                resolver.openOutputStream(uri)
            } catch (e: Exception) {
                null
            }
            if (stream == null) {
                resolver.delete(uri, null, null)
                return null
            }
            return Target(
                stream,
                commit = {
                    val v = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    resolver.update(uri, v, null, null)
                },
                abort = { runCatching { resolver.delete(uri, null, null) } },
            )
        }
    }

    /** اندروید ۹ و پایین‌تر: فایل مستقیم در Pictures (نیاز به WRITE_EXTERNAL_STORAGE) */
    private class LegacySink(private val context: Context) : Sink {
        @Suppress("DEPRECATION")
        private val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), GALLERY_DIR
        ).also { it.mkdirs() }

        override fun create(name: String, mime: String): Target? {
            val stem = name.substringBeforeLast('.')
            val ext = name.substringAfterLast('.')
            var f = File(dir, name)
            var n = 2
            while (f.exists()) {
                f = File(dir, "${stem}_$n.$ext")
                n++
            }
            val stream = try {
                FileOutputStream(f)
            } catch (e: Exception) {
                return null
            }
            return Target(
                stream,
                commit = {
                    MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), arrayOf(mime), null)
                },
                abort = { f.delete() },
            )
        }
    }

    /** پوشهٔ دلخواه کاربر (Storage Access Framework) */
    private class FolderSink(context: Context, tree: Uri) : Sink {
        private val resolver = context.contentResolver
        private val parent = treeDocument(tree)

        override fun create(name: String, mime: String): Target? {
            val doc = try {
                DocumentsContract.createDocument(resolver, parent, mime, name)
            } catch (e: Exception) {
                null
            } ?: return null
            val stream = try {
                resolver.openOutputStream(doc)
            } catch (e: Exception) {
                null
            }
            if (stream == null) {
                runCatching { DocumentsContract.deleteDocument(resolver, doc) }
                return null
            }
            return Target(
                stream,
                commit = {},
                abort = { runCatching { DocumentsContract.deleteDocument(resolver, doc) } },
            )
        }
    }
}
