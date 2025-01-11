package com.eerimoq.moblink

import android.content.SharedPreferences
import com.google.gson.Gson
import java.util.UUID

class Database {
    var relayId = UUID.randomUUID().toString()
    var name = randomName()
    var password: String? = "1234"
    var port: Int? = 7777
}

class Settings(private val sharedPreferences: SharedPreferences) {
    var database = Database()

    init {
        load()
    }

    private fun load() {
        val value = sharedPreferences.getString("database", "{}") ?: "{}"
        try {
            database = Gson().fromJson(value, Database::class.java)
        } catch (_: Exception) {}
        if (database.password == null) {
            database.password = "1234"
        }
        if (database.port == null) {
            database.port = 7777
        }
        store()
    }

    fun store() {
        val editor = sharedPreferences.edit()
        editor.putString("database", Gson().toJson(database))
        editor.apply()
    }
}

private fun randomName(): String {
    val colors = arrayOf("Black", "Red", "Green", "Yellow", "Blue", "Purple", "Cyan", "White")
    return colors.random()
}
