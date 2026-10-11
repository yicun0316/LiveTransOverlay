package com.livetrans.overlay.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.livetrans.overlay.LiveTransApplication
import com.livetrans.overlay.MainActivity
import com.livetrans.overlay.R
import com.livetrans.overlay.data.AudioSourceType
import com.livetrans.overlay.data.Language
import com.livetrans.overlay.data.SubtitleStyle
import com.livetrans.overlay.engine.SpeechRecognitionManager
import com.livetrans.overlay.engine.TranslationEngineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveSubtitleService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private lateinit var translationEngineManager: TranslationEngineManager
    private var speechRecognitionManager: SpeechRecognitionManager? = null

    private var isPaused = false
    private var isTouchLocked = false
    private var isFolded = false
    private var lastTranslateJob: Job? = null

    // UI elements in overlay
    private var tvOriginal: TextView? = null
    private var tvTranslated: TextView? = null
    private var tvLanguageBadge: TextView? = null
    private var tvStatusHint: TextView? = null
    private var btnFold: ImageView? = null
    private var btnPause: ImageView? = null
    private var btnLock: ImageView? = null
    private var btnClose: ImageView? = null
    private var ivResizeHandle: ImageView? = null
    private var rootContainer: View? = null

    // Broadcast receiver for test speech injection
    private val testReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_TEST_SPEECH) {
                val sampleText = intent.getStringExtra(EXTRA_TEXT) ?: return
                onSpeechResult(sampleText)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val app = application as LiveTransApplication
        translationEngineManager = TranslationEngineManager(app.appPreferences)
        speechRecognitionManager = SpeechRecognitionManager(
            context = this,
            appPreferences = app.appPreferences,
            scope = serviceScope,
            onSpeechTextRecognized = ::onSpeechResult,
            onAudioDbLevel = { db ->
                if (db > 34f && !isPaused) {
                    val bars = when {
                        db > 62f -> " ▂▃▅▆▇"
                        db > 48f -> " ▂▃▅"
                        else -> " ▂"
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (tvStatusHint?.text?.contains("正在极速") != true) {
                            tvStatusHint?.text = "🎤 声音捕获中: [$bars ${db.toInt()}dB]"
                        }
                    }
                }
            },
            onStatusHintChanged = { hint ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    tvStatusHint?.text = hint
                }
            }
        )

        createNotificationChannel()
        startServiceAsForeground()

        createFloatingOverlay()
        observeSettings()
        setupClipboardListener()
        startRecognitionPipeline()

        // Register test injection receiver
        val filter = IntentFilter(ACTION_TEST_SPEECH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(testReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(testReceiver, filter)
        }
    }

    private var projectionResultCode: Int = 0
    private var projectionData: Intent? = null
    private var lastCopiedText: String = ""
    private var clipListener: android.content.ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val code = it.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, 0)
            val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableExtra(EXTRA_PROJECTION_DATA)
            }
            if (code != 0 && data != null) {
                projectionResultCode = code
                projectionData = data
                // Re-launch capture with system internal audio
                startRecognitionPipeline()
            }
        }
        return START_STICKY
    }

    private fun setupClipboardListener() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
            clipListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                    if (text.isNotEmpty() && text != lastCopiedText && text.length > 1) {
                        lastCopiedText = text
                        tvStatusHint?.text = "📋 捕获到剪贴板文本"
                        onSpeechResult(text)
                    }
                }
            }
            clipboard.addPrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup clipboard listener", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "实时同传字幕常驻通知"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startServiceAsForeground() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createFloatingOverlay() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.view_floating_subtitle, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val app = application as LiveTransApplication
        val initialStyle = app.appPreferences.loadSubtitleStyle()
        val density = resources.displayMetrics.density

        val initialWidth = (initialStyle.windowWidthDp * density).toInt()
        val initialHeight = (initialStyle.windowHeightDp * density).toInt()

        layoutParams = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - initialWidth) / 2
            y = (resources.displayMetrics.heightPixels * 0.18f).toInt()
        }

        overlayView?.let { view ->
            rootContainer = view.findViewById(R.id.rootOverlayContainer)
            tvOriginal = view.findViewById(R.id.tvOriginalText)
            tvTranslated = view.findViewById(R.id.tvTranslatedText)
            tvLanguageBadge = view.findViewById(R.id.tvLanguageBadge)
            tvStatusHint = view.findViewById(R.id.tvStatusHint)
            btnFold = view.findViewById(R.id.btnFold)
            btnPause = view.findViewById(R.id.btnPause)
            btnLock = view.findViewById(R.id.btnLock)
            btnClose = view.findViewById(R.id.btnClose)
            ivResizeHandle = view.findViewById(R.id.ivResizeHandle)

            setupDragAndPinchGestures(view)
            setupResizeHandleEvents(view)
            setupButtonControls()

            try {
                windowManager.addView(view, layoutParams)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding overlay view to WindowManager", e)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndPinchGestures(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        var isPinching = false
        var initialFingerDistance = 0f
        var initialWidth = 0
        var initialHeight = 0

        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val minWidthPx = (200 * density).toInt()
        val maxWidthPx = (screenWidth - (20 * density).toInt()).coerceAtLeast(minWidthPx)
        val minHeightPx = (75 * density).toInt()
        val maxHeightPx = (screenHeight * 0.75f).toInt()

        fun getDistance(event: MotionEvent): Float {
            if (event.pointerCount < 2) return 0f
            val dx = event.getX(0) - event.getX(1)
            val dy = event.getY(0) - event.getY(1)
            return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }

        fun saveDimensions() {
            val app = application as? LiveTransApplication ?: return
            val currentStyle = app.appPreferences.loadSubtitleStyle()
            val newWidthDp = (layoutParams.width / density).toInt()
            val newHeightDp = (layoutParams.height / density).toInt()
            app.appPreferences.updateSubtitleStyle(
                currentStyle.copy(windowWidthDp = newWidthDp, windowHeightDp = newHeightDp)
            )
        }

        val gestureListener = View.OnTouchListener { _, event ->
            if (isTouchLocked) return@OnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isPinching = false
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2 && !isFolded) {
                        isPinching = true
                        initialFingerDistance = getDistance(event)
                        initialWidth = layoutParams.width
                        initialHeight = layoutParams.height
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isPinching && event.pointerCount >= 2 && !isFolded) {
                        // 双指缩放 (Two-finger Pinch-to-Zoom)
                        val currentDistance = getDistance(event)
                        if (initialFingerDistance > 30f && currentDistance > 30f) {
                            val scale = currentDistance / initialFingerDistance
                            val targetWidth = (initialWidth * scale).toInt().coerceIn(minWidthPx, maxWidthPx)
                            val targetHeight = (initialHeight * scale).toInt().coerceIn(minHeightPx, maxHeightPx)

                            layoutParams.width = targetWidth
                            layoutParams.height = targetHeight
                            try {
                                windowManager.updateViewLayout(view, layoutParams)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating layout in pinch", e)
                            }
                        }
                    } else if (!isPinching && event.pointerCount == 1) {
                        // 单指移动 (Single-finger Drag)
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        layoutParams.x = initialX + deltaX
                        layoutParams.y = initialY + deltaY
                        try {
                            windowManager.updateViewLayout(view, layoutParams)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating layout in drag", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (isPinching) {
                        isPinching = false
                        saveDimensions()
                        val remainingIdx = if (event.actionIndex == 0) 1 else 0
                        if (remainingIdx < event.pointerCount) {
                            initialX = layoutParams.x
                            initialY = layoutParams.y
                            try {
                                initialTouchX = event.getRawX(remainingIdx)
                                initialTouchY = event.getRawY(remainingIdx)
                            } catch (_: Exception) {
                                initialTouchX = event.rawX
                                initialTouchY = event.rawY
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isPinching) {
                        isPinching = false
                        saveDimensions()
                    }
                    true
                }
                else -> false
            }
        }

        rootContainer?.setOnTouchListener(gestureListener)
        view.findViewById<View>(R.id.headerControlBar)?.setOnTouchListener(gestureListener)
        view.findViewById<View>(R.id.contentContainer)?.setOnTouchListener(gestureListener)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupResizeHandleEvents(view: View) {
        var initialWidth = 0
        var initialHeight = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        val density = resources.displayMetrics.density
        val minWidthPx = (240 * density).toInt()
        val minHeightPx = (100 * density).toInt()

        ivResizeHandle?.setOnTouchListener { _, event ->
            if (isTouchLocked || isFolded) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialWidth = layoutParams.width
                    initialHeight = layoutParams.height
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    layoutParams.width = (initialWidth + deltaX).coerceAtLeast(minWidthPx)
                    layoutParams.height = (initialHeight + deltaY).coerceAtLeast(minHeightPx)
                    windowManager.updateViewLayout(view, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Persist new dimensions to preferences
                    val app = application as LiveTransApplication
                    val currentStyle = app.appPreferences.loadSubtitleStyle()
                    val newWidthDp = (layoutParams.width / density).toInt()
                    val newHeightDp = (layoutParams.height / density).toInt()
                    app.appPreferences.updateSubtitleStyle(
                        currentStyle.copy(windowWidthDp = newWidthDp, windowHeightDp = newHeightDp)
                    )
                    true
                }
                else -> false
            }
        }
    }

    private fun setupButtonControls() {
        tvLanguageBadge?.setOnClickListener {
            val app = application as LiveTransApplication
            val curSrc = app.appPreferences.loadSourceLanguage()
            val curTgt = app.appPreferences.loadTargetLanguage()
            app.appPreferences.setSourceLanguage(curTgt)
            app.appPreferences.setTargetLanguage(curSrc)
            tvStatusHint?.text = "🔄 已反转语种方向: ${curTgt.shortCode} ➔ ${curSrc.shortCode}"
        }

        btnFold?.setOnClickListener {
            toggleFoldState()
        }

        btnPause?.setOnClickListener {
            isPaused = !isPaused
            if (isPaused) {
                btnPause?.setImageResource(android.R.drawable.ic_media_play)
                tvStatusHint?.text = "⏸️ 【已暂停同传监听】"
                speechRecognitionManager?.stop()
            } else {
                btnPause?.setImageResource(android.R.drawable.ic_media_pause)
                tvStatusHint?.text = "🎤 【已恢复实时同传监听】"
                startRecognitionPipeline()
            }
        }

        btnLock?.setOnClickListener {
            toggleTouchLock()
        }

        btnClose?.setOnClickListener {
            stopSelf()
        }
    }

    private fun toggleFoldState() {
        isFolded = !isFolded
        val density = resources.displayMetrics.density
        val app = application as LiveTransApplication
        val style = app.appPreferences.loadSubtitleStyle()

        if (isFolded) {
            btnFold?.setImageResource(R.drawable.ic_fold_down)
            tvOriginal?.visibility = View.GONE
            tvStatusHint?.visibility = View.GONE
            ivResizeHandle?.visibility = View.GONE
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            btnFold?.setImageResource(R.drawable.ic_fold_up)
            if (style.showOriginalText) tvOriginal?.visibility = View.VISIBLE
            tvStatusHint?.visibility = View.VISIBLE
            ivResizeHandle?.visibility = View.VISIBLE
            layoutParams.height = (style.windowHeightDp * density).toInt()
            layoutParams.width = (style.windowWidthDp * density).toInt()
        }
        overlayView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    private fun toggleTouchLock() {
        isTouchLocked = !isTouchLocked
        if (isTouchLocked) {
            btnLock?.setColorFilter(Color.parseColor("#EF4444"))
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            tvStatusHint?.text = "🔒 穿透锁定开启：手势已直达下方APP"
        } else {
            btnLock?.setColorFilter(Color.parseColor("#94A3B8"))
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            tvStatusHint?.text = "🔓 穿透已解锁：可自由拖动或拉伸调整"
        }
        overlayView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    private fun observeSettings() {
        val app = application as LiveTransApplication

        // Observe Subtitle Style Changes
        serviceScope.launch {
            app.appPreferences.styleFlow.collectLatest { style ->
                applySubtitleStyle(style)
            }
        }

        // Observe Language Pair Changes
        serviceScope.launch {
            app.appPreferences.sourceLangFlow.collectLatest { src ->
                val tgt = app.appPreferences.loadTargetLanguage()
                updateLanguageBadge(src, tgt)
                startRecognitionPipeline()
            }
        }

        // Observe Audio Source Changes
        serviceScope.launch {
            app.appPreferences.audioSourceFlow.collectLatest {
                startRecognitionPipeline()
            }
        }

        // Observe ASR Engine Changes
        serviceScope.launch {
            app.appPreferences.asrEngineTypeFlow.collectLatest {
                startRecognitionPipeline()
            }
        }
    }

    private fun applySubtitleStyle(style: SubtitleStyle) {
        val isWhiteBg = (style.backgroundColor == Color.WHITE || style.backgroundColor == 0xFFFFFFFF.toInt())

        // If background is pure white and text color is pure white, automatically use dark slate
        val effectiveTextColor = if (isWhiteBg && (style.textColor == Color.WHITE || style.textColor == 0xFFFFFFFF.toInt())) {
            Color.parseColor("#0F172A")
        } else {
            style.textColor
        }

        tvTranslated?.apply {
            setTextColor(effectiveTextColor)
            textSize = style.textSizeSp
            if (style.enableTextShadow) {
                val shadowColor = if (isWhiteBg) Color.parseColor("#33000000") else Color.BLACK
                setShadowLayer(4f, 2f, 2f, shadowColor)
            } else {
                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }
        }

        val effectiveOriginalColor = if (isWhiteBg && (style.originalTextColor == Color.WHITE || style.originalTextColor == 0xFFFFFFFF.toInt())) {
            Color.parseColor("#475569")
        } else {
            style.originalTextColor
        }

        tvOriginal?.apply {
            if (!isFolded) {
                visibility = if (style.showOriginalText) View.VISIBLE else View.GONE
            }
            setTextColor(effectiveOriginalColor)
            textSize = style.originalTextSizeSp
        }

        val iconTint = if (isWhiteBg) Color.parseColor("#475569") else Color.parseColor("#94A3B8")
        btnFold?.setColorFilter(iconTint)
        btnPause?.setColorFilter(iconTint)
        btnClose?.setColorFilter(iconTint)
        if (!isTouchLocked) btnLock?.setColorFilter(iconTint)
        tvLanguageBadge?.setTextColor(if (isWhiteBg) Color.parseColor("#1E293B") else Color.parseColor("#94A3B8"))
        tvStatusHint?.setTextColor(if (isWhiteBg) Color.parseColor("#64748B") else Color.parseColor("#94A3B8"))

        rootContainer?.let { root ->
            val bg = GradientDrawable().apply {
                setColor(style.backgroundColor)
                cornerRadius = 24f
                val strokeColor = if (isWhiteBg) Color.parseColor("#CBD5E1") else Color.parseColor("#26FFFFFF")
                setStroke(2, strokeColor)
            }
            root.background = bg
        }
    }

    private fun updateLanguageBadge(sourceLang: Language, targetLang: Language) {
        tvLanguageBadge?.text = "${sourceLang.shortCode} ➔ ${targetLang.shortCode}"
    }

    private fun startRecognitionPipeline() {
        if (isPaused) return
        val app = application as LiveTransApplication
        val src = app.appPreferences.loadSourceLanguage()
        val tgt = app.appPreferences.loadTargetLanguage()
        val audioSource = app.appPreferences.loadAudioSource()
        updateLanguageBadge(src, tgt)

        serviceScope.launch {
            translationEngineManager.prepareCurrentEngine(src, tgt)
        }

        var mediaProjection: android.media.projection.MediaProjection? = null
        if (audioSource == AudioSourceType.SYSTEM_INTERNAL && projectionData != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? android.media.projection.MediaProjectionManager
                mediaProjection = mediaProjectionManager?.getMediaProjection(projectionResultCode, projectionData!!)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get MediaProjection", e)
            }
        }

        speechRecognitionManager?.start(src, audioSource, mediaProjection)
    }

    private fun onSpeechResult(text: String) {
        if (isPaused || text.isBlank()) return

        tvOriginal?.text = text
        tvStatusHint?.text = "⚡ 正在极速同传翻译…"

        lastTranslateJob?.cancel()
        lastTranslateJob = serviceScope.launch {
            delay(80)
            val app = application as LiveTransApplication
            val src = app.appPreferences.loadSourceLanguage()
            val tgt = app.appPreferences.loadTargetLanguage()

            val result = translationEngineManager.translate(text, src, tgt)
            withContext(Dispatchers.Main) {
                if (result.isSuccess && !result.getOrNull().isNullOrBlank()) {
                    tvTranslated?.text = result.getOrNull()
                    tvStatusHint?.text = "✔ 同传已就绪 · ${app.appPreferences.loadEngineType().displayName}"
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "未知错误"
                    Log.w(TAG, "Translation error: $errMsg")
                    tvTranslated?.text = "【翻译异常: $errMsg】"
                    tvStatusHint?.text = "⚠️ 翻译未成功，可前往主界面切换引擎"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(testReceiver)
        } catch (_: Exception) {}

        speechRecognitionManager?.destroy()
        speechRecognitionManager = null
        translationEngineManager.close()

        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            }
        }
        overlayView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LiveSubtitleService"
        private const val CHANNEL_ID = "live_subtitles_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_TEST_SPEECH = "com.livetrans.overlay.ACTION_TEST_SPEECH"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_PROJECTION_RESULT_CODE = "extra_projection_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"

        fun start(context: Context, resultCode: Int = 0, projectionData: Intent? = null) {
            val intent = Intent(context, LiveSubtitleService::class.java).apply {
                if (resultCode != 0 && projectionData != null) {
                    putExtra(EXTRA_PROJECTION_RESULT_CODE, resultCode)
                    putExtra(EXTRA_PROJECTION_DATA, projectionData)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveSubtitleService::class.java))
        }

        fun sendTestSpeech(context: Context, text: String) {
            val intent = Intent(ACTION_TEST_SPEECH).apply {
                putExtra(EXTRA_TEXT, text)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }
}
