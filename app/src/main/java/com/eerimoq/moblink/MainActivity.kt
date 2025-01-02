package com.eerimoq.moblink

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.eerimoq.moblink.ui.theme.MoblinkTheme
import com.google.gson.Gson
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.encodeUtf8
import okio.IOException

data class Empty(val dummy: Boolean?)

data class Result(val ok: Empty?, val wrongPassword: Empty?)

data class Authentication(val challenge: String, val salt: String)

data class Hello(val apiVersion: String, val authentication: Authentication)

data class Identified(val result: Result)

data class StartTunnelRequest(val address: String, val port: Int)

data class MessageRequestData(val startTunnel: StartTunnelRequest?)

data class MessageRequest(val id: Int, val data: MessageRequestData)

data class MessageToClient(
    val hello: Hello?,
    val identified: Identified?,
    val request: MessageRequest?,
)

data class StartTunnelResponseData(val port: Int)

data class ResponseData(val startTunnel: StartTunnelResponseData?)

data class MessageResponse(val id: Int, val result: Result, val data: ResponseData)

data class Identify(val id: String, val name: String, val authentication: String)

data class MessageToServer(val identify: Identify?, val response: MessageResponse?)

class MainActivity : ComponentActivity() {
    private var webSocket: WebSocket? = null
    private var wiFiNetwork: Network? = null
    private var cellularNetwork: Network? = null
    private var streamerSocket: DatagramSocket? = null
    private var destinationSocket: DatagramSocket? = null
    private var streamerUrl = ""
    private var password = ""
    private var name = "Relay"
    private var relayId = ""
    private val handlerThread = HandlerThread("Something")
    private var handler: Handler? = null
    private val okHttpClient = OkHttpClient.Builder().pingInterval(5, TimeUnit.SECONDS).build()
    private var started = false
    private var version = "?"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setup()
        setContent {
            MoblinkTheme {
                Main(
                    streamerUrl,
                    password,
                    name,
                    version,
                    { streamerUrl: String, password: String -> start(streamerUrl, password) },
                    { stop() },
                    { streamerUrl: String, password: String, name: String ->
                        this.streamerUrl = streamerUrl
                        this.password = password
                        this.name = name
                        saveSettings(streamerUrl, password, relayId, name)
                    },
                )
            }
        }
    }

    private fun setup() {
        requestNetwork(NetworkCapabilities.TRANSPORT_CELLULAR, "Cellular")
        requestNetwork(NetworkCapabilities.TRANSPORT_WIFI, "WiFi")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        val settings = getSharedPreferences("settings", Context.MODE_PRIVATE)
        streamerUrl = settings.getString("streamerUrl", "") ?: ""
        password = settings.getString("password", "") ?: ""
        val uuid = UUID.randomUUID().toString()
        relayId = settings.getString("relayId", uuid) ?: uuid
        name = settings.getString("name", "Relay") ?: "Relay"
        saveSettings(streamerUrl, password, relayId, name)
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            version = packageInfo.versionName
        } catch (_: Exception) {}
    }

    private fun saveSettings(streamerUrl: String, password: String, relayId: String, name: String) {
        val settings = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val editor = settings.edit()
        editor.putString("streamerUrl", streamerUrl)
        editor.putString("password", password)
        editor.putString("relayId", relayId)
        editor.putString("name", name)
        editor.apply()
    }

    private fun start(streamerUrl: String, password: String) {
        handler?.post {
            Log.i("Moblink", "Start")
            if (started) {
                return@post
            }
            started = true
            this.streamerUrl = streamerUrl
            this.password = password
            startInternal()
        }
    }

    private fun stop() {
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

    private fun getWebSocket(): WebSocket? {
        return webSocket
    }

    private fun stopInternal() {
        webSocket?.cancel()
        streamerSocket?.close()
        destinationSocket?.close()
    }

    private fun reconnectSoon() {
        Log.i("Moblink", "Reconnect soon")
        stopInternal()
        handler?.postDelayed(
            {
                Log.i("Moblink", "Reconnect")
                startInternal()
            },
            5000,
        )
    }

    private fun handleMessage(text: String) {
        try {
            val message = Gson().fromJson(text, MessageToClient::class.java)
            if (message.hello != null) {
                handleMessageHello(message.hello)
            } else if (message.identified != null) {
                handleMessageIdentified()
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

    private fun handleMessageIdentified() {}

    private fun handleMessageRequest(request: MessageRequest) {
        if (request.data.startTunnel != null) {
            handleMessageStartTunnelRequest(request.id, request.data.startTunnel)
        }
    }

    private fun handleMessageStartTunnelRequest(id: Int, startTunnel: StartTunnelRequest) {
        if (!setupStreamerSocket()) {
            return
        }
        if (!setupDestinationSocket()) {
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
            Log.i("Moblink", "Cellular networks not ready")
            return false
        }
        destinationSocket = DatagramSocket()
        cellularNetwork?.bindSocket(destinationSocket)
        return true
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
            while (true) {
                try {
                    streamerSocket.receive(packet)
                } catch (e: IOException) {
                    break
                }
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
            while (true) {
                try {
                    destinationSocket.receive(packet)
                } catch (e: IOException) {
                    break
                }
                packet.address = streamerAddress
                packet.port = streamerPort
                streamerSocket.send(packet)
            }
        }
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
                    }
                }
            }
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.requestNetwork(networkRequest, networkCallback)
    }
}

@Composable
fun Main(
    streamerUrl: String,
    password: String,
    name: String,
    version: String,
    onStart: (streamerUrl: String, password: String) -> Unit,
    onStop: () -> Unit,
    saveSettings: (streamerUrl: String, password: String, name: String) -> Unit,
) {
    var streamerUrlInput by remember { mutableStateOf(streamerUrl) }
    var passwordInput by remember { mutableStateOf(password) }
    var nameInput by remember { mutableStateOf(name) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Moblink", fontSize = 30.sp)
        OutlinedTextField(
            value = streamerUrlInput,
            onValueChange = {
                streamerUrlInput = it
                saveSettings(streamerUrlInput, passwordInput, nameInput)
            },
            label = { Text("Streamer URL") },
        )
        OutlinedTextField(
            value = passwordInput,
            onValueChange = {
                passwordInput = it
                saveSettings(streamerUrlInput, passwordInput, nameInput)
            },
            label = { Text("Password") },
        )
        OutlinedTextField(
            value = nameInput,
            onValueChange = {
                nameInput = it
                saveSettings(streamerUrlInput, passwordInput, nameInput)
            },
            label = { Text("Name") },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { onStart(streamerUrlInput, passwordInput) }) { Text("Start") }
            Button(onClick = { onStop() }) { Text("Stop") }
        }
        Text("Version $version")
    }
}
