package com.eerimoq.moblink

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eerimoq.moblink.ui.theme.MoblinkTheme

class MainActivity : ComponentActivity() {
    private val relays = arrayOf(Relay(), Relay(), Relay(), Relay(), Relay())
    private var settings: Settings? = null
    private var version = "?"
    private val wakeLock = WakeLock()
    private var scanner: Scanner? = null
    private var automaticStarted = false
    private var automaticButtonText = mutableStateOf("Start")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setup()
        setContent { MoblinkTheme { Surface { Main() } } }
    }

    override fun onDestroy() {
        for (relay in relays) {
            stop(relay)
        }
        super.onDestroy()
    }

    private fun setup() {
        wakeLock.setup(this)
        settings = Settings(getSharedPreferences("settings", Context.MODE_PRIVATE))
        val database = settings!!.database
        for ((relay, relaySettings) in relays.zip(database.relays)) {
            relay.setup(
                database.relayId,
                relaySettings.streamerUrl,
                relaySettings.password,
                database.name,
                { status -> runOnUiThread { relay.uiStatus.value = status } },
                { callback -> getBatteryPercentage(callback) },
            )
        }
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            version = packageInfo.versionName
        } catch (_: Exception) {}
        requestNetwork(NetworkCapabilities.TRANSPORT_CELLULAR, createCellularNetworkRequest())
        requestNetwork(NetworkCapabilities.TRANSPORT_WIFI, createWiFiNetworkRequest())
        requestNetwork(NetworkCapabilities.TRANSPORT_ETHERNET, createEthernetNetworkRequest())
        scanner = Scanner(getSystemService(Context.NSD_SERVICE) as NsdManager)
        scanner?.setup()
    }

    private fun saveSettings() {
        settings!!.store()
        val database = settings!!.database
        for ((relay, relaySettings) in relays.zip(database.relays)) {
            relay.updateSettings(
                database.relayId,
                relaySettings.streamerUrl,
                relaySettings.password,
                database.name,
            )
        }
    }

    private fun start(relay: Relay) {
        if (!isStarted()) {
            startService(this)
            wakeLock.acquire()
        }
        relay.uiStarted = true
        relay.start()
    }

    private fun stop(relay: Relay) {
        relay.uiStarted = false
        relay.stop()
        if (!isStarted()) {
            stopService(this)
            wakeLock.release()
        }
    }

    private fun isStarted(): Boolean {
        return relays.any { it.uiStarted }
    }

    private fun getBatteryPercentage(callback: (Int) -> Unit) {
        runOnUiThread {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            callback(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
        }
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
                for (relay in relays) {
                    relay.setCellularNetwork(network)
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                for (relay in relays) {
                    relay.setCellularNetwork(null)
                }
            }
        }
    }

    private fun createWiFiNetworkRequest(): NetworkCallback {
        return object : NetworkCallback() {}
    }

    private fun createEthernetNetworkRequest(): NetworkCallback {
        return object : NetworkCallback() {}
    }

    @Composable
    fun Main() {
        var manual by remember { mutableStateOf(settings!!.database.manual!!) }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Moblink relay", modifier = Modifier.padding(top = 70.dp), fontSize = 30.sp)
            AppIcon()
            NameField()
            Row(
                modifier = Modifier.padding(top = 5.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Manual")
                Spacer(modifier = Modifier.padding(horizontal = 50.dp))
                Switch(
                    checked = manual,
                    onCheckedChange = {
                        manual = it
                        settings!!.database.manual = manual
                        saveSettings()
                    },
                )
            }
            if (manual) {
                Streamers()
            } else {
                Automatic()
            }
            Text("Version $version")
            Spacer(modifier = Modifier.fillMaxHeight())
        }
    }

    @Composable
    fun AppIcon() {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
        )
    }

    @Composable
    fun NameField() {
        var nameInput by remember { mutableStateOf(settings!!.database.name) }
        val focusManager = LocalFocusManager.current
        OutlinedTextField(
            value = nameInput,
            onValueChange = {
                nameInput = it
                settings!!.database.name = nameInput
                saveSettings()
            },
            label = { Text("Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
    }

    @Composable
    fun Automatic() {
        PasswordField(settings!!.database.automatic!!)
        Text("Connected to 2 streamers")
        AutomaticStartStopButton()
    }

    @Composable
    fun AutomaticStartStopButton() {
        val text by automaticButtonText
        Button(
            modifier = Modifier.padding(10.dp),
            onClick = {
                if (!automaticStarted) {
                    automaticButtonText.value = "Stop"
                    automaticStarted = true
                } else {
                    automaticButtonText.value = "Start"
                    automaticStarted = false
                }
            },
        ) {
            Text(text)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Streamers() {
        val pagerState = rememberPagerState(pageCount = { relays.count() })
        StreamerDots(pagerState)
        HorizontalPager(state = pagerState) { relayIndex -> Streamer(relayIndex) }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun StreamerDots(pagerState: PagerState) {
        Row(
            Modifier.wrapContentHeight().fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(relays.count()) { relayIndex ->
                val color =
                    if (pagerState.currentPage == relayIndex) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.inversePrimary
                Box(
                    modifier =
                        Modifier.padding(2.dp).clip(CircleShape).background(color).size(10.dp)
                )
            }
        }
    }

    @Composable
    fun Streamer(relayIndex: Int) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val relay = relays[relayIndex]
            val relaySettings = settings!!.database.relays[relayIndex]
            val status by relay.uiStatus
            StreamerUrlField(relaySettings)
            PasswordField(relaySettings)
            Text(status)
            StartStopButton(relay)
        }
    }

    @Composable
    fun StreamerUrlField(relaySettings: RelaySettings) {
        val focusManager = LocalFocusManager.current
        var streamerUrlInput by remember { mutableStateOf(relaySettings.streamerUrl) }
        OutlinedTextField(
            value = streamerUrlInput,
            onValueChange = {
                streamerUrlInput = it
                relaySettings.streamerUrl = streamerUrlInput
                saveSettings()
            },
            label = { Text("Streamer URL") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
    }

    @Composable
    fun PasswordField(relaySettings: RelaySettings) {
        val focusManager = LocalFocusManager.current
        var passwordInput by remember { mutableStateOf(relaySettings.password) }
        OutlinedTextField(
            modifier = Modifier.padding(bottom = 15.dp),
            value = passwordInput,
            onValueChange = {
                passwordInput = it
                relaySettings.password = passwordInput
                saveSettings()
            },
            label = { Text("Password") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
    }

    @Composable
    fun StartStopButton(relay: Relay) {
        val text by relay.uiButtonText
        Button(
            modifier = Modifier.padding(10.dp),
            onClick = {
                if (!relay.uiStarted) {
                    relay.uiButtonText.value = "Stop"
                    start(relay)
                } else {
                    relay.uiButtonText.value = "Start"
                    stop(relay)
                }
            },
        ) {
            Text(text)
        }
    }
}
