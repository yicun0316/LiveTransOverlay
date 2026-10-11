package com.livetrans.overlay.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import com.livetrans.overlay.data.Language
import com.livetrans.overlay.data.SubtitleStyle
import com.livetrans.overlay.engine.AndroidSystemSpeechEngine
import com.livetrans.overlay.engine.ISpeechRecognitionEngine
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
    private lateinit var speechEngine: ISpeechRecognitionEngine

    private var isPaused = false
    private var isTouchLocked = false
    private var lastTranslateJob: Job? = null

    // UI elements in overlay
    private var tvOriginal: TextView? = null
    private var tvTranslated: TextView? = null
    private var tvLanguageBadge: TextView? = null
    private var btnPause: ImageView? = null
    private var btnLock: ImageView? = null
    private var btnClose: ImageView? = null
    private var rootContainer: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val app = application as LiveTransApplication
        translationEngineManager = TranslationEngineManager(app.appPreferences)
        speechEngine = AndroidSystemSpeechEngine(this)

        createNotificationChannel()
        startServiceAsForeground()

        createFloatingOverlay()
        observeSettings()
        startRecognitionPipeline()
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

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 180 // Default top margin
        }

        overlayView?.let { view ->
            rootContainer = view.findViewById(R.id.rootOverlayContainer)
            tvOriginal = view.findViewById(R.id.tvOriginalText)
            tvTranslated = view.findViewById(R.id.tvTranslatedText)
            tvLanguageBadge = view.findViewById(R.id.tvLanguageBadge)
            btnPause = view.findViewById(R.id.btnPause)
            btnLock = view.findViewById(R.id.btnLock)
            btnClose = view.findViewById(R.id.btnClose)

            setupDragAndTouchEvents(view)
            setupButtonControls()

            windowManager.addView(view, layoutParams)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndTouchEvents(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        val headerBar = view.findViewById<View>(R.id.headerControlBar)
        headerBar.setOnTouchListener { _, event ->
            if (isTouchLocked) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, layoutParams)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupButtonControls() {
        btnPause?.setOnClickListener {
            isPaused = !isPaused
            if (isPaused) {
                btnPause?.setImageResource(android.R.drawable.ic_media_play)
                tvTranslated?.text = "【已暂停同传】"
                speechEngine.stopListening()
            } else {
                btnPause?.setImageResource(android.R.drawable.ic_media_pause)
                tvTranslated?.text = getString(R.string.overlay_waiting_trans)
                val app = application as LiveTransApplication
                speechEngine.startListening(
                    app.appPreferences.loadSourceLanguage(),
                    ::onSpeechResult,
                    ::onSpeechResult,
                    ::onSpeechError
                )
            }
        }

        btnLock?.setOnClickListener {
            toggleTouchLock()
        }

        btnClose?.setOnClickListener {
            stopSelf()
        }
    }

    private fun toggleTouchLock() {
        isTouchLocked = !isTouchLocked
        if (isTouchLocked) {
            btnLock?.setColorFilter(Color.parseColor("#EF4444"))
            // Add NOT_TOUCHABLE flag so taps go directly to the underlying video / game
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            btnLock?.setColorFilter(Color.parseColor("#94A3B8"))
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        overlayView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    private fun observeSettings() {
        val app = application as LiveTransApplication

        // Observe Subtitle Style Changes in Real Time
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
                restartSpeechEngine(src)
            }
        }
    }

    private fun applySubtitleStyle(style: SubtitleStyle) {
        tvTranslated?.apply {
            setTextColor(style.textColor)
            textSize = style.textSizeSp
            if (style.enableTextShadow) {
                setShadowLayer(4f, 2f, 2f, Color.BLACK)
            } else {
                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }
        }

        tvOriginal?.apply {
            visibility = if (style.showOriginalText) View.VISIBLE else View.GONE
            setTextColor(style.originalTextColor)
            textSize = style.originalTextSizeSp
        }

        rootContainer?.let { root ->
            val bg = GradientDrawable().apply {
                setColor(style.backgroundColor)
                cornerRadius = 32f
                setStroke(2, Color.parseColor("#26FFFFFF"))
            }
            root.background = bg
        }
    }

    private fun updateLanguageBadge(sourceLang: Language, targetLang: Language) {
        tvLanguageBadge?.text = "${sourceLang.shortCode} ➔ ${targetLang.shortCode}"
    }

    private fun startRecognitionPipeline() {
        val app = application as LiveTransApplication
        val src = app.appPreferences.loadSourceLanguage()
        val tgt = app.appPreferences.loadTargetLanguage()
        updateLanguageBadge(src, tgt)

        serviceScope.launch {
            translationEngineManager.prepareCurrentEngine(src, tgt)
        }

        speechEngine.startListening(
            src,
            onPartialResult = ::onSpeechResult,
            onFinalResult = ::onSpeechResult,
            onError = ::onSpeechError
        )
    }

    private fun restartSpeechEngine(language: Language) {
        speechEngine.stopListening()
        speechEngine.startListening(
            language,
            onPartialResult = ::onSpeechResult,
            onFinalResult = ::onSpeechResult,
            onError = ::onSpeechError
        )
    }

    private fun onSpeechResult(text: String) {
        if (isPaused || text.isBlank()) return

        tvOriginal?.text = text

        // Debounce translation calls by 120ms to allow streaming sentences to settle
        lastTranslateJob?.cancel()
        lastTranslateJob = serviceScope.launch {
            delay(120)
            val app = application as LiveTransApplication
            val src = app.appPreferences.loadSourceLanguage()
            val tgt = app.appPreferences.loadTargetLanguage()

            val result = translationEngineManager.translate(text, src, tgt)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    tvTranslated?.text = result.getOrNull()
                } else {
                    Log.w(TAG, "Translation error: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    private fun onSpeechError(error: String) {
        Log.w(TAG, "ASR Error: $error")
    }

    override fun onDestroy() {
        super.onDestroy()
        speechEngine.destroy()
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

        fun start(context: Context) {
            val intent = Intent(context, LiveSubtitleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveSubtitleService::class.java))
        }
    }
}
