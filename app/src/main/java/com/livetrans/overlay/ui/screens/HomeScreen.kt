package com.livetrans.overlay.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetrans.overlay.data.*
import com.livetrans.overlay.service.LiveSubtitleService
import com.livetrans.overlay.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    isServiceRunning: Boolean,
    appPreferences: AppPreferences,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val currentStyle by appPreferences.styleFlow.collectAsState()
    val sourceLang by appPreferences.sourceLangFlow.collectAsState()
    val targetLang by appPreferences.targetLangFlow.collectAsState()
    val audioSource by appPreferences.audioSourceFlow.collectAsState()
    val engineType by appPreferences.engineTypeFlow.collectAsState()

    var testInputText by remember { mutableStateOf("Hello! Welcome to the real-time simultaneous translation test.") }
    var inAppTestResult by remember { mutableStateOf("") }
    var isTestingInProgress by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Slate900,
        topBar = {
            Surface(
                color = Slate900.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "实时同传字幕",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Indigo500.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "PRO",
                                    color = Indigo500,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "多语言悬浮窗 · 拉伸缩放 · 折叠收缩 · 零延迟",
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }

                    // Status Pill
                    Surface(
                        color = if (isServiceRunning) Emerald500.copy(alpha = 0.15f) else Slate800,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isServiceRunning) Emerald500.copy(alpha = 0.6f) else Slate700
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isServiceRunning) Emerald500 else Slate400)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isServiceRunning) "同传运行中" else "已就绪",
                                color = if (isServiceRunning) Emerald500 else Slate400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master Start / Stop Action Button with Gradient
            Button(
                onClick = {
                    if (isServiceRunning) onStopService() else onStartService()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) Rose500 else Indigo500
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isServiceRunning) "关闭悬浮窗字幕" else "开启悬浮同传字幕",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Section 1: Quick Interactive Testing Workbench (直接解决翻译/测试无反应问题)
            InteractiveTestCard(
                isServiceRunning = isServiceRunning,
                inputText = testInputText,
                onInputTextChange = { testInputText = it },
                onSendToOverlay = { textToSend ->
                    if (!isServiceRunning) {
                        onStartService()
                    }
                    LiveSubtitleService.sendTestSpeech(context, textToSend)
                },
                onQuickTestInApp = { textToTest ->
                    isTestingInProgress = true
                    coroutineScope.launch {
                        val engineManager = com.livetrans.overlay.engine.TranslationEngineManager(appPreferences)
                        val res = engineManager.translate(textToTest, sourceLang, targetLang)
                        inAppTestResult = if (res.isSuccess) {
                            "✔ 翻译成功: ${res.getOrNull()}"
                        } else {
                            "⚠️ 翻译异常: ${res.exceptionOrNull()?.message}"
                        }
                        isTestingInProgress = false
                    }
                },
                testResult = inAppTestResult,
                isLoading = isTestingInProgress
            )

            // Section 2: Subtitle Size & Stretch Customization (调整大小与拉伸收缩)
            WindowSizeAndStretchCard(
                style = currentStyle,
                onStyleChange = { appPreferences.updateSubtitleStyle(it) }
            )

            // Section 3: Subtitle Appearance Studio & Live Preview
            SubtitleStyleStudioCard(
                style = currentStyle,
                onStyleChange = { appPreferences.updateSubtitleStyle(it) }
            )

            // Section 4: Language Pair Switcher
            LanguagePairCard(
                sourceLang = sourceLang,
                targetLang = targetLang,
                onSwap = {
                    val prevSrc = sourceLang
                    appPreferences.setSourceLanguage(targetLang)
                    appPreferences.setTargetLanguage(prevSrc)
                },
                onSourceChange = { appPreferences.setSourceLanguage(it) },
                onTargetChange = { appPreferences.setTargetLanguage(it) }
            )

            // Section 5: Audio Source Mode
            AudioSourceCard(
                audioSource = audioSource,
                onSelectSource = { appPreferences.setAudioSource(it) }
            )

            // Section 6: Translation Engine Selector (Default Free Web Engine)
            EngineSelectionCard(
                engineType = engineType,
                appPreferences = appPreferences,
                onSelectEngine = { appPreferences.setEngineType(it) }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

/**
 * Interactive Live Testing Studio:
 * Allows user to test the translation engine instantly with 1 tap,
 * and verify the overlay responds and displays subtitles.
 */
@Composable
fun InteractiveTestCard(
    isServiceRunning: Boolean,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSendToOverlay: (String) -> Unit,
    onQuickTestInApp: (String) -> Unit,
    testResult: String,
    isLoading: Boolean
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Indigo500.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "实时同传测试台 (验证翻译与反应)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = Emerald500.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "免说话即可测试",
                        color = Emerald500,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "点击下方预设语句或输入文字，悬浮窗将即刻显示并实时翻译：",
                color = Slate400,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Preset Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        val sample = "Hello! Welcome to the real-time simultaneous translation test."
                        onInputTextChange(sample)
                        onSendToOverlay(sample)
                        onQuickTestInApp(sample)
                    },
                    label = { Text("🇬🇧 英语测试", fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Slate900)
                )
                AssistChip(
                    onClick = {
                        val sample = "こんにちは！リアルタイム同時通訳のテストです。"
                        onInputTextChange(sample)
                        onSendToOverlay(sample)
                        onQuickTestInApp(sample)
                    },
                    label = { Text("🇯🇵 日语测试", fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Slate900)
                )
                AssistChip(
                    onClick = {
                        val sample = "你好！很高兴认识你，欢迎体验实时同传悬浮字幕。"
                        onInputTextChange(sample)
                        onSendToOverlay(sample)
                        onQuickTestInApp(sample)
                    },
                    label = { Text("🇨🇳 中文测试", fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Slate900)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Custom Test Input Field
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入任意测试语句…", color = Slate400, fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo500,
                    unfocusedBorderColor = Slate700,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onSendToOverlay(inputText)
                        onQuickTestInApp(inputText)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "发送至悬浮窗翻译", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // In-app test result preview
            AnimatedVisibility(visible = testResult.isNotEmpty() || isLoading) {
                Surface(
                    color = Slate900,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(
                        text = if (isLoading) "⏳ 正在连接引擎翻译中…" else testResult,
                        color = if (testResult.startsWith("✔")) Amber400 else Slate400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

/**
 * Window Size & Stretch Settings Card:
 * Configures default window width, height, and explains stretch & fold gestures.
 */
@Composable
fun WindowSizeAndStretchCard(
    style: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "字幕框大小与拉伸控制",
                    color = Slate400,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Surface(
                    color = Indigo500.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "支持右下角自由拉伸 ↘",
                        color = Indigo500,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Gesture Tip Banner
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate700.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.TouchApp, contentDescription = null, tint = Cyan400, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "如何操作悬浮窗拉伸与收缩？", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1. 【自由拉伸】：按住悬浮窗右下角斜纹手柄 ↘ 即可随手拉大或拉小宽度与高度。\n2. 【顶部收缩】：点击悬浮窗顶部的 ▲ 图标，即可瞬间折叠为极窄迷你胶囊条，完全不挡屏幕！",
                        color = Slate400,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Width Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "字幕框初始宽度", color = Color.White, fontSize = 13.sp)
                Text(text = "${style.windowWidthDp} dp", color = Indigo500, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = style.windowWidthDp.toFloat(),
                onValueChange = { onStyleChange(style.copy(windowWidthDp = it.toInt())) },
                valueRange = 240f..400f,
                steps = 7,
                colors = SliderDefaults.colors(thumbColor = Indigo500, activeTrackColor = Indigo500)
            )

            // Height Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "字幕框初始高度", color = Color.White, fontSize = 13.sp)
                Text(text = "${style.windowHeightDp} dp", color = Indigo500, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = style.windowHeightDp.toFloat(),
                onValueChange = { onStyleChange(style.copy(windowHeightDp = it.toInt())) },
                valueRange = 100f..260f,
                steps = 7,
                colors = SliderDefaults.colors(thumbColor = Indigo500, activeTrackColor = Indigo500)
            )
        }
    }
}

/**
 * Subtitle Appearance Studio & Live Preview Card
 */
@Composable
fun SubtitleStyleStudioCard(
    style: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "字幕颜色与视觉工作台",
                    color = Slate400,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "所见即所得实时预览",
                    color = Amber400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Realistic Video Overlay Simulation Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                        )
                    )
                    .padding(14.dp)
            ) {
                Surface(
                    color = Color(style.backgroundColor),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Simulated Overlay Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Emerald500)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "EN ➔ ZH", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Row {
                                Icon(imageVector = Icons.Default.ExpandLess, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (style.showOriginalText) {
                            Text(
                                text = "Hello, welcome to real-time subtitle translation.",
                                color = Color(style.originalTextColor),
                                fontSize = style.originalTextSizeSp.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Text(
                            text = "你好，欢迎使用实时同传悬浮字幕。",
                            color = Color(style.textColor),
                            fontSize = style.textSizeSp.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "✔ 同传已就绪", color = Color(0xFF64748B), fontSize = 9.sp)
                            Icon(imageVector = Icons.Default.OpenInFull, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle Font Color Palette
            Text(text = "选择字体高对比度颜色", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SubtitleStyle.PRESET_COLORS.forEach { colorInt ->
                    val composeColor = Color(colorInt)
                    val isSelected = style.textColor == colorInt
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(composeColor)
                            .clickable { onStyleChange(style.copy(textColor = colorInt)) }
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Font Size Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "字幕字号大小", color = Color.White, fontSize = 13.sp)
                Text(text = "${style.textSizeSp.toInt()} sp", color = Indigo500, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = style.textSizeSp,
                onValueChange = { onStyleChange(style.copy(textSizeSp = it)) },
                valueRange = 14f..28f,
                steps = 6,
                colors = SliderDefaults.colors(thumbColor = Indigo500, activeTrackColor = Indigo500)
            )

            // Background Opacity Slider
            val currentAlpha = (AndroidColor.alpha(style.backgroundColor) / 255f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "悬浮窗背景不透明度", color = Color.White, fontSize = 13.sp)
                Text(text = "${(currentAlpha * 100).toInt()}%", color = Indigo500, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = currentAlpha,
                onValueChange = { alpha ->
                    val newAlphaInt = (alpha * 255).toInt().coerceIn(0, 255)
                    val baseColor = style.backgroundColor and 0x00FFFFFF
                    val newBg = (newAlphaInt shl 24) or baseColor
                    onStyleChange(style.copy(backgroundColor = newBg))
                },
                valueRange = 0.0f..1.0f,
                colors = SliderDefaults.colors(thumbColor = Indigo500, activeTrackColor = Indigo500)
            )

            // Switch: Show Original Text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "双语对照模式", color = Color.White, fontSize = 13.sp)
                    Text(text = "同时显示原文与翻译字幕", color = Slate400, fontSize = 11.sp)
                }
                Switch(
                    checked = style.showOriginalText,
                    onCheckedChange = { onStyleChange(style.copy(showOriginalText = it)) }
                )
            }

            // Switch: Text Shadow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "文字边缘阴影", color = Color.White, fontSize = 13.sp)
                    Text(text = "增强在浅色明亮背景下的可读性", color = Slate400, fontSize = 11.sp)
                }
                Switch(
                    checked = style.enableTextShadow,
                    onCheckedChange = { onStyleChange(style.copy(enableTextShadow = it)) }
                )
            }
        }
    }
}

@Composable
fun LanguagePairCard(
    sourceLang: Language,
    targetLang: Language,
    onSwap: () -> Unit,
    onSourceChange: (Language) -> Unit,
    onTargetChange: (Language) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "翻译语种配置",
                color = Slate400,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LanguageDropdown(
                    label = "输入语言",
                    selected = sourceLang,
                    onSelect = onSourceChange,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onSwap,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clip(CircleShape)
                        .background(Slate700)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Swap Languages",
                        tint = Indigo500
                    )
                }

                LanguageDropdown(
                    label = "译文语言",
                    selected = targetLang,
                    onSelect = onTargetChange,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun LanguageDropdown(
    label: String,
    selected: Language,
    onSelect: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(text = label, color = Slate400, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            color = Slate900,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selected.displayName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Slate400
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Language.entries.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(text = "${lang.displayName} (${lang.shortCode})") },
                        onClick = {
                            onSelect(lang)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AudioSourceCard(
    audioSource: AudioSourceType,
    onSelectSource: (AudioSourceType) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "音频输入来源",
                color = Slate400,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AudioSourceOption(
                    title = "手机系统内录",
                    desc = "刷剧/生肉无需外放",
                    icon = Icons.Default.VolumeUp,
                    isSelected = audioSource == AudioSourceType.SYSTEM_INTERNAL,
                    onClick = { onSelectSource(AudioSourceType.SYSTEM_INTERNAL) },
                    modifier = Modifier.weight(1f)
                )

                AudioSourceOption(
                    title = "麦克风拾音",
                    desc = "面对面/外放环境交流",
                    icon = Icons.Default.Mic,
                    isSelected = audioSource == AudioSourceType.MIC,
                    onClick = { onSelectSource(AudioSourceType.MIC) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun AudioSourceOption(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) Indigo500.copy(alpha = 0.15f) else Slate900,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isSelected) Indigo500 else Color.Transparent
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Indigo500 else Slate400,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = Slate400,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun EngineSelectionCard(
    engineType: TranslationEngineType,
    appPreferences: AppPreferences,
    onSelectEngine: (TranslationEngineType) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "翻译引擎与模型选择",
                color = Slate400,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 1. FREE_WEB (Default Recommended)
            EngineOptionRow(
                title = "免费极速网页引擎 (推荐·即开即用)",
                badge = "100%免费·国内直连·免下载",
                description = "无需下载离线大模型，无需配置任何API Key，毫秒级响应，任何设备开箱即用。",
                isSelected = engineType == TranslationEngineType.FREE_WEB,
                onClick = { onSelectEngine(TranslationEngineType.FREE_WEB) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Google ML Kit (Local Offline)
            EngineOptionRow(
                title = "Google ML Kit (本地离线神经网络)",
                badge = "断网可用·需外网下模型",
                description = "端侧神经网络推理，首次需从谷歌服务器下载模型包（国内网络需配合科学环境下载）。",
                isSelected = engineType == TranslationEngineType.LOCAL_ML_KIT,
                onClick = { onSelectEngine(TranslationEngineType.LOCAL_ML_KIT) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. DeepL API
            EngineOptionRow(
                title = "DeepL API (高精度云端)",
                badge = "官方免费50万字/月",
                description = "业内顶尖翻译自然度与生动度，需自备免费或专业版 API Key。",
                isSelected = engineType == TranslationEngineType.CLOUD_DEEPL,
                onClick = { onSelectEngine(TranslationEngineType.CLOUD_DEEPL) }
            )

            AnimatedVisibility(visible = engineType == TranslationEngineType.CLOUD_DEEPL) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    var apiKey by remember { mutableStateOf(appPreferences.deepLApiKey) }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            appPreferences.deepLApiKey = it
                        },
                        label = { Text("DeepL Auth Key (如 xxxxxxxx:fx)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Cloud LLM
            EngineOptionRow(
                title = "AI大语言模型 (DeepSeek / OpenAI)",
                badge = "上下文同传",
                description = "利用大模型理解俚语、专业术语并进行同传润色，兼容各大 OpenAI 协议接口。",
                isSelected = engineType == TranslationEngineType.CLOUD_LLM,
                onClick = { onSelectEngine(TranslationEngineType.CLOUD_LLM) }
            )

            AnimatedVisibility(visible = engineType == TranslationEngineType.CLOUD_LLM) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var apiKey by remember { mutableStateOf(appPreferences.llmApiKey) }
                    var baseUrl by remember { mutableStateOf(appPreferences.llmBaseUrl) }
                    var modelName by remember { mutableStateOf(appPreferences.llmModelName) }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            appPreferences.llmApiKey = it
                        },
                        label = { Text("API Key (如 sk-...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            appPreferences.llmBaseUrl = it
                        },
                        label = { Text("Base URL (如 https://api.deepseek.com/v1)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = {
                            modelName = it
                            appPreferences.llmModelName = it
                        },
                        label = { Text("模型名称 (如 deepseek-chat)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }
    }
}

@Composable
fun EngineOptionRow(
    title: String,
    badge: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) Indigo500.copy(alpha = 0.12f) else Slate900,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isSelected) Indigo500 else Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = if (isSelected) Emerald500.copy(alpha = 0.2f) else Slate800,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badge,
                        color = if (isSelected) Emerald500 else Slate400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = Slate400,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}
