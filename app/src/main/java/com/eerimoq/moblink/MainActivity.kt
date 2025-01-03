package com.eerimoq.moblink

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.util.Base64
import android.util.Log
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
import com.google.gson.Gson
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.encodeUtf8

class MainActivity : ComponentActivity() {
    private var webSocket: WebSocket? = null
    private var wiFiNetwork: Network? = null
    private var cellularNetwork: Network? = null
    private var streamerSocket: DatagramSocket? = null
    private var destinationSocket: DatagramSocket? = null
    private var relayId = ""
    private var streamerUrl = ""
    private var password = ""
    private var name = ""
    private val handlerThread = HandlerThread("Something")
    private var handler: Handler? = null
    private val okHttpClient = OkHttpClient.Builder().pingInterval(5, TimeUnit.SECONDS).build()
    private var started = false
    private var connected = false
    private var wrongPassword = false
    private var uiSettings: Settings? = null
    private var uiStarted = false
    private var uiVersion = "?"
    private var uiButtonText = "Start"
    private val uiMutableButtonText = mutableStateOf(uiButtonText)
    private val uiMutableStatus = mutableStateOf("")
    private var uiWakeLock: PowerManager.WakeLock? = null

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
        uiSettings = Settings(getSharedPreferences("settings", Context.MODE_PRIVATE))
        requestNetwork(NetworkCapabilities.TRANSPORT_CELLULAR, "Cellular")
        requestNetwork(NetworkCapabilities.TRANSPORT_WIFI, "WiFi")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        uiSettings!!.load()
        streamerUrl = uiSettings!!.streamerUrl
        password = uiSettings!!.password
        relayId = uiSettings!!.relayId
        name = uiSettings!!.name
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            uiVersion = packageInfo.versionName
        } catch (_: Exception) {}
        handler?.post { updateStatus() }
    }

    private fun updateStatus() {
        val status =
            if (cellularNetwork == null) {
                "Waiting for cellular"
            } else if (wiFiNetwork == null) {
                "Waiting for WiFi"
            } else if (connected) {
                "Connected to streamer"
            } else if (wrongPassword) {
                "Wrong password"
            } else if (started) {
                "Connecting to streamer"
            } else {
                "Disconnected from streamer"
            }
        runOnUiThread { uiMutableStatus.value = status }
    }

    private fun saveSettings() {
        uiSettings!!.store()
        val streamerUrl = uiSettings!!.streamerUrl
        val password = uiSettings!!.password
        val name = uiSettings!!.name
        handler?.post {
            this.streamerUrl = streamerUrl
            this.password = password
            this.name = name
        }
    }

    private fun start() {
        if (uiStarted) {
            return
        }
        uiStarted = true
        startService()
        uiWakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MoblinkService::lock").apply {
                    acquire()
                }
            }
        handler?.post {
            Log.i("Moblink", "Start")
            if (started) {
                return@post
            }
            started = true
            startInternal()
        }
    }

    private fun stop() {
        if (!uiStarted) {
            return
        }
        uiStarted = false
        stopService()
        uiWakeLock?.release()
        handler?.post {
            Log.i("Moblink", "Stop")
            if (!started) {
                return@post
            }
            started = false
            stopInternal()
        }
    }

    private fun startInternal() {
        stopInternal()
        if (!started) {
            return
        }
        try {
            val request = Request.Builder().url(streamerUrl).build()
            webSocket =
                okHttpClient.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            super.onOpen(webSocket, response)
                            handler?.post { Log.i("Moblink", "Websocket opened") }
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            super.onMessage(webSocket, text)
                            handler?.post {
                                if (webSocket === getWebSocket()) {
                                    Log.i("Moblink", "Websocket message received: $text")
                                    handleMessage(text)
                                }
                            }
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            super.onClosed(webSocket, code, reason)
                            handler?.post {
                                if (webSocket === getWebSocket()) {
                                    Log.i("Moblink", "Websocket closed $reason (code $code)")
                                    reconnectSoon()
                                }
                            }
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            super.onFailure(webSocket, t, response)
                            handler?.post {
                                if (webSocket === getWebSocket()) {
                                    Log.i(
                                        "Moblink",
                                        "Websocket failure $t $webSocket ${getWebSocket()}",
                                    )
                                    reconnectSoon()
                                }
                            }
                        }
                    },
                )
        } catch (e: Exception) {
            Log.i("Moblink", "Failed to build URL: $e")
        }
    }

    private fun stopInternal() {
        webSocket?.cancel()
        webSocket = null
        connected = false
        wrongPassword = false
        updateStatus()
        streamerSocket?.close()
        destinationSocket?.close()
    }

    private fun startService() {
        controlService(Actions.START)
    }

    private fun stopService() {
        controlService(Actions.STOP)
    }

    private fun controlService(action: Actions) {
        val intent = Intent(this, MoblinkService::class.java)
        intent.action = action.name
        startForegroundService(intent)
    }

    private fun reconnectSoon() {
        Log.i("Moblink", "Reconnect soon")
        stopInternal()
        handler?.postDelayed(
            {
                Log.i("Moblink", "Reconnect?")
                startInternal()
            },
            5000,
        )
    }

    private fun getWebSocket(): WebSocket? {
        return webSocket
    }

    private fun handleMessage(text: String) {
        try {
            val message = Gson().fromJson(text, MessageToClient::class.java)
            if (message.hello != null) {
                handleMessageHello(message.hello)
            } else if (message.identified != null) {
                handleMessageIdentified(message.identified)
            } else if (message.request != null) {
                handleMessageRequest(message.request)
            }
        } catch (e: Exception) {
            Log.i("Moblink", "Deserialize failed: $e")
        }
    }

    private fun handleMessageHello(hello: Hello) {
        var concatenated = "$password${hello.authentication.salt}"
        val sha256 = MessageDigest.getInstance("SHA-256")
        sha256.reset()
        var hash: ByteArray = sha256.digest(concatenated.encodeUtf8().toByteArray())
        concatenated =
            "${Base64.encodeToString(hash, Base64.NO_WRAP)}${hello.authentication.challenge}"
        sha256.reset()
        hash = sha256.digest(concatenated.encodeUtf8().toByteArray())
        val authentication = Base64.encodeToString(hash, Base64.NO_WRAP)
        val identify = Identify(relayId, name, authentication)
        val message = MessageToServer(identify, null)
        val text = Gson().toJson(message, MessageToServer::class.java)
        send(text)
    }

    private fun handleMessageIdentified(identified: Identified) {
        if (identified.result.ok != null) {
            connected = true
        } else if (identified.result.wrongPassword != null) {
            wrongPassword = true
        }
        updateStatus()
    }

    private fun handleMessageRequest(request: MessageRequest) {
        if (request.data.startTunnel != null) {
            handleMessageStartTunnelRequest(request.id, request.data.startTunnel)
        }
    }

    private fun handleMessageStartTunnelRequest(id: Int, startTunnel: StartTunnelRequest) {
        if (!setupStreamerSocket()) {
            reconnectSoon()
            return
        }
        if (!setupDestinationSocket()) {
            reconnectSoon()
            return
        }
        Log.i("Moblink", "Port: ${streamerSocket!!.localPort}")
        startStreamerReceiver(
            streamerSocket!!,
            destinationSocket!!,
            InetAddress.getByName(startTunnel.address),
            startTunnel.port,
        )
        val data = ResponseData(StartTunnelResponseData(streamerSocket!!.localPort))
        val response = MessageResponse(id, Result(Empty(true), null), data)
        send(Gson().toJson(MessageToServer(null, response)))
    }

    private fun setupStreamerSocket(): Boolean {
        streamerSocket?.close()
        if (wiFiNetwork == null) {
            Log.i("Moblink", "WiFi network not ready")
            return false
        }
        streamerSocket = DatagramSocket()
        wiFiNetwork?.bindSocket(streamerSocket)
        return true
    }

    private fun setupDestinationSocket(): Boolean {
        destinationSocket?.close()
        if (cellularNetwork == null) {
            Log.i("Moblink", "Cellular network not ready")
            return false
        }
        destinationSocket = DatagramSocket()
        cellularNetwork?.bindSocket(destinationSocket)
        return true
    }

    private fun send(text: String) {
        webSocket?.send(text)
    }

    private fun requestNetwork(transportType: Int, type: String) {
        val networkRequest =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(transportType)
                .build()
        val networkCallback =
            object : NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    handler?.post {
                        Log.i("Moblink", "$type available")
                        if (transportType == NetworkCapabilities.TRANSPORT_CELLULAR) {
                            cellularNetwork = network
                        } else {
                            wiFiNetwork = network
                        }
                        updateStatus()
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    handler?.post {
                        Log.i("Moblink", "$type lost")
                        if (transportType == NetworkCapabilities.TRANSPORT_CELLULAR) {
                            cellularNetwork = null
                        } else {
                            wiFiNetwork = null
                        }
                        updateStatus()
                    }
                }
            }
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.requestNetwork(networkRequest, networkCallback)
    }

    @Composable
    fun Main() {
        var streamerUrlInput by remember { mutableStateOf(uiSettings!!.streamerUrl) }
        var passwordInput by remember { mutableStateOf(uiSettings!!.password) }
        var nameInput by remember { mutableStateOf(uiSettings!!.name) }
        val text by uiMutableButtonText
        val status by uiMutableStatus
        val focusManager = LocalFocusManager.current
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Moblink", fontSize = 30.sp)
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
            )
            OutlinedTextField(
                value = streamerUrlInput,
                onValueChange = {
                    streamerUrlInput = it
                    uiSettings!!.streamerUrl = it
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
                    uiSettings!!.password = it
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
                    uiSettings!!.name = it
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
                    if (text == "Start") {
                        uiMutableButtonText.value = "Stop"
                        start()
                    } else {
                        uiMutableButtonText.value = "Start"
                        stop()
                    }
                }
            ) {
                Text(text)
            }
            Text("Version $uiVersion")
        }
    }
}

