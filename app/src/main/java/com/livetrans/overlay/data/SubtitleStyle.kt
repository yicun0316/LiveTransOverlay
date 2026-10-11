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
    val isLockedTouchThrough: Boolean = false,
    val windowWidthDp: Int = 320,  // Resizable width (dp)
    val windowHeightDp: Int = 160, // Resizable height (dp)
    val isFolded: Boolean = false  // Collapsed / folded status
) {
    companion object {
        // Preset popular subtitle color themes
        val PRESET_COLORS = listOf(
            0xFFFACC15.toInt(), // Neon Yellow (Industry standard for videos)
            0xFFFFFFFF.toInt(), // Pure White
            0xFF22D3EE.toInt(), // Cyber Cyan
            0xFF4ADE80.toInt(), // Mint Green
            0xFFF43F5E.toInt(), // Neon Coral
            0xFFA855F7.toInt(), // Soft Purple
            0xFFFF8C00.toInt(), // Sunset Orange
            0xFFFF69B4.toInt()  // Hot Pink
        )

        val PRESET_BG_OPACITIES = listOf(
            0.0f to "全透明 (0%)",
            0.35f to "轻度磨砂 (35%)",
            0.75f to "标准半透 (75%)",
            0.95f to "纯黑高对比 (95%)"
        )
    }
}
