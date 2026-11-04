package com.watermarker.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportResult(val done: Int, val failed: Int)

object Exporter {

    suspend fun exportZip(
        context: Context,
        photos: List<Uri>,
        logo: Bitmap?,
        settings: WatermarkSettings,
        target: Uri,
        onProgress: (Float) -> Unit,
    ): ExportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var done = 0
        var failed = 0
        val usedNames = HashSet<String>()

        val stream = resolver.openOutputStream(target)
            ?: return@withContext ExportResult(0, photos.size)

        ZipOutputStream(BufferedOutputStream(stream)).use { zip ->
            photos.forEachIndexed { index, uri ->
                try {
                    val base = WatermarkEngine.decode(context, uri)
                    if (base == null) {
                        failed++
                    } else {
                        val result = WatermarkEngine.apply(base, logo, settings)
                        base.recycle()

                        val isPng = resolver.getType(uri) == "image/png"
                        val ext = if (isPng) "png" else "jpg"
                        val name = uniqueName(usedNames, displayName(context, uri, index), ext)

                        zip.putNextEntry(ZipEntry(name))
                        if (isPng) {
                            result.compress(Bitmap.CompressFormat.PNG, 100, zip)
                        } else {
                            result.compress(Bitmap.CompressFormat.JPEG, 95, zip)
                        }
                        zip.closeEntry()
                        result.recycle()
                        done++
                    }
                } catch (e: Exception) {
                    failed++
                } catch (e: OutOfMemoryError) {
                    failed++
                }
                onProgress((index + 1).toFloat() / photos.size)
            }
        }
        ExportResult(done, failed)
    }

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

    private fun uniqueName(used: MutableSet<String>, base: String, ext: String): String {
        val clean = base.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        var candidate = "${clean}_wm.$ext"
        var n = 2
        while (!used.add(candidate)) {
            candidate = "${clean}_wm_$n.$ext"
            n++
        }
        return candidate
    }
}
