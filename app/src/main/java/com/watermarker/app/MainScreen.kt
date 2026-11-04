package com.watermarker.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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

    val context = LocalContext.current
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // اندروید ۹ و پایین‌تر برای نوشتن در Pictures مجوز می‌خواهد
    val askStorage = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        if (ok) vm.export() else vm.message = "بدون مجوز ذخیره‌سازی نمی‌شود در گالری ذخیره کرد"
    }
    fun startExport() {
        val toGallery = vm.output.destination == Destination.GALLERY
        val needsPermission = toGallery && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) askStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else vm.export()
    }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val s = vm.settings

    if (showSettings) {
        SettingsScreen(vm, onBack = { showSettings = false })
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("واترمارک‌گذار") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "تنظیمات خروجی")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (vm.canExport) {
                        startExport()
                    } else {
                        vm.message = if (vm.photos.isEmpty()) {
                            "اول عکس‌ها را انتخاب کن"
                        } else {
                            "لوگو یا متن واترمارک را مشخص کن"
                        }
                    }
                },
                shape = MaterialTheme.shapes.small,
                icon = { Icon(Icons.Outlined.Save, contentDescription = null) },
                text = { Text("ذخیره عکس‌ها") },
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
            vm.progress?.let { p ->
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            }

            // ------------------------------------------------ پیش‌نمایش / عکس‌ها
            AmplifyCard(Modifier.fillMaxWidth()) {
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
                        FilledTonalButton(shape = MaterialTheme.shapes.small, onClick ={ pickPhotos.launch(imageOnly) }) {
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
            AmplifyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("واترمارک", style = MaterialTheme.typography.titleMedium)

                    val modes = listOf(MarkMode.LOGO to "لوگو", MarkMode.TEXT to "متن", MarkMode.BOTH to "هر دو")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = s.mode == mode,
                                onClick = { vm.update { copy(mode = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(label) }
                        }
                    }

                    if (s.mode != MarkMode.TEXT) {
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
                            FilledTonalButton(shape = MaterialTheme.shapes.small, onClick ={ pickLogo.launch(imageOnly) }) {
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

                    if (s.mode != MarkMode.LOGO) {
                        OutlinedTextField(
                            value = s.text,
                            onValueChange = { v -> vm.update { copy(text = v) } },
                            label = { Text("متن واترمارک") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Text(
                        "اندازه: ${s.sizePercent.roundToInt()}٪ عرض عکس",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = s.sizePercent,
                        onValueChange = { v -> vm.update { copy(sizePercent = v) } },
                        valueRange = 8f..50f,
                    )

                    Text(
                        "شفافیت: ${(s.opacity * 100).roundToInt()}٪",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = s.opacity,
                        onValueChange = { v -> vm.update { copy(opacity = v) } },
                        valueRange = 0.1f..1f,
                    )
                }
            }

            // ------------------------------------------------------------ جایگذاری
            AmplifyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("جایگذاری", style = MaterialTheme.typography.titleMedium)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("تکرار مورب روی کل عکس")
                            Text(
                                "برای محافظت بیشتر در برابر برش‌دادن",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = s.tiled,
                            onCheckedChange = { v -> vm.update { copy(tiled = v) } },
                        )
                    }

                    if (!s.tiled) {
                        FilterChip(
                            selected = s.placement == Placement.AUTO,
                            onClick = { vm.update { copy(placement = Placement.AUTO) } },
                            label = { Text("خودکار و هوشمند") },
                        )

                        Text(
                            "یا یکی از جاها را خودت انتخاب کن:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // شبکهٔ جای‌ها باید مثل خود عکس چپ‌به‌راست باشد
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (r in 0..2) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        for (c in 0..2) {
                                            val p = Placement.values()[1 + r * 3 + c]
                                            val selected = s.placement == p
                                            FilledTonalIconToggleButton(
                                                shape = MaterialTheme.shapes.small,
                                                checked = selected,
                                                onCheckedChange = { vm.update { copy(placement = p) } },
                                            ) {
                                                if (selected) {
                                                    Icon(Icons.Outlined.Check, contentDescription = null)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (s.placement == Placement.AUTO) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("دوری از چهره‌ها")
                                    Text(
                                        "با تشخیص چهره، واترمارک روی صورت نمی‌افتد",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = s.avoidFaces,
                                    onCheckedChange = { v -> vm.update { copy(avoidFaces = v) } },
                                )
                            }
                        }
                    }
                }
            }

            // فضای خالی برای اینکه FAB روی محتوا نیفتد
            Spacer(Modifier.height(88.dp))
        }
    }
}

/** کارت تخت با حاشیهٔ نازک و گوشهٔ کم‌گرد، مثل Card در Amplify UI */
@Composable
internal fun AmplifyCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}
