package com.livetrans.overlay.data

import android.graphics.Color

data class SubtitleStyle(
    val textColor: Int = Color.parseColor("#FACC15"), // High visibility bright yellow
    val textSizeSp: Float = 18f,
    val backgroundColor: Int = Color.parseColor("#CC0F172A"), // 80% slate dark
    val showOriginalText: Boolean = true,
    val originalTextColor: Int = Color.parseColor("#94A3B8"), // Slate secondary
    val originalTextSizeSp: Float = 14f,
    val enableTextShadow: Boolean = true,
    val isLockedTouchThrough: Boolean = false
) {
    companion object {
        // Preset popular subtitle color themes
        val PRESET_COLORS = listOf(
            0xFFFACC15.toInt(), // Neon Yellow (Industry standard for videos)
            0xFFFFFFFF.toInt(), // Pure White
            0xFF22D3EE.toInt(), // Cyber Cyan
            0xFF4ADE80.toInt(), // Mint Green
            0xFFF43F5E.toInt(), // Neon Coral
            0xFFA855F7.toInt()  // Soft Purple
        )

        val PRESET_BG_OPACITIES = listOf(
            0.0f to "全透明 (0%)",
            0.4f to "轻度磨砂 (40%)",
            0.75f to "标准半透 (75%)",
            0.95f to "纯黑高对比 (95%)"
        )
    }
}
