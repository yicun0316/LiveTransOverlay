package com.livetrans.overlay.ui.screens

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetrans.overlay.data.*
import com.livetrans.overlay.engine.CloudTranslationEngine
import com.livetrans.overlay.service.LiveSubtitleService
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

    val isLightTheme by appPreferences.isLightThemeFlow.collectAsState()
    val currentStyle by appPreferences.styleFlow.collectAsState()
    val sourceLang by appPreferences.sourceLangFlow.collectAsState()
    val targetLang by appPreferences.targetLangFlow.collectAsState()
    val audioSource by appPreferences.audioSourceFlow.collectAsState()
    val engineType by appPreferences.engineTypeFlow.collectAsState()

    // API input states initialized from preferences
    var apiKeyInput by remember { mutableStateOf(appPreferences.llmApiKey) }
    var baseUrlInput by remember { mutableStateOf(appPreferences.llmBaseUrl) }
    var modelInput by remember { mutableStateOf(appPreferences.llmModelName) }

    var apiTestResult by remember { mutableStateOf("") }
    var isTestingApi by remember { mutableStateOf(false) }

    var manualTestText by remember { mutableStateOf("Hello! This is a translation test.") }
    var inAppTestOutput by remember { mutableStateOf("") }
    var isInAppTranslating by remember { mutableStateOf(false) }

    // Color theme tokens based on Light / Dark mode
    val bgColor = if (isLightTheme) Color(0xFFFFFFFF) else Color(0xFF0F172A)
    val cardBg = if (isLightTheme) Color(0xFFF8FAFC) else Color(0xFF1E293B)
    val cardBorder = if (isLightTheme) Color(0xFFE2E8F0) else Color(0xFF334155)
    val textPrimary = if (isLightTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val textSecondary = if (isLightTheme) Color(0xFF64748B) else Color(0xFF94A3B8)
    val brandPrimary = Color(0xFF2563EB) // Clean royal blue

    Scaffold(
        containerColor = bgColor,
        topBar = {
            Surface(
                color = bgColor,
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "实时同传字幕",
                            color = textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "极简同传控制台",
                            color = textSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Light / Dark Theme Toggle
                        IconButton(
                            onClick = { appPreferences.setAppLightTheme(!isLightTheme) },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(cardBg)
                        ) {
                            Icon(
                                imageVector = if (isLightTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme",
                                tint = if (isLightTheme) Color(0xFFD97706) else Color(0xFF93C5FD),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Status Badge
                        Surface(
                            color = if (isServiceRunning) Color(0xFF10B981).copy(alpha = 0.15f) else cardBg,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isServiceRunning) Color(0xFF10B981) else cardBorder
                            )
                        ) {
                            Text(
                                text = if (isServiceRunning) "运行中" else "已就绪",
                                color = if (isServiceRunning) Color(0xFF059669) else textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Master Action Button
            Button(
                onClick = {
                    if (isServiceRunning) onStopService() else onStartService()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) Color(0xFFEF4444) else brandPrimary
                )
            ) {
                Icon(
                    imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isServiceRunning) "停止悬浮窗字幕" else "启动悬浮同传字幕",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 0. Audio Source & Silent Subtitle Mode Card (无声字幕 / 内录模式)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "声音来源与无声字幕模式",
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "支持手机静音或戴耳机时提取内部声音，外放无声也能照样出字幕",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = audioSource == AudioSourceType.MIC,
                            onClick = { appPreferences.setAudioSource(AudioSourceType.MIC) },
                            label = { Text("🎙️ 麦克风拾音 (外放/对话)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = audioSource == AudioSourceType.SYSTEM_INTERNAL,
                            onClick = { appPreferences.setAudioSource(AudioSourceType.SYSTEM_INTERNAL) },
                            label = { Text("🔇 无声字幕 (系统内录/静音看剧)", fontSize = 11.sp) }
                        )
                    }
                }
            }

            // 1. Translation Test Workbench (实测测试台)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "实时翻译测试",
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Quick Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                val s = "Hello, welcome to live translation."
                                manualTestText = s
                                if (!isServiceRunning) onStartService()
                                LiveSubtitleService.sendTestSpeech(context, s)
                            },
                            label = { Text("🇬🇧 英语测试", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                val s = "こんにちは、翻訳テストです。"
                                manualTestText = s
                                if (!isServiceRunning) onStartService()
                                LiveSubtitleService.sendTestSpeech(context, s)
                            },
                            label = { Text("🇯🇵 日语测试", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                val s = "你好，欢迎体验实时同传字幕。"
                                manualTestText = s
                                if (!isServiceRunning) onStartService()
                                LiveSubtitleService.sendTestSpeech(context, s)
                            },
                            label = { Text("🇨🇳 中文测试", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = manualTestText,
                        onValueChange = { manualTestText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("输入测试语句…", color = textSecondary) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (!isServiceRunning) onStartService()
                            LiveSubtitleService.sendTestSpeech(context, manualTestText)

                            // Also run in-app test
                            isInAppTranslating = true
                            coroutineScope.launch {
                                val mgr = com.livetrans.overlay.engine.TranslationEngineManager(appPreferences)
                                val res = mgr.translate(manualTestText, sourceLang, targetLang)
                                inAppTestOutput = if (res.isSuccess) {
                                    "✔ 译文: ${res.getOrNull()}"
                                } else {
                                    "❌ 失败: ${res.exceptionOrNull()?.message}"
                                }
                                isInAppTranslating = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = brandPrimary)
                    ) {
                        Text(text = "发送至悬浮窗并翻译", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    AnimatedVisibility(visible = inAppTestOutput.isNotEmpty() || isInAppTranslating) {
                        Surface(
                            color = if (isLightTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                text = if (isInAppTranslating) "⏳ 正在请求翻译中…" else inAppTestOutput,
                                color = textPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // 2. API Settings Card (含明确的保存与测试按钮)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "翻译引擎与 API 配置",
                            color = textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (engineType == TranslationEngineType.CLOUD_LLM) "已启用 API" else "网页模式",
                            color = brandPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(text = "快速预设 (一键填入服务与模型):", color = textSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = baseUrlInput.contains("siliconflow"),
                            onClick = {
                                baseUrlInput = "https://api.siliconflow.cn/v1"
                                modelInput = "deepseek-ai/DeepSeek-V4-Flash"
                            },
                            label = { Text("⚡ 硅基限免 (DeepSeek-Flash)", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = baseUrlInput.contains("googleapis"),
                            onClick = {
                                baseUrlInput = "https://generativelanguage.googleapis.com/v1beta/openai"
                                modelInput = "gemini-3.5-flash"
                            },
                            label = { Text("🌐 Google Gemini (官方免费)", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = baseUrlInput.contains("deepseek"),
                            onClick = {
                                baseUrlInput = "https://api.deepseek.com/v1"
                                modelInput = "deepseek-chat"
                            },
                            label = { Text("🤖 DeepSeek", fontSize = 10.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "API Key (密钥):", color = textSecondary, fontSize = 11.sp)
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("粘贴您的 API Key...", color = textSecondary) },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "Base URL (接口地址):", color = textSecondary, fontSize = 11.sp)
                    OutlinedTextField(
                        value = baseUrlInput,
                        onValueChange = { baseUrlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://api.deepseek.com/v1", color = textSecondary) },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "Model (模型名称):", color = textSecondary, fontSize = 11.sp)
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("deepseek-chat", color = textSecondary) },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons: Save & Test API
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                appPreferences.llmApiKey = apiKeyInput.trim()
                                appPreferences.llmBaseUrl = baseUrlInput.trim()
                                appPreferences.llmModelName = modelInput.trim()
                                appPreferences.setEngineType(TranslationEngineType.CLOUD_LLM)
                                Toast.makeText(context, "API 配置已保存，已启用大模型引擎！", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "保存配置", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val key = apiKeyInput.trim()
                                val url = baseUrlInput.trim()
                                val mod = modelInput.trim()

                                appPreferences.llmApiKey = key
                                appPreferences.llmBaseUrl = url
                                appPreferences.llmModelName = mod
                                appPreferences.setEngineType(TranslationEngineType.CLOUD_LLM)

                                isTestingApi = true
                                apiTestResult = "正在连接并测试 API..."
                                coroutineScope.launch {
                                    val cloud = CloudTranslationEngine(appPreferences)
                                    val res = cloud.testConnection(key, url, mod)
                                    apiTestResult = if (res.isSuccess) {
                                        "✔ API 正常可用！${res.getOrNull()}"
                                    } else {
                                        "❌ 测试失败: ${res.exceptionOrNull()?.message}"
                                    }
                                    isTestingApi = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = brandPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "测试连接", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    AnimatedVisibility(visible = apiTestResult.isNotEmpty() || isTestingApi) {
                        Surface(
                            color = if (isLightTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            Text(
                                text = apiTestResult,
                                color = if (apiTestResult.startsWith("✔")) Color(0xFF059669) else Color(0xFFDC2626),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // 3. Subtitle Box Appearance (纯白背景 / 字体颜色 / 字号)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "悬浮窗外观与颜色",
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "悬浮窗背景风格:", color = textSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SubtitleStyle.PRESET_BG_STYLES.forEach { (bgInt, label) ->
                            val isSelected = currentStyle.backgroundColor == bgInt
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    // If pure white selected, auto set text color to black if was white
                                    val newFg = if (bgInt == AndroidColor.WHITE && currentStyle.textColor == AndroidColor.WHITE) {
                                        AndroidColor.BLACK
                                    } else currentStyle.textColor
                                    appPreferences.updateSubtitleStyle(currentStyle.copy(backgroundColor = bgInt, textColor = newFg))
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "字幕字体颜色 (10款预设 + 自定义色值):", color = textSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SubtitleStyle.PRESET_COLORS.forEach { colorInt ->
                            val composeColor = Color(colorInt)
                            val isSelected = currentStyle.textColor == colorInt
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(composeColor)
                                    .clickable { appPreferences.updateSubtitleStyle(currentStyle.copy(textColor = colorInt)) }
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) brandPrimary else cardBorder,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    var customHexInput by remember { mutableStateOf(String.format("#%06X", 0xFFFFFF and currentStyle.textColor)) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customHexInput,
                            onValueChange = { customHexInput = it },
                            placeholder = { Text("#000000 自定义色", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val parsed = AndroidColor.parseColor(customHexInput.trim())
                                    appPreferences.updateSubtitleStyle(currentStyle.copy(textColor = parsed))
                                    Toast.makeText(context, "颜色已更新", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(context, "色值格式错误，请如 #FF0055", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = brandPrimary)
                        ) {
                            Text("应用颜色", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "字号大小", color = textSecondary, fontSize = 12.sp)
                        Text(text = "${currentStyle.textSizeSp.toInt()} sp", color = brandPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = currentStyle.textSizeSp,
                        onValueChange = { appPreferences.updateSubtitleStyle(currentStyle.copy(textSizeSp = it)) },
                        valueRange = 14f..26f,
                        steps = 5
                    )
                }
            }

            // 4. Language Selection
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "语种选择", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LanguageDropdown(
                            label = "源语言 (发音)",
                            selected = sourceLang,
                            onSelect = { appPreferences.setSourceLanguage(it) },
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                val prevSrc = sourceLang
                                appPreferences.setSourceLanguage(targetLang)
                                appPreferences.setTargetLanguage(prevSrc)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, tint = brandPrimary)
                        }

                        LanguageDropdown(
                            label = "目标语言 (译文)",
                            selected = targetLang,
                            onSelect = { appPreferences.setTargetLanguage(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
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
        Text(text = label, color = Color(0xFF64748B), fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            color = Color(0xFFF1F5F9),
            shape = RoundedCornerShape(10.dp),
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
                    color = Color(0xFF0F172A),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color(0xFF64748B)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Language.values().forEach { lang ->
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
