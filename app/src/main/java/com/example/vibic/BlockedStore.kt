package com.example.vibic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BlockedUser(
    val id: String,
    val name: String,
    val letter: String
)

object BlockedStore {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_BLOCKED_USERS = "blocked_users"

    fun add(context: Context, id: String, name: String) {
        val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val users = getAll(context).filterNot { it.id == id }.toMutableList()
        users.add(BlockedUser(id, name, letter))
        save(context, users)
    }

    fun remove(context: Context, id: String) {
        val users = getAll(context).filterNot { it.id == id }
        save(context, users)
    }

    fun getAll(context: Context): List<BlockedUser> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_BLOCKED_USERS, null) ?: return emptyList()

        val result = mutableListOf<BlockedUser>()
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                BlockedUser(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    letter = obj.optString("letter")
                )
            )
        }
        return result
    }

    private fun save(context: Context, users: List<BlockedUser>) {
        val array = JSONArray()
        for (user in users) {
            val obj = JSONObject()
            obj.put("id", user.id)
            obj.put("name", user.name)
            obj.put("letter", user.letter)
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BLOCKED_USERS, array.toString())
            .apply()
    }
}
