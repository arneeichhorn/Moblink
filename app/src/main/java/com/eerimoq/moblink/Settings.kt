package com.eerimoq.moblink

import android.content.SharedPreferences
import com.google.gson.Gson
import java.util.UUID

class RelaySettings {
    var streamerUrl = ""
    var password = "1234"
}

class Database {
    var relayId = UUID.randomUUID().toString()
    var name = randomName()
    var relays =
        arrayOf(RelaySettings(), RelaySettings(), RelaySettings(), RelaySettings(), RelaySettings())
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
