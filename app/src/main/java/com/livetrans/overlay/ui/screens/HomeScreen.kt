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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetrans.overlay.data.*
import com.livetrans.overlay.ui.theme.*

@Composable
fun HomeScreen(
    isServiceRunning: Boolean,
    appPreferences: AppPreferences,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val scrollState = rememberScrollState()

    val currentStyle by appPreferences.styleFlow.collectAsState()
    val sourceLang by appPreferences.sourceLangFlow.collectAsState()
    val targetLang by appPreferences.targetLangFlow.collectAsState()
    val audioSource by appPreferences.audioSourceFlow.collectAsState()
    val engineType by appPreferences.engineTypeFlow.collectAsState()

    var showApiSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Slate900,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "实时同传字幕",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "多语言悬浮窗 · 本地离线 · 双向同传",
                            color = Slate400,
                            fontSize = 13.sp
                        )
                    }

                    // Status Badge
                    Surface(
                        color = if (isServiceRunning) Emerald500.copy(alpha = 0.2f) else Slate800,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isServiceRunning) Emerald500 else Slate700
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
                                text = if (isServiceRunning) "同传中" else "未启动",
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master Action Button
            Button(
                onClick = {
                    if (isServiceRunning) onStopService() else onStartService()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) Rose500 else Indigo500
                )
            ) {
                Icon(
                    imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isServiceRunning) "停止悬浮窗与同传" else "开启悬浮字幕同传",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Section 1: Language Pairs
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

            // Section 2: Audio Source Selection
            AudioSourceCard(
                audioSource = audioSource,
                onSelectSource = { appPreferences.setAudioSource(it) }
            )

            // Section 3: Subtitle Style Studio & Live Preview
            SubtitleStyleStudioCard(
                style = currentStyle,
                onStyleChange = { appPreferences.updateSubtitleStyle(it) }
            )

            // Section 4: Engine Selection (Local ML Kit vs Cloud)
            EngineSelectionCard(
                engineType = engineType,
                appPreferences = appPreferences,
                onSelectEngine = { appPreferences.setEngineType(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))
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
                // Source Language Selector
                LanguageDropdown(
                    label = "输入语言",
                    selected = sourceLang,
                    onSelect = onSourceChange,
                    modifier = Modifier.weight(1f)
                )

                // Swap Button
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

                // Target Language Selector
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
                // Internal system audio
                AudioSourceOption(
                    title = "手机系统内录",
                    desc = "刷剧/生肉/会议无需外放",
                    icon = Icons.Default.VolumeUp,
                    isSelected = audioSource == AudioSourceType.SYSTEM_INTERNAL,
                    onClick = { onSelectSource(AudioSourceType.SYSTEM_INTERNAL) },
                    modifier = Modifier.weight(1f)
                )

                // Microphone
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
                    text = "字幕样式定制与预览",
                    color = Slate400,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "实时所见即所得",
                    color = Indigo500,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Live Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                        )
                    )
                    .padding(16.dp)
            ) {
                Surface(
                    color = Color(style.backgroundColor),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (style.showOriginalText) {
                            Text(
                                text = "Welcome to the real-time simultaneous translation app.",
                                color = Color(style.originalTextColor),
                                fontSize = style.originalTextSizeSp.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            text = "欢迎使用实时同传翻译字幕应用！",
                            color = Color(style.textColor),
                            fontSize = style.textSizeSp.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle Font Color Palette
            Text(text = "字幕字体颜色", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                            .size(36.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

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
                colors = SliderDefaults.colors(
                    thumbColor = Indigo500,
                    activeTrackColor = Indigo500
                )
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
                colors = SliderDefaults.colors(
                    thumbColor = Indigo500,
                    activeTrackColor = Indigo500
                )
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

            // 1. Google ML Kit (Local Free)
            EngineOptionRow(
                title = "Google ML Kit (推荐·本地离线)",
                badge = "100%免费·0延迟",
                description = "端侧神经网络，免配置API Key，纯本地运行保护隐私，支持中/英/日互译。",
                isSelected = engineType == TranslationEngineType.LOCAL_ML_KIT,
                onClick = { onSelectEngine(TranslationEngineType.LOCAL_ML_KIT) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. DeepL API
            EngineOptionRow(
                title = "DeepL API (高精度云端)",
                badge = "免费50万字/月",
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

            // 3. Cloud LLM
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
