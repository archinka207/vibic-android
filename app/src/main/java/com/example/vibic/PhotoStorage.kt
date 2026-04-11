package com.example.vibic.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class PhotoStorage private constructor(private val context: Context) {

    private val photoFileName = "profile_photo.jpg"

    fun save(bitmap: Bitmap) {
        val file = File(context.filesDir, photoFileName)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            Log.d("PhotoStorage", "✅ Фото успешно сохранено: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("PhotoStorage", "❌ Ошибка сохранения фото: ${e.localizedMessage}")
        }
    }

    fun load(): Bitmap? {
        val file = File(context.filesDir, photoFileName)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath).also {
                Log.d("PhotoStorage", "✅ Фото успешно загружено")
            }
        } catch (e: Exception) {
            Log.e("PhotoStorage", "❌ Ошибка загрузки фото: ${e.localizedMessage}")
            null
        }
    }

    fun delete() {
        val file = File(context.filesDir, photoFileName)
        if (file.exists() && file.delete()) {
            Log.d("PhotoStorage", "✅ Фото успешно удалено")
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PhotoStorage? = null

        fun shared(context: Context): PhotoStorage {
            return INSTANCE ?: synchronized(this) {
                val instance = PhotoStorage(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}