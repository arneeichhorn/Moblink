package com.eerimoq.moblink

import android.content.SharedPreferences
import com.google.gson.Gson
import java.util.UUID

class RelaySettings {
    var streamerUrl = ""
    var password = ""
}

class Database {
    var relayId = UUID.randomUUID().toString()
    var name = "Relay"
    var relays: List<RelaySettings> =
        listOf(RelaySettings(), RelaySettings(), RelaySettings(), RelaySettings(), RelaySettings())
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
        } catch (_: Exception) {
            // Migrate from old database.
            database.relays[0].streamerUrl = sharedPreferences.getString("streamerUrl", "") ?: ""
            database.relays[0].password = sharedPreferences.getString("password", "") ?: ""
            val uuid = UUID.randomUUID().toString()
            database.relayId = sharedPreferences.getString("relayId", uuid) ?: uuid
            database.name = sharedPreferences.getString("name", "Relay") ?: "Relay"
            store()
        }
    }

    fun store() {
        val editor = sharedPreferences.edit()
        editor.putString("database", Gson().toJson(database))
        editor.apply()
    }
}
