package com.example.vibic

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class AppError(
    val code: Int,          // 400, 404, 500, 0 = нет сети
    val message: String,
    val endpoint: String = "" // какой запрос упал, напр. "/chats/send"
)

object ErrorBus {
    private val _errors = MutableSharedFlow<AppError>(extraBufferCapacity = 8)
    val errors = _errors.asSharedFlow()

    fun report(code: Int, message: String, endpoint: String = "") {
        _errors.tryEmit(AppError(code, message, endpoint))
    }
}