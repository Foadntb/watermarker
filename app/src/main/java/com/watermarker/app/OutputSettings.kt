package com.watermarker.app

enum class OutFormat { ORIGINAL, JPEG, PNG, WEBP }

enum class SizeMode { MAX_SIDE, PERCENT }

enum class Destination { GALLERY, FOLDER }

/** تنظیمات خروجی: فرمت، کیفیت، اندازه، نام فایل و مقصد ذخیره */
data class OutputSettings(
    val format: OutFormat = OutFormat.ORIGINAL,
    /** فقط برای JPEG و WebP */
    val quality: Int = 95,
    val sizeMode: SizeMode = SizeMode.MAX_SIDE,
    /** بلندترین ضلع خروجی به پیکسل (عکس کوچک‌تر بزرگ نمی‌شود) */
    val maxSide: Int = 4096,
    /** درصد از اندازهٔ اصلی */
    val percent: Int = 100,
    /** به آخر نام اصلی اضافه می‌شود */
    val suffix: String = "_wm",
    val destination: Destination = Destination.GALLERY,
    /** آدرس درخت پوشهٔ انتخاب‌شده (SAF) */
    val folderUri: String? = null,
)
