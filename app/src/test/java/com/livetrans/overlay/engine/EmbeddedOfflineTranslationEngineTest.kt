package com.livetrans.overlay.engine

import com.livetrans.overlay.data.Language
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedOfflineTranslationEngineTest {

    private val engine = EmbeddedOfflineTranslationEngine()

    @Test
    fun testEnglishToChinese() = runBlocking {
        val res1 = engine.translate("Hello, welcome to live translation.", Language.ENGLISH, Language.CHINESE)
        assertTrue(res1.isSuccess)
        val text1 = res1.getOrNull() ?: ""
        assertTrue("Expected translation not empty: $text1", text1.isNotEmpty())
        assertTrue("Expected contains 你好: $text1", text1.contains("你好"))

        val res2 = engine.translate("Good morning", Language.ENGLISH, Language.CHINESE)
        assertTrue(res2.isSuccess)
        val text2 = res2.getOrNull() ?: ""
        assertTrue("Expected 早上好: $text2", text2.contains("早上好"))
    }

    @Test
    fun testJapaneseToChinese() = runBlocking {
        val res = engine.translate("こんにちは、翻訳テストです。", Language.JAPANESE, Language.CHINESE)
        assertTrue(res.isSuccess)
        val text = res.getOrNull() ?: ""
        assertTrue("Expected translation not empty: $text", text.isNotEmpty())
        assertTrue("Expected contains 你好: $text", text.contains("你好"))
    }

    @Test
    fun testChineseToEnglish() = runBlocking {
        val res = engine.translate("你好，世界", Language.CHINESE, Language.ENGLISH)
        assertTrue(res.isSuccess)
        val text = res.getOrNull() ?: ""
        assertTrue("Expected translation not empty: $text", text.isNotEmpty())
        assertTrue("Expected contains Hello: $text", text.contains("Hello"))
    }

    @Test
    fun testNeverThrowsOnEmptyOrArbitraryText() = runBlocking {
        val emptyRes = engine.translate("", Language.ENGLISH, Language.CHINESE)
        assertTrue(emptyRes.isSuccess)

        val arbitrary = engine.translate("Random 123 !? UnknownWord", Language.ENGLISH, Language.CHINESE)
        assertTrue(arbitrary.isSuccess)
        assertFalse(arbitrary.getOrNull().isNullOrBlank())
    }
}
