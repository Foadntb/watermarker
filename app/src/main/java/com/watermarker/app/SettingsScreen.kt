package com.watermarker.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val MIN_SIDE = 64

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val o = vm.output

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(vm::setFolder) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("تنظیمات خروجی") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "بازگشت")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ------------------------------------------------------------- مقصد
            AmplifyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("محل ذخیره", style = MaterialTheme.typography.titleMedium)

                    val dests = listOf(Destination.GALLERY to "گالری", Destination.FOLDER to "پوشهٔ دلخواه")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        dests.forEachIndexed { i, (d, label) ->
                            SegmentedButton(
                                selected = o.destination == d,
                                onClick = { vm.updateOutput { copy(destination = d) } },
                                shape = SegmentedButtonDefaults.itemShape(i, dests.size),
                            ) { Text(label) }
                        }
                    }

                    if (o.destination == Destination.GALLERY) {
                        Hint("عکس‌ها در پوشهٔ Pictures/${Exporter.GALLERY_DIR} گالری ذخیره می‌شوند.")
                    } else {
                        Text(
                            vm.folderName ?: if (o.folderUri == null) "هنوز پوشه‌ای انتخاب نشده" else "پوشهٔ انتخاب‌شده",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        FilledTonalButton(
                            shape = MaterialTheme.shapes.small,
                            onClick = { pickFolder.launch(null) },
                        ) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(if (o.folderUri == null) "انتخاب پوشه" else "تغییر پوشه")
                        }
                    }
                }
            }

            // ------------------------------------------------------------ فرمت
            AmplifyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("فرمت و کیفیت", style = MaterialTheme.typography.titleMedium)

                    val formats = listOf(
                        OutFormat.ORIGINAL to "مثل اصلی",
                        OutFormat.JPEG to "JPEG",
                        OutFormat.PNG to "PNG",
                        OutFormat.WEBP to "WebP",
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        formats.forEachIndexed { i, (f, label) ->
                            SegmentedButton(
                                selected = o.format == f,
                                onClick = { vm.updateOutput { copy(format = f) } },
                                shape = SegmentedButtonDefaults.itemShape(i, formats.size),
                            ) { Text(label, maxLines = 1) }
                        }
                    }

                    if (o.format == OutFormat.ORIGINAL) {
                        Hint("PNG بماند PNG، WebP بماند WebP و بقیه JPEG شوند.")
                    }
                    if (o.format == OutFormat.PNG) {
                        Hint("PNG بدون افت کیفیت است، پس کیفیت ندارد.")
                    } else {
                        Text(
                            "کیفیت: ${o.quality}٪",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Slider(
                            value = o.quality.toFloat(),
                            onValueChange = { v -> vm.updateOutput { copy(quality = v.roundToInt()) } },
                            valueRange = 50f..100f,
                        )
                        if (o.format == OutFormat.ORIGINAL) {
                            Hint("کیفیت فقط برای خروجی‌های JPEG و WebP اعمال می‌شود.")
                        }
                    }
                }
            }

            // ------------------------------------------------------------ اندازه
            AmplifyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("اندازه", style = MaterialTheme.typography.titleMedium)

                    val modes = listOf(SizeMode.MAX_SIDE to "حداکثر ضلع", SizeMode.PERCENT to "درصد اندازهٔ اصلی")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, (m, label) ->
                            SegmentedButton(
                                selected = o.sizeMode == m,
                                onClick = { vm.updateOutput { copy(sizeMode = m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(label, maxLines = 1) }
                        }
                    }

                    if (o.sizeMode == SizeMode.MAX_SIDE) {
                        var text by remember { mutableStateOf(o.maxSide.toString()) }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { v ->
                                val digits = v.filter { it.isDigit() }.take(5)
                                text = digits
                                digits.toIntOrNull()?.takeIf { it >= MIN_SIDE }?.let { n ->
                                    vm.updateOutput { copy(maxSide = n) }
                                }
                            },
                            label = { Text("بلندترین ضلع (پیکسل)") },
                            singleLine = true,
                            isError = (text.toIntOrNull() ?: 0) < MIN_SIDE,
                            supportingText = {
                                if ((text.toIntOrNull() ?: 0) < MIN_SIDE) Text("حداقل $MIN_SIDE پیکسل")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1080, 1600, 2560, 4096).forEach { px ->
                                FilterChip(
                                    selected = o.maxSide == px,
                                    onClick = {
                                        text = px.toString()
                                        vm.updateOutput { copy(maxSide = px) }
                                    },
                                    label = { Text(px.toString()) },
                                )
                            }
                        }
                        Hint("عکس‌های کوچک‌تر از این اندازه بزرگ نمی‌شوند.")
                    } else {
                        Text(
                            "اندازه: ${o.percent}٪ عکس اصلی",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Slider(
                            value = o.percent.toFloat(),
                            onValueChange = { v -> vm.updateOutput { copy(percent = v.roundToInt()) } },
                            valueRange = 10f..100f,
                            steps = 17, // پله‌های ۵ درصدی
                        )
                        if (o.percent == 100) Hint("اندازهٔ کامل؛ روی عکس‌های خیلی بزرگ ممکن است حافظه کم بیاید.")
                    }

                    vm.firstPhotoSide?.let { src ->
                        val dst = minOf(src, Exporter.targetSide(o, src))
                        Text(
                            "عکس اول: بلندترین ضلع از $src به $dst پیکسل",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // ------------------------------------------------------------ نام فایل
            AmplifyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("نام فایل", style = MaterialTheme.typography.titleMedium)
                    var suffix by remember { mutableStateOf(o.suffix) }
                    OutlinedTextField(
                        value = suffix,
                        onValueChange = { v ->
                            suffix = v
                            vm.updateOutput { copy(suffix = v) }
                        },
                        label = { Text("پسوند بعد از نام اصلی") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Hint("مثال: photo$suffix.jpg")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
