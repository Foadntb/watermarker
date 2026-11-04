package com.watermarker.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val logoFile = File(app.filesDir, "logo.png")

    var settings by mutableStateOf(loadSettings())
        private set
    var logo by mutableStateOf<Bitmap?>(null)
        private set
    var photos by mutableStateOf<List<Uri>>(emptyList())
        private set
    var preview by mutableStateOf<Bitmap?>(null)
        private set

    var output by mutableStateOf(loadOutput())
        private set
    /** نام پوشهٔ دلخواه برای نمایش در تنظیمات */
    var folderName by mutableStateOf<String?>(null)
        private set
    /** بلندترین ضلع عکس اول (پیکسل)، برای نمایش اندازهٔ خروجی در تنظیمات */
    var firstPhotoSide by mutableStateOf<Int?>(null)
        private set

    /** null یعنی در حال خروجی گرفتن نیستیم */
    var progress by mutableStateOf<Float?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    private var previewBase: Bitmap? = null
    private var previewFaces: List<RectF>? = null
    private var previewJob: Job? = null

    init {
        refreshFolderName()
        // لوگوی ذخیره‌شدهٔ دفعهٔ قبل را برگردان
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                if (logoFile.exists()) BitmapFactory.decodeFile(logoFile.absolutePath) else null
            }
            if (saved != null && logo == null) {
                logo = saved
                refreshPreview()
            }
        }
    }

    // --------------------------------------------------------------- settings

    private fun loadSettings(): WatermarkSettings {
        val d = WatermarkSettings()
        return WatermarkSettings(
            mode = runCatching { MarkMode.valueOf(prefs.getString("mode", d.mode.name) ?: d.mode.name) }
                .getOrDefault(d.mode),
            text = prefs.getString("text", d.text) ?: d.text,
            sizePercent = prefs.getFloat("size", d.sizePercent),
            opacity = prefs.getFloat("opacity", d.opacity),
            placement = runCatching {
                Placement.valueOf(prefs.getString("placement", d.placement.name) ?: d.placement.name)
            }.getOrDefault(d.placement),
            tiled = prefs.getBoolean("tiled", d.tiled),
            avoidFaces = prefs.getBoolean("faces", d.avoidFaces),
        )
    }

    private fun saveSettings(s: WatermarkSettings) {
        prefs.edit()
            .putString("mode", s.mode.name)
            .putString("text", s.text)
            .putFloat("size", s.sizePercent)
            .putFloat("opacity", s.opacity)
            .putString("placement", s.placement.name)
            .putBoolean("tiled", s.tiled)
            .putBoolean("faces", s.avoidFaces)
            .apply()
    }

    private fun loadOutput(): OutputSettings {
        val d = OutputSettings()
        return OutputSettings(
            format = runCatching { OutFormat.valueOf(prefs.getString("o_format", d.format.name) ?: d.format.name) }
                .getOrDefault(d.format),
            quality = prefs.getInt("o_quality", d.quality).coerceIn(50, 100),
            sizeMode = runCatching { SizeMode.valueOf(prefs.getString("o_size_mode", d.sizeMode.name) ?: d.sizeMode.name) }
                .getOrDefault(d.sizeMode),
            maxSide = prefs.getInt("o_max_side", d.maxSide).coerceIn(64, 20000),
            percent = prefs.getInt("o_percent", d.percent).coerceIn(10, 100),
            suffix = prefs.getString("o_suffix", d.suffix) ?: d.suffix,
            destination = runCatching {
                Destination.valueOf(prefs.getString("o_dest", d.destination.name) ?: d.destination.name)
            }.getOrDefault(d.destination),
            folderUri = prefs.getString("o_folder", null),
        )
    }

    private fun saveOutput(o: OutputSettings) {
        prefs.edit()
            .putString("o_format", o.format.name)
            .putInt("o_quality", o.quality)
            .putString("o_size_mode", o.sizeMode.name)
            .putInt("o_max_side", o.maxSide)
            .putInt("o_percent", o.percent)
            .putString("o_suffix", o.suffix)
            .putString("o_dest", o.destination.name)
            .putString("o_folder", o.folderUri)
            .apply()
    }

    fun updateOutput(block: OutputSettings.() -> OutputSettings) {
        output = output.block()
        saveOutput(output)
    }

    /** کاربر پوشه را انتخاب کرده؛ اجازهٔ دائمی می‌گیریم تا دفعهٔ بعد هم کار کند */
    fun setFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            message = "اجازهٔ نوشتن در این پوشه گرفته نشد"
            return
        }
        // اجازهٔ پوشهٔ قبلی دیگر لازم نیست
        output.folderUri?.let(Uri::parse)?.takeIf { it != uri }?.let {
            runCatching {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(it, flags)
            }
        }
        updateOutput { copy(folderUri = uri.toString(), destination = Destination.FOLDER) }
        refreshFolderName()
    }

    private fun refreshFolderName() {
        val uri = output.folderUri?.let(Uri::parse)
        if (uri == null) {
            folderName = null
            return
        }
        viewModelScope.launch {
            folderName = withContext(Dispatchers.IO) { Exporter.folderLabel(getApplication(), uri) }
        }
    }

    fun update(block: WatermarkSettings.() -> WatermarkSettings) {
        settings = settings.block()
        saveSettings(settings)
        refreshPreview()
    }

    // ------------------------------------------------------------------ inputs

    fun setLogo(uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                WatermarkEngine.decode(getApplication(), uri, maxSide = 1024)?.also { b ->
                    try {
                        FileOutputStream(logoFile).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } catch (e: Exception) {
                        // ذخیرهٔ لوگو اختیاری است؛ اگر نشد، فقط برای همین بار می‌ماند
                    }
                }
            }
            if (bmp == null) {
                message = "این فایل لوگو قابل خواندن نیست"
            } else {
                logo = bmp
                refreshPreview()
            }
        }
    }

    fun selectPhotos(uris: List<Uri>) {
        photos = uris
        firstPhotoSide = null
        uris.firstOrNull()?.let { first ->
            viewModelScope.launch {
                val side = withContext(Dispatchers.IO) {
                    WatermarkEngine.sourceLongestSide(getApplication(), first)
                }
                if (photos.firstOrNull() == first && side > 0) firstPhotoSide = side
            }
        }
        previewBase = null
        previewFaces = null
        refreshPreview()
    }

    // ----------------------------------------------------------------- preview

    private fun refreshPreview() {
        previewJob?.cancel()
        val first = photos.firstOrNull() ?: run { preview = null; return }
        previewJob = viewModelScope.launch {
            delay(120)
            val s = settings
            val result = withContext(Dispatchers.Default) {
                val base = previewBase
                    ?: WatermarkEngine.decode(getApplication(), first, 1280)?.also { previewBase = it }
                if (base == null) {
                    null
                } else {
                    val needFaces = s.avoidFaces && s.placement == Placement.AUTO && !s.tiled
                    val faces = if (needFaces) {
                        previewFaces ?: FaceFinder.detect(base).also { previewFaces = it }
                    } else {
                        emptyList()
                    }
                    WatermarkEngine.apply(base, logo, s, faces)
                }
            }
            preview = result
        }
    }

    // ------------------------------------------------------------------ export

    val canExport: Boolean
        get() = photos.isNotEmpty() && progress == null &&
            ((settings.mode != MarkMode.TEXT && logo != null) ||
                (settings.mode != MarkMode.LOGO && settings.text.isNotBlank()))

    fun export() {
        if (progress != null) return
        val out = output
        if (out.destination == Destination.FOLDER) {
            val folder = out.folderUri?.let(Uri::parse)
            if (folder == null) {
                message = "اول پوشهٔ ذخیره را در تنظیمات انتخاب کن"
                return
            }
            if (!Exporter.isFolderUsable(getApplication(), folder)) {
                message = "به پوشهٔ انتخاب‌شده دسترسی نداریم؛ در تنظیمات دوباره انتخابش کن"
                return
            }
        }
        progress = 0f
        viewModelScope.launch {
            val r = try {
                Exporter.export(getApplication(), photos, logo, settings, out) { progress = it }
            } catch (e: Exception) {
                ExportResult(0, photos.size)
            }
            progress = null
            message = when {
                r.done == 0 -> "ذخیره نشد؛ مقصد یا اندازهٔ خروجی را بررسی کن"
                r.failed == 0 -> "${r.done} عکس در ${r.where} ذخیره شد"
                else -> "${r.done} عکس در ${r.where} ذخیره شد، ${r.failed} عکس ناموفق بود"
            }
        }
    }
}
