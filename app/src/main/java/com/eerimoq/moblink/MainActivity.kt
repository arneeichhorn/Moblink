package com.eerimoq.moblink

import android.R.attr.label
import android.R.attr.text
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eerimoq.moblink.ui.theme.MoblinkTheme
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private var relay: Relay? = null
    private var settings: Settings? = null
    private var version = "?"
    private val wakeLock = WakeLock()
    private val buttonText = mutableStateOf("Start")
    private val statusText = mutableStateOf("Not started")
    private var started = false
    private var cellularNetworkRequest: NetworkCallback? = null
    private var wifiNetworkRequest: NetworkCallback? = null
    private val handlerThread = HandlerThread("Something")
    private var handler: Handler? = null
    private val ipAddresses = mutableStateOf(emptyList<InetAddress>())

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
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        wakeLock.setup(this)
        settings = Settings(getSharedPreferences("settings", Context.MODE_PRIVATE))
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            version = packageInfo.versionName
        } catch (_: Exception) {}
    }

    private fun saveSettings() {
        settings!!.store()
        val database = settings!!.database
        relay?.updateSettings(database.relayId, database.name, database.password!!)
    }

    private fun start() {
        if (started) {
            return
        }
        started = true
        startService(this)
        wakeLock.acquire()
        val database = settings!!.database
        relay = Relay(handler!!, InetSocketAddress(database.port!!))
        relay?.setup(
            database.relayId,
            database.name,
            database.password!!,
            { status -> runOnUiThread { statusText.value = status } },
            { callback -> getBatteryPercentage(callback) },
        )
        relay?.start()
        cellularNetworkRequest = createCellularNetworkRequest()
        wifiNetworkRequest = createWiFiNetworkRequest()
        requestNetwork(NetworkCapabilities.TRANSPORT_CELLULAR, cellularNetworkRequest!!)
        requestNetwork(NetworkCapabilities.TRANSPORT_WIFI, wifiNetworkRequest!!)
    }

    private fun stop() {
        if (!started) {
            return
        }
        started = false
        stopService(this)
        wakeLock.release()
        unregisterNetwork(cellularNetworkRequest!!)
        unregisterNetwork(wifiNetworkRequest!!)
        relay?.stop()
        relay = null
        ipAddresses.value = emptyList()
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

    private fun unregisterNetwork(networkCallback: NetworkCallback) {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private fun createCellularNetworkRequest(): NetworkCallback {
        return object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                relay?.setCellularNetwork(network)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                relay?.setCellularNetwork(null)
            }
        }
    }

    private fun createWiFiNetworkRequest(): NetworkCallback {
        return object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                relay?.setWiFiNetwork(network)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                relay?.setWiFiNetwork(null)
                runOnUiThread { ipAddresses.value = emptyList() }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                runOnUiThread {
                    ipAddresses.value =
                        linkProperties.linkAddresses.map { address -> address.address }
                }
            }
        }
    }

    @Composable
    fun Main() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Moblink relay", modifier = Modifier.padding(top = 50.dp), fontSize = 30.sp)
            AppIcon()
            NameField()
            PasswordField()
            Status()
            StartStopButton()
            WebSocketUrls()
            Text("Version $version", modifier = Modifier.padding(top = 5.dp))
            Spacer(modifier = Modifier.fillMaxSize())
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
    fun PasswordField() {
        val focusManager = LocalFocusManager.current
        var passwordInput by remember { mutableStateOf("1234") }
        OutlinedTextField(
            modifier = Modifier.padding(top = 5.dp, bottom = 10.dp),
            value = passwordInput,
            onValueChange = {
                passwordInput = it
                saveSettings()
            },
            label = { Text("Password") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
    }

    @Composable
    fun Status() {
        val text by statusText
        Text(text)
    }

    @Composable
    fun StartStopButton() {
        val text by buttonText
        Button(
            modifier = Modifier.padding(top = 5.dp),
            onClick = {
                if (!started) {
                    start()
                    buttonText.value = "Stop"
                } else {
                    stop()
                    buttonText.value = "Start"
                }
            },
        ) {
            Text(text)
        }
    }

    @Composable
    fun WebSocketUrls() {
        val addresses by ipAddresses
        if (addresses.isNotEmpty()) {
            Text(
                "Copy one Relay URL to Moblin",
                modifier = Modifier.padding(top = 5.dp, bottom = 5.dp),
            )
        }
        var number = 1
        for (address in addresses.filterIsInstance<Inet4Address>()) {
            WebSocketUrl("IPv4", number, address.hostAddress!!)
            number++
        }
        for (address in addresses.filterIsInstance<Inet6Address>()) {
            WebSocketUrl("IPv6", number, "[${address.hostAddress}]")
            number++
        }
    }

    @Composable
    fun WebSocketUrl(type: String, number: Int, host: String) {
        Button(
            onClick = {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip =
                    ClipData.newPlainText("Relay URL", "ws://$host:${settings!!.database.port!!}")
                clipboard.setPrimaryClip(clip)
            }
        ) {
            Text("Relay URL $number ($type)")
        }
    }

    fun setupBonjour() {
        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifiManager.createMulticastLock("Moblink:lock")
        lock.setReferenceCounted(true)
        lock.acquire()
        thread {
            try {
                val ipAddress = wifiManager.connectionInfo.ipAddress
                val intToIp =
                    InetAddress.getByAddress(
                        byteArrayOf(
                            (ipAddress and 0xff).toByte(),
                            (ipAddress shr 8 and 0xff).toByte(),
                            (ipAddress shr 16 and 0xff).toByte(),
                            (ipAddress shr 24 and 0xff).toByte(),
                        )
                    )
                val jmDns = JmDNS.create(intToIp)
                val serviceInfo = ServiceInfo.create("_moblink._tcp.local", "Moblink", 7777, "")
                jmDns?.registerService(serviceInfo)
                // Stop
                jmDns?.unregisterAllServices()
                jmDns?.close()
            } catch (e: Exception) {}
        }
    }
}
