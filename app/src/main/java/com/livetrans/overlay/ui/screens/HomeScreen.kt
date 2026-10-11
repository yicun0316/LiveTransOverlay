package com.livetrans.overlay.ui.screens

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetrans.overlay.data.*
import com.livetrans.overlay.engine.CloudTranslationEngine
import com.livetrans.overlay.engine.LocalMlKitTranslationEngine
import com.livetrans.overlay.engine.TranslationEngineManager
import com.livetrans.overlay.service.LiveSubtitleService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    // ML Kit offline model download states
    var mlKitEnDownloaded by remember { mutableStateOf(false) }
    var mlKitZhDownloaded by remember { mutableStateOf(false) }
    var mlKitJaDownloaded by remember { mutableStateOf(false) }
    var mlKitDownloadingLang by remember { mutableStateOf<Language?>(null) }
    var mlKitStatusHint by remember { mutableStateOf("") }

    // Check ML Kit model statuses on launch
    LaunchedEffect(Unit) {
        val mlEngine = LocalMlKitTranslationEngine()
        mlKitEnDownloaded = mlEngine.isModelDownloaded(Language.ENGLISH)
        mlKitZhDownloaded = mlEngine.isModelDownloaded(Language.CHINESE)
        mlKitJaDownloaded = mlEngine.isModelDownloaded(Language.JAPANESE)
    }

    // High-contrast color tokens based on Light / Dark mode
    val bgColor = if (isLightTheme) Color(0xFFFFFFFF) else Color(0xFF0F172A)
    val cardBg = if (isLightTheme) Color(0xFFF8FAFC) else Color(0xFF1E293B)
    val cardBorder = if (isLightTheme) Color(0xFFE2E8F0) else Color(0xFF334155)
    val textPrimary = if (isLightTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val textSecondary = if (isLightTheme) Color(0xFF475569) else Color(0xFF94A3B8)
    val brandPrimary = Color(0xFF2563EB) // Clean royal blue
    val inputContainerColor = if (isLightTheme) Color(0xFFFFFFFF) else Color(0xFF0F172A)
    val inputBorderColor = if (isLightTheme) Color(0xFFCBD5E1) else Color(0xFF475569)

    // Reusable TextFieldColors ensuring 100% visible text in pure white background
    val highContrastTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textPrimary,
        unfocusedTextColor = textPrimary,
        cursorColor = brandPrimary,
        focusedBorderColor = brandPrimary,
        unfocusedBorderColor = inputBorderColor,
        focusedContainerColor = inputContainerColor,
        unfocusedContainerColor = inputContainerColor,
        focusedPlaceholderColor = textSecondary,
        unfocusedPlaceholderColor = textSecondary
    )

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
                            text = "极简同传控制台 · 离线/云端双引擎",
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

            // 0. Audio Source & Silent Subtitle Mode Card
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

            // 1. Language Pair Direction
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "语种互译方向", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LanguageDropdown(
                            label = "源语言 (发音/原文)",
                            selected = sourceLang,
                            isLightTheme = isLightTheme,
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
                            Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = "交换语言", tint = brandPrimary)
                        }

                        LanguageDropdown(
                            label = "目标语言 (同传译文)",
                            selected = targetLang,
                            isLightTheme = isLightTheme,
                            onSelect = { appPreferences.setTargetLanguage(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2. Translation Test Workbench (实测测试台，防闪退 + 纯白高对比)
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
                            text = "实时翻译测试台",
                            color = textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "当前引擎: ${engineType.displayName}",
                            color = brandPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "输入测试文字立即验证翻译质量，无需启动悬浮窗也可直观预览结果",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Quick Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                manualTestText = "Hello, welcome to live translation."
                            },
                            label = { Text("🇬🇧 英语测试", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                manualTestText = "こんにちは、翻訳テストです。"
                            },
                            label = { Text("🇯🇵 日语测试", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                manualTestText = "你好，欢迎体验实时同传字幕。"
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
                        colors = highContrastTextFieldColors,
                        placeholder = { Text("输入测试语句…", color = textSecondary) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. 立即测试翻译 (防闪退，直接在应用内展示结果)
                        Button(
                            onClick = {
                                val text = manualTestText.trim()
                                if (text.isEmpty()) {
                                    Toast.makeText(context, "请输入测试语句", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isInAppTranslating = true
                                inAppTestOutput = "⏳ 正在请求【${engineType.displayName}】翻译中…"
                                coroutineScope.launch {
                                    try {
                                        val mgr = TranslationEngineManager(appPreferences)
                                        val res = mgr.translate(text, sourceLang, targetLang)
                                        inAppTestOutput = if (res.isSuccess) {
                                            "✔ 译文: ${res.getOrNull()}"
                                        } else {
                                            "❌ 失败: ${res.exceptionOrNull()?.message}"
                                        }
                                    } catch (e: Throwable) {
                                        inAppTestOutput = "❌ 异常: ${e.message ?: "未知异常"}"
                                    } finally {
                                        isInAppTranslating = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = brandPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "测试翻译", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // 2. 发送至悬浮窗
                        OutlinedButton(
                            onClick = {
                                val text = manualTestText.trim()
                                if (text.isEmpty()) return@OutlinedButton
                                if (isServiceRunning) {
                                    LiveSubtitleService.sendTestSpeech(context, text)
                                    Toast.makeText(context, "已发送至悬浮窗", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "请先点击顶部【启动悬浮同传字幕】", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, brandPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = brandPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "同步至悬浮窗", color = brandPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    AnimatedVisibility(visible = inAppTestOutput.isNotEmpty() || isInAppTranslating) {
                        Surface(
                            color = if (isLightTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            Text(
                                text = if (isInAppTranslating) "⏳ 正在极速同传翻译中…" else inAppTestOutput,
                                color = if (inAppTestOutput.startsWith("✔")) Color(0xFF059669) else textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // 3. 核心翻译引擎选择 (新增多款高质量离线模型 + 离线管理)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "翻译引擎架构选择",
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "支持 100% 本地纯离线模型，无需配置 Key、断网无网也能高质量同传",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Engine Mode Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = engineType == TranslationEngineType.EMBEDDED_OFFLINE,
                                onClick = { appPreferences.setEngineType(TranslationEngineType.EMBEDDED_OFFLINE) },
                                modifier = Modifier.weight(1f),
                                label = { Text("📱 内置纯离线引擎 (免下载·零网络)", fontSize = 11.sp) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = engineType == TranslationEngineType.LOCAL_ML_KIT,
                                onClick = { appPreferences.setEngineType(TranslationEngineType.LOCAL_ML_KIT) },
                                modifier = Modifier.weight(1f),
                                label = { Text("🧠 Google ML Kit (端侧神经网络模型)", fontSize = 11.sp) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = engineType == TranslationEngineType.FREE_WEB,
                                onClick = { appPreferences.setEngineType(TranslationEngineType.FREE_WEB) },
                                modifier = Modifier.weight(1f),
                                label = { Text("🌐 免费网页引擎 (免Key·即开即用)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = engineType == TranslationEngineType.CLOUD_LLM,
                                onClick = { appPreferences.setEngineType(TranslationEngineType.CLOUD_LLM) },
                                modifier = Modifier.weight(1f),
                                label = { Text("⚡ 大模型 API (DeepSeek/Gemini)", fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Detail section based on active engine
                    when (engineType) {
                        TranslationEngineType.EMBEDDED_OFFLINE -> {
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "100% 纯本地离线引擎已激活", color = Color(0xFF059669), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "• 零网络请求、零外部下载依赖，无 SIM 卡/飞行模式均可秒级翻译。\n• 内建影视字幕高频词典、日常口语对话、流式双向短语重组模型。\n• 支持 英语 ⇄ 中文、日语 ⇄ 中文、英语 ⇄ 日语 全向高质量互译。",
                                        color = textSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        TranslationEngineType.LOCAL_ML_KIT -> {
                            Surface(
                                color = if (isLightTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = "Google ML Kit 端侧神经网络离线包管理", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "单语言包约 30MB，一次下载后永久离线运行，享受端侧神经网络极速推理", color = textSecondary, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Model pack status rows
                                    ModelPackStatusRow(
                                        langName = "🇬🇧 英语模型 (EN)",
                                        isDownloaded = mlKitEnDownloaded,
                                        isDownloading = mlKitDownloadingLang == Language.ENGLISH,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        onDownloadClick = {
                                            mlKitDownloadingLang = Language.ENGLISH
                                            coroutineScope.launch {
                                                val ml = LocalMlKitTranslationEngine()
                                                val res = ml.downloadModel(Language.ENGLISH)
                                                mlKitEnDownloaded = ml.isModelDownloaded(Language.ENGLISH)
                                                mlKitDownloadingLang = null
                                                mlKitStatusHint = if (res.isSuccess) "英语模型下载就绪！" else "下载失败: ${res.exceptionOrNull()?.message}"
                                            }
                                        }
                                    )

                                    ModelPackStatusRow(
                                        langName = "🇨🇳 简体中文模型 (ZH)",
                                        isDownloaded = mlKitZhDownloaded,
                                        isDownloading = mlKitDownloadingLang == Language.CHINESE,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        onDownloadClick = {
                                            mlKitDownloadingLang = Language.CHINESE
                                            coroutineScope.launch {
                                                val ml = LocalMlKitTranslationEngine()
                                                val res = ml.downloadModel(Language.CHINESE)
                                                mlKitZhDownloaded = ml.isModelDownloaded(Language.CHINESE)
                                                mlKitDownloadingLang = null
                                                mlKitStatusHint = if (res.isSuccess) "中文模型下载就绪！" else "下载失败: ${res.exceptionOrNull()?.message}"
                                            }
                                        }
                                    )

                                    ModelPackStatusRow(
                                        langName = "🇯🇵 日语模型 (JA)",
                                        isDownloaded = mlKitJaDownloaded,
                                        isDownloading = mlKitDownloadingLang == Language.JAPANESE,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        onDownloadClick = {
                                            mlKitDownloadingLang = Language.JAPANESE
                                            coroutineScope.launch {
                                                val ml = LocalMlKitTranslationEngine()
                                                val res = ml.downloadModel(Language.JAPANESE)
                                                mlKitJaDownloaded = ml.isModelDownloaded(Language.JAPANESE)
                                                mlKitDownloadingLang = null
                                                mlKitStatusHint = if (res.isSuccess) "日语模型下载就绪！" else "下载失败: ${res.exceptionOrNull()?.message}"
                                            }
                                        }
                                    )

                                    if (mlKitStatusHint.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = mlKitStatusHint, color = brandPrimary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        TranslationEngineType.FREE_WEB -> {
                            Surface(
                                color = Color(0xFF2563EB).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = "免费网页直连引擎", color = brandPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "• 无需 API 密钥，无需提前下载几十兆离线包，开箱即用。\n• 适用于设备有 Wi-Fi 或移动数据环境，若断网将自动无缝降级至内置离线引擎。",
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        TranslationEngineType.CLOUD_LLM -> {
                            Column(modifier = Modifier.padding(top = 4.dp)) {
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
                                        label = { Text("⚡ 硅基流动", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = baseUrlInput.contains("googleapis"),
                                        onClick = {
                                            baseUrlInput = "https://generativelanguage.googleapis.com/v1beta/openai"
                                            modelInput = "gemini-3.5-flash"
                                        },
                                        label = { Text("🌐 Gemini", fontSize = 10.sp) }
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

                                Text(text = "API Key (密钥):", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = highContrastTextFieldColors,
                                    placeholder = { Text("粘贴您的 API Key...", color = textSecondary) },
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(text = "Base URL (接口地址):", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = baseUrlInput,
                                    onValueChange = { baseUrlInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = highContrastTextFieldColors,
                                    placeholder = { Text("https://api.deepseek.com/v1", color = textSecondary) },
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(text = "Model (模型名称):", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = modelInput,
                                    onValueChange = { modelInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = highContrastTextFieldColors,
                                    placeholder = { Text("deepseek-chat", color = textSecondary) },
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Buttons: Save & Test API (Crash-Proof)
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
                                            Toast.makeText(context, "API 配置已保存！", Toast.LENGTH_SHORT).show()
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

                                            if (key.isEmpty()) {
                                                apiTestResult = "⚠️ 请先填写 API Key"
                                                return@Button
                                            }

                                            isTestingApi = true
                                            apiTestResult = "正在连接并测试 API..."
                                            coroutineScope.launch {
                                                try {
                                                    val cloud = CloudTranslationEngine(appPreferences)
                                                    val res = cloud.testConnection(key, url, mod)
                                                    apiTestResult = if (res.isSuccess) {
                                                        "✔ API 正常可用！${res.getOrNull()}"
                                                    } else {
                                                        "❌ 测试失败: ${res.exceptionOrNull()?.message}"
                                                    }
                                                } catch (e: Throwable) {
                                                    apiTestResult = "❌ 异常: ${e.message ?: "未知异常"}"
                                                } finally {
                                                    isTestingApi = false
                                                }
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
                                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
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
                        else -> {}
                    }
                }
            }

            // 4. Subtitle Box Appearance & Gesture Tips
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "悬浮窗外观与操控手势",
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Gesture Hint Alert Banner
                    Surface(
                        color = Color(0xFF3B82F6).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.TouchApp, contentDescription = null, tint = brandPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "👆 单指按住悬浮窗任意位置可随意拖动移动\n✌️ 双指捏合/外扩可自由无级缩放窗口大小与字幕",
                                color = textPrimary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

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

                    Text(text = "字幕字体颜色 (10款预设调色盘):", color = textSecondary, fontSize = 11.sp)
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
                        valueRange = 14f..28f,
                        steps = 7
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun ModelPackStatusRow(
    langName: String,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    onDownloadClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = langName, color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (isDownloaded) "✅ 已就绪 (离线可用)" else "📥 未下载 (点击下载 ~30MB)",
                color = if (isDownloaded) Color(0xFF059669) else textSecondary,
                fontSize = 10.sp
            )
        }

        if (isDownloaded) {
            Text(text = "已就绪", color = Color(0xFF059669), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        } else {
            Button(
                onClick = onDownloadClick,
                enabled = !isDownloading,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(text = if (isDownloading) "正在下载…" else "下载模型", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun LanguageDropdown(
    label: String,
    selected: Language,
    isLightTheme: Boolean,
    onSelect: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val containerBg = if (isLightTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textPrimary = if (isLightTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val textSecondary = if (isLightTheme) Color(0xFF475569) else Color(0xFF94A3B8)

    Column(modifier = modifier) {
        Text(text = label, color = textSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            color = containerBg,
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
                    color = textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = textSecondary
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
