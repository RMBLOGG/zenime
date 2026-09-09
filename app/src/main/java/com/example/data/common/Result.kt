package com.example.data.common

sealed class Result<out T> {
    data object Loading : Result<Nothing>()
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String = exception.localizedMessage ?: "Terjadi kesalahan") : Result<Nothing>()
}