private fun startStreamerReceiver(
    streamerSocket: DatagramSocket,
    destinationSocket: DatagramSocket,
    destinationAddress: InetAddress,
    destinationPort: Int,
) {
    thread {
        var destinationReceiverCreated = false
        val buffer = ByteArray(2048)
        val packet = DatagramPacket(buffer, buffer.size)
        try {
            while (true) {
                streamerSocket.receive(packet)
                if (!destinationReceiverCreated) {
                    startDestinationReceiver(
                        streamerSocket,
                        destinationSocket,
                        packet.address,
                        packet.port,
                    )
                    destinationReceiverCreated = true
                }
                packet.address = destinationAddress
                packet.port = destinationPort
                destinationSocket.send(packet)
            }
        } catch (_: Exception) {}
    }
}

private fun startDestinationReceiver(
    streamerSocket: DatagramSocket,
    destinationSocket: DatagramSocket,
    streamerAddress: InetAddress,
    streamerPort: Int,
) {
    thread {
        val buffer = ByteArray(2048)
        val packet = DatagramPacket(buffer, buffer.size)
        try {
            while (true) {
                destinationSocket.receive(packet)
                packet.address = streamerAddress
                packet.port = streamerPort
                streamerSocket.send(packet)
            }
        } catch (_: Exception) {}
    }
}
