package com.watermarker.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }

    val pickLogo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(vm::setLogo) }

    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> if (uris.isNotEmpty()) vm.selectPhotos(uris) }

    val createZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(vm::export) }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("واترمارک‌گذار") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (vm.canExport) createZip.launch("watermarked_photos.zip")
                    else vm.message = when {
                        vm.photos.isEmpty() -> "اول عکس‌ها را انتخاب کن"
                        else -> "لوگو یا متن واترمارک را مشخص کن"
                    }
                },
                icon = { Icon(Icons.Outlined.FolderZip, contentDescription = null) },
                text = { Text("ساخت فایل ZIP") },
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
            vm.progress?.let {
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
            }

            // ------------------------------------------------ پیش‌نمایش / عکس‌ها
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("عکس‌ها", style = MaterialTheme.typography.titleMedium)

                    val preview = vm.preview
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "پیش‌نمایش",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "برای دیدن پیش‌نمایش، عکس انتخاب کن",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { pickPhotos.launch(imageOnly) }) {
                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("انتخاب عکس‌ها")
                        }
                        Spacer(Modifier.size(12.dp))
                        if (vm.photos.isNotEmpty()) {
                            Text(
                                "${vm.photos.size} عکس انتخاب شد",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------- واترمارک
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("واترمارک", style = MaterialTheme.typography.titleMedium)

                    val modes = listOf(MarkMode.LOGO to "لوگو", MarkMode.TEXT to "متن", MarkMode.BOTH to "هر دو")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = vm.settings.mode == mode,
                                onClick = { vm.update { copy(mode = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(label) }
                        }
                    }

                    if (vm.settings.mode != MarkMode.TEXT) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val logo = vm.logo
                            if (logo != null) {
                                Image(
                                    bitmap = logo.asImageBitmap(),
                                    contentDescription = "لوگو",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                                Spacer(Modifier.size(12.dp))
                            }
                            FilledTonalButton(onClick = { pickLogo.launch(imageOnly) }) {
                                Icon(Icons.Outlined.Image, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(if (logo == null) "انتخاب لوگو" else "تغییر لوگو")
                            }
                        }
                        if (vm.logo == null) {
                            Text(
                                "بهتر است PNG با پس‌زمینهٔ شفاف باشد",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (vm.settings.mode != MarkMode.LOGO) {
                        OutlinedTextField(
                            value = vm.settings.text,
                            onValueChange = { v -> vm.update { copy(text = v) } },
                            label = { Text("متن واترمارک") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Text(
                        "اندازه: ${vm.settings.sizePercent.roundToInt()}٪ عرض عکس",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = vm.settings.sizePercent,
                        onValueChange = { v -> vm.update { copy(sizePercent = v) } },
                        valueRange = 8f..50f,
                    )

                    Text(
                        "شفافیت: ${(vm.settings.opacity * 100).roundToInt()}٪",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = vm.settings.opacity,
                        onValueChange = { v -> vm.update { copy(opacity = v) } },
                        valueRange = 0.1f..1f,
                    )

                    Text(
                        "جای واترمارک برای هر عکس خودکار انتخاب می‌شود: کم‌شلوغ‌ترین گوشه یا لبه.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // فضای خالی برای اینکه FAB روی محتوا نیفتد
            Spacer(Modifier.height(88.dp))
        }
    }
}
