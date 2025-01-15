package com.eerimoq.moblink

import android.content.SharedPreferences
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
class RelaySettings {
    var streamerUrl = ""
    var password = "1234"
}

@Serializable
class Database {
    var relayId = UUID.randomUUID().toString()
    var name = randomName()
    var relays =
        arrayOf(RelaySettings(), RelaySettings(), RelaySettings(), RelaySettings(), RelaySettings())

    fun toJson(): String {
        return Json.encodeToString(this)
    }

    companion object {
        fun fromJson(text: String): Database {
            return Json.decodeFromString(text)
        }
    }
}

class Settings(private val sharedPreferences: SharedPreferences) {
    var database = Database()

    init {
        load()
    }

    private fun load() {
        val value = sharedPreferences.getString("database", "{}") ?: "{}"
        try {
            database = Database.fromJson(value)
        } catch (_: Exception) {}
    }

    fun store() {
        val editor = sharedPreferences.edit()
        editor.putString("database", database.toJson())
        editor.apply()
    }
}

private fun randomName(): String {
    val colors = arrayOf("Black", "Red", "Green", "Yellow", "Blue", "Purple", "Cyan", "White")
    return colors.random()
}
