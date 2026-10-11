package com.livetrans.overlay.data

import android.graphics.Color

data class SubtitleStyle(
    val textColor: Int = Color.BLACK, // Default black on white or customizable
    val textSizeSp: Float = 18f,
    val backgroundColor: Int = Color.WHITE, // Pure white background support
    val showOriginalText: Boolean = true,
    val originalTextColor: Int = Color.parseColor("#64748B"), // Slate secondary
    val originalTextSizeSp: Float = 13f,
    val enableTextShadow: Boolean = false,
    val isLockedTouchThrough: Boolean = false,
    val windowWidthDp: Int = 340,
    val windowHeightDp: Int = 150,
    val isFolded: Boolean = false
) {
    companion object {
        val PRESET_COLORS = listOf(
            0xFF000000.toInt(), // Pure Black (Standard for White Background)
            0xFF2563EB.toInt(), // Deep Blue
            0xFFFACC15.toInt(), // High-Vis Yellow
            0xFFFFFFFF.toInt(), // Pure White
            0xFF10B981.toInt(), // Emerald Green
            0xFFEF4444.toInt()  // Coral Red
        )

        val PRESET_BG_STYLES = listOf(
            0xFFFFFFFF.toInt() to "纯白背景",
            0xEBFFFFFF.toInt() to "白底半透",
            0xCC0F172A.toInt() to "暗黑磨砂",
            0x00000000 to "完全透明"
        )
    }
}
