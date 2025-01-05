package com.eerimoq.moblink

import android.content.SharedPreferences
import java.util.UUID

class Settings(private val sharedPreferences: SharedPreferences) {
    var streamerUrl = ""
    var password = ""
    var relayId = ""
    var name = ""

    init {
        load()
    }

    fun load() {
        streamerUrl = sharedPreferences.getString("streamerUrl", "") ?: ""
        password = sharedPreferences.getString("password", "") ?: ""
        val uuid = UUID.randomUUID().toString()
        relayId = sharedPreferences.getString("relayId", uuid) ?: uuid
        name = sharedPreferences.getString("name", "Relay") ?: "Relay"
    }

    fun store() {
        val editor = sharedPreferences.edit()
        editor.putString("streamerUrl", streamerUrl)
        editor.putString("password", password)
        editor.putString("relayId", relayId)
        editor.putString("name", name)
        editor.apply()
    }
}
