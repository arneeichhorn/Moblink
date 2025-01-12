package com.eerimoq.moblink

import android.net.Network
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.mutableStateOf
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

class Relay {
    private val okHttpClient = OkHttpClient.Builder().pingInterval(5, TimeUnit.SECONDS).build()
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
    private var started = false
    private var connected = false
    private var wrongPassword = false
    private var onStatusUpdated: ((String) -> Unit)? = null
    private var getBatteryPercentage: (((Int) -> Unit) -> Unit)? = null
    val uiButtonText = mutableStateOf("Start")
    val uiStatus = mutableStateOf("")
    var uiStarted = false

    fun setup(
        relayId: String,
        streamerUrl: String,
        password: String,
        name: String,
        onStatusUpdated: (String) -> Unit,
        getBatteryPercentage: ((Int) -> Unit) -> Unit,
    ) {
        this.onStatusUpdated = onStatusUpdated
        this.getBatteryPercentage = getBatteryPercentage
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        handler?.post {
            this.relayId = relayId
            this.streamerUrl = streamerUrl
            this.password = password
            this.name = name
            updateStatusInternal()
        }
    }

    fun start() {
        handler?.post {
            if (!started) {
                started = true
                startInternal()
            }
        }
    }

    fun stop() {
        handler?.post {
            if (started) {
                started = false
                stopInternal()
            }
        }
    }

    fun updateSettings(relayId: String, streamerUrl: String, password: String, name: String) {
        handler?.post {
            this.relayId = relayId
            this.streamerUrl = streamerUrl
            this.password = password
            this.name = name
            updateStatusInternal()
        }
    }

    fun setCellularNetwork(network: Network?) {
        handler?.post {
            cellularNetwork = network
            updateStatusInternal()
        }
    }

    fun setWiFiNetwork(network: Network?) {
        handler?.post {
            wiFiNetwork = network
            updateStatusInternal()
        }
    }

    private fun startInternal() {
        stopInternal()
        if (!started) {
            return
        }
        val request =
            try {
                Request.Builder().url(streamerUrl).build()
            } catch (e: Exception) {
                Log.i("Moblink", "Failed to build URL: $e")
                return
            }
        webSocket =
            okHttpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        super.onMessage(webSocket, text)
                        handler?.post {
                            if (webSocket === getWebsocket()) {
                                handleMessage(text)
                            }
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        super.onClosed(webSocket, code, reason)
                        handler?.post {
                            if (webSocket === getWebsocket()) {
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
                            if (webSocket === getWebsocket()) {
                                Log.i("Moblink", "Websocket failure $t")
                                reconnectSoon()
                            }
                        }
                    }
                },
            )
    }

    private fun stopInternal() {
        webSocket?.cancel()
        webSocket = null
        connected = false
        wrongPassword = false
        updateStatusInternal()
        streamerSocket?.close()
        destinationSocket?.close()
    }

    private fun updateStatusInternal() {
        val status =
            if (streamerUrl.isEmpty()) {
                "Streamer URL empty"
            } else if (password.isEmpty()) {
                "Password empty"
            } else if (cellularNetwork == null) {
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
        onStatusUpdated?.let { it(status) }
    }

    private fun reconnectSoon() {
        stopInternal()
        handler?.postDelayed({ startInternal() }, 5000)
    }

    private fun getWebsocket(): WebSocket? {
        return webSocket
    }

    private fun handleMessage(text: String) {
        try {
            val message = Gson().fromJson(text, MessageToRelay::class.java)
            if (message.hello != null) {
                handleMessageHello(message.hello)
            } else if (message.identified != null) {
                handleMessageIdentified(message.identified)
            } else if (message.request != null) {
                handleMessageRequest(message.request)
            }
        } catch (e: Exception) {
            Log.i("Moblink", "Message handling failed: $e")
            reconnectSoon()
        }
    }

    private fun handleMessageHello(hello: Hello) {
        var concatenated = "$password${hello.authentication.salt}"
        concatenated = "${base64Encode(calcSha256(concatenated))}${hello.authentication.challenge}"
        val identify = Identify(relayId, name, base64Encode(calcSha256(concatenated)))
        send(MessageToStreamer(identify, null))
    }

    private fun handleMessageIdentified(identified: Identified) {
        if (identified.result.ok != null) {
            connected = true
        } else if (identified.result.wrongPassword != null) {
            wrongPassword = true
        }
        updateStatusInternal()
    }

    private fun handleMessageRequest(request: MessageRequest) {
        if (request.data.startTunnel != null) {
            handleMessageStartTunnelRequest(request.id, request.data.startTunnel)
        } else if (request.data.status != null) {
            handleMessageStatus(request.id)
        }
    }

    private fun handleMessageStartTunnelRequest(id: Int, startTunnel: StartTunnelRequest) {
        streamerSocket?.close()
        destinationSocket?.close()
        if (wiFiNetwork == null || cellularNetwork == null) {
            reconnectSoon()
            return
        }
        streamerSocket = DatagramSocket()
        wiFiNetwork?.bindSocket(streamerSocket)
        destinationSocket = DatagramSocket()
        cellularNetwork?.bindSocket(destinationSocket)
        startStreamerReceiver(
            streamerSocket!!,
            destinationSocket!!,
            InetAddress.getByName(startTunnel.address),
            startTunnel.port,
        )
        val data = ResponseData(StartTunnelResponseData(streamerSocket!!.localPort), null)
        val response = MessageResponse(id, Result(Present(), null), data)
        send(MessageToStreamer(null, response))
    }

    private fun handleMessageStatus(id: Int) {
        getBatteryPercentage?.let {
            it { batteryPercentage ->
                handler?.post {
                    val data = ResponseData(null, StatusResponseData(batteryPercentage))
                    val response = MessageResponse(id, Result(Present(), null), data)
                    send(MessageToStreamer(null, response))
                }
            }
        }
    }

    private fun send(message: MessageToStreamer) {
        webSocket?.send(Gson().toJson(message))
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

private fun base64Encode(input: ByteArray): String {
    return Base64.encodeToString(input, Base64.NO_WRAP)
}

private fun calcSha256(data: String): ByteArray {
    val sha256 = MessageDigest.getInstance("SHA-256")
    return sha256.digest(data.encodeUtf8().toByteArray())
}
