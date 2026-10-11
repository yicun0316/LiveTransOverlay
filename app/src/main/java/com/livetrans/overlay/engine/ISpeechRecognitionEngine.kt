package com.livetrans.overlay.engine

import com.livetrans.overlay.data.Language

interface ISpeechRecognitionEngine {
    val name: String

    fun startListening(
        language: Language,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    )

    fun stopListening()
    fun destroy()
}
