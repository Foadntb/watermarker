package com.watermarker.app

import android.app.Application
import android.graphics.Bitmap
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

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var settings by mutableStateOf(WatermarkSettings())
        private set
    var logo by mutableStateOf<Bitmap?>(null)
        private set
    var photos by mutableStateOf<List<Uri>>(emptyList())
        private set
    var preview by mutableStateOf<Bitmap?>(null)
        private set

    /** null یعنی در حال خروجی گرفتن نیستیم */
    var progress by mutableStateOf<Float?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    private var previewBase: Bitmap? = null
    private var previewJob: Job? = null

    fun update(block: WatermarkSettings.() -> WatermarkSettings) {
        settings = settings.block()
        refreshPreview()
    }

    fun setLogo(uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                WatermarkEngine.decode(getApplication(), uri, maxSide = 1024)
            }
            if (bmp == null) {
                message = "این فایل لوگو قابل خواندن نیست"
            } else {
                logo = bmp
                refreshPreview()
            }
        }
    }

    fun setPhotos(uris: List<Uri>) {
        photos = uris
        previewBase = null
        refreshPreview()
    }

    private fun refreshPreview() {
        previewJob?.cancel()
        val first = photos.firstOrNull() ?: run { preview = null; return }
        previewJob = viewModelScope.launch {
            delay(120)
            val result = withContext(Dispatchers.Default) {
                val base = previewBase ?: WatermarkEngine.decode(getApplication(), first, 1280)
                    ?.also { previewBase = it }
                base?.let { WatermarkEngine.apply(it, logo, settings) }
            }
            preview = result
        }
    }

    val canExport: Boolean
        get() = photos.isNotEmpty() && progress == null &&
            ((settings.mode != MarkMode.TEXT && logo != null) ||
                (settings.mode != MarkMode.LOGO && settings.text.isNotBlank()))

    fun export(target: Uri) {
        if (progress != null) return
        progress = 0f
        viewModelScope.launch {
            val r = try {
                Exporter.exportZip(getApplication(), photos, logo, settings, target) {
                    progress = it
                }
            } catch (e: Exception) {
                ExportResult(0, photos.size)
            }
            progress = null
            message = when {
                r.done == 0 -> "خروجی ساخته نشد"
                r.failed == 0 -> "${r.done} عکس در فایل ZIP ذخیره شد"
                else -> "${r.done} عکس ذخیره شد، ${r.failed} عکس ناموفق بود"
            }
        }
    }
}
