package com.eerimoq.moblink

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import com.eerimoq.moblink.ui.theme.MoblinkTheme

class MainActivity : ComponentActivity() {
    private val relay = Relay()
    private var settings: Settings? = null
    private var started = false
    private var version = "?"
    private val buttonText = mutableStateOf("Start")
    private val status = mutableStateOf("")
    private val wakeLock = WakeLock()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setup()
        setContent { MoblinkTheme { Surface { Main() } } }
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun setup() {
        wakeLock.setup(this)
        settings = Settings(getSharedPreferences("settings", Context.MODE_PRIVATE))
        relay.setup(
            settings!!.relayId,
            settings!!.streamerUrl,
            settings!!.password,
            settings!!.name,
        ) { status ->
            runOnUiThread { this.status.value = status }
        }
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            version = packageInfo.versionName
        } catch (_: Exception) {}
        requestNetwork(NetworkCapabilities.TRANSPORT_CELLULAR, createCellularNetworkRequest())
        requestNetwork(NetworkCapabilities.TRANSPORT_WIFI, createWiFiNetworkRequest())
    }

    private fun saveSettings() {
        settings!!.store()
        relay.updateSettings(
            settings!!.relayId,
            settings!!.streamerUrl,
            settings!!.password,
            settings!!.name,
        )
    }

    private fun start() {
        if (started) {
            return
        }
        started = true
        startService(this)
        wakeLock.acquire()
        relay.start()
    }

    private fun stop() {
        if (!started) {
            return
        }
        started = false
        stopService(this)
        wakeLock.release()
        relay.stop()
    }

    private fun requestNetwork(transportType: Int, networkCallback: NetworkCallback) {
        val networkRequest =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(transportType)
                .build()
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.requestNetwork(networkRequest, networkCallback)
    }

    private fun createCellularNetworkRequest(): NetworkCallback {
        return object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                relay.setCellularNetwork(network)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                relay.setCellularNetwork(null)
            }
        }
    }

    private fun createWiFiNetworkRequest(): NetworkCallback {
        return object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                relay.setWiFiNetwork(network)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                relay.setWiFiNetwork(null)
            }
        }
    }

    @Composable
    fun Main() {
        var streamerUrlInput by remember { mutableStateOf(settings!!.streamerUrl) }
        var passwordInput by remember { mutableStateOf(settings!!.password) }
        var nameInput by remember { mutableStateOf(settings!!.name) }
        val text by buttonText
        val status by status
        val focusManager = LocalFocusManager.current
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Moblink relay", fontSize = 30.sp)
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
            )
            OutlinedTextField(
                value = streamerUrlInput,
                onValueChange = {
                    streamerUrlInput = it
                    settings!!.streamerUrl = streamerUrlInput
                    saveSettings()
                },
                label = { Text("Streamer URL") },
                placeholder = { Text("ws://192.168.0.10:7777") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
            OutlinedTextField(
                value = passwordInput,
                onValueChange = {
                    passwordInput = it
                    settings!!.password = passwordInput
                    saveSettings()
                },
                label = { Text("Password") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
            OutlinedTextField(
                value = nameInput,
                onValueChange = {
                    nameInput = it
                    settings!!.name = nameInput
                    saveSettings()
                },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
            Text(status)
            Button(
                onClick = {
                    if (!started) {
                        buttonText.value = "Stop"
                        start()
                    } else {
                        buttonText.value = "Start"
                        stop()
                    }
                }
            ) {
                Text(text)
            }
            Text("Version $version")
        }
    }
}
