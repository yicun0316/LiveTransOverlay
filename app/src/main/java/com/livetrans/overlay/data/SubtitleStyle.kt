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
    val windowHeightDp: Int = 160,
    val isFolded: Boolean = false,
    val maxHistoryParagraphs: Int = 3, // 1: 单句极简, 2: 2段滚动, 3: 3段标准(默认), 4: 4段长篇
    val enableSilenceFade: Boolean = true // 静音8秒自动变淡半透明保护
) {
    companion object {
        val PRESET_COLORS = listOf(
            0xFF000000.toInt(), // 纯黑 (白底首选)
            0xFFFACC15.toInt(), // 荧光黄 (黑底首选)
            0xFF2563EB.toInt(), // 科技蓝
            0xFF10B981.toInt(), // 翡翠绿
            0xFFEF4444.toInt(), // 鲜亮红
            0xFF8B5CF6.toInt(), // 优雅紫
            0xFFF97316.toInt(), // 活力橙
            0xFFEC4899.toInt(), // 芭比粉
            0xFF06B6D4.toInt(), // 赛博青
            0xFFFFFFFF.toInt()  // 纯白
        )

        val PRESET_BG_STYLES = listOf(
            0xFFFFFFFF.toInt() to "纯白背景",
            0xEBFFFFFF.toInt() to "白底半透",
            0xCC0F172A.toInt() to "暗黑磨砂",
            0x00000000 to "完全透明"
        )
    }
}
