package com.eerimoq.moblink

import android.net.Network
import android.os.Handler
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.concurrent.thread
import okio.ByteString.Companion.encodeUtf8
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer

class Client(private val relay: Relay, val webSocket: WebSocket) {
    private var streamerSocket: DatagramSocket? = null
    private var destinationSocket: DatagramSocket? = null
    private var challenge = ""
    private var salt = ""
    private var identified = false

    fun start() {
        challenge = randomString()
        salt = randomString()
        val authentication = Authentication(challenge, salt)
        send(
            MessageToClient(
                Hello(apiVersion, relay.relayId, relay.name, authentication),
                null,
                null,
            )
        )
        identified = false
    }

    fun stop() {
        webSocket.close()
        streamerSocket?.close()
        destinationSocket?.close()
    }

    fun handleMessage(text: String) {
        try {
            val message = Gson().fromJson(text, MessageToServer::class.java)
            if (message.identify != null) {
                handleMessageIdentify(message.identify)
            } else if (message.request != null) {
                handleMessageRequest(message.request)
            }
        } catch (e: Exception) {
            Log.i("Moblink", "Message handling failed: $e")
        }
    }

    private fun handleMessageIdentify(identify: Identify) {
        val identified =
            if (identify.authentication == hashPassword(challenge, salt, relay.password)) {
                identified = true
                Identified(Result(Present(), null))
            } else {
                Identified(Result(null, Present()))
            }
        send(MessageToClient(null, identified, null))
    }

    private fun handleMessageRequest(request: MessageRequest) {
        if (!identified) {
            return
        }
        if (request.data.startTunnel != null) {
            handleMessageStartTunnelRequest(request.id, request.data.startTunnel)
        } else if (request.data.status != null) {
            handleMessageStatus(request.id)
        }
    }

    private fun handleMessageStartTunnelRequest(id: Int, startTunnel: StartTunnelRequest) {
        streamerSocket?.close()
        destinationSocket?.close()
        if (relay.wifiNetwork == null || relay.cellNetwork == null) {
            return
        }
        streamerSocket = DatagramSocket()
        relay.wifiNetwork?.bindSocket(streamerSocket)
        destinationSocket = DatagramSocket()
        relay.cellNetwork?.bindSocket(destinationSocket)
        startStreamerReceiver(
            streamerSocket!!,
            destinationSocket!!,
            InetAddress.getByName(startTunnel.address),
            startTunnel.port,
        )
        val data = ResponseData(StartTunnelResponseData(streamerSocket!!.localPort), null)
        val response = MessageResponse(id, Result(Present(), null), data)
        send(MessageToClient(null, null, response))
    }

    private fun handleMessageStatus(id: Int) {
        relay.getBatteryPercentage?.let {
            it { batteryPercentage ->
                relay.handler.post {
                    val data = ResponseData(null, StatusResponseData(batteryPercentage))
                    val response = MessageResponse(id, Result(Present(), null), data)
                    send(MessageToClient(null, null, response))
                }
            }
        }
    }

    private fun send(message: MessageToClient) {
        webSocket.send(Gson().toJson(message))
    }
}

class Relay(val handler: Handler, address: InetSocketAddress?) : WebSocketServer(address) {
    var wifiNetwork: Network? = null
    var cellNetwork: Network? = null
    var relayId = ""
    var password = ""
    var name = ""
    private var started = false
    private var webServerRunning = false
    private var onStatusUpdated: ((String) -> Unit)? = null
    var getBatteryPercentage: (((Int) -> Unit) -> Unit)? = null
    private var onError: (() -> Unit)? = null
    private var clients = mutableListOf<Client>()

    fun setup(
        relayId: String,
        name: String,
        password: String,
        onError: () -> Unit,
        onStatusUpdated: (String) -> Unit,
        getBatteryPercentage: ((Int) -> Unit) -> Unit,
    ) {
        this.onError = onError
        this.onStatusUpdated = onStatusUpdated
        this.getBatteryPercentage = getBatteryPercentage
        handler.post {
            this.relayId = relayId
            this.name = name
            this.password = password
            updateStatusInternal()
        }
    }

    override fun start() {
        handler.post {
            if (!started) {
                started = true
                startInternal()
            }
        }
    }

    override fun stop() {
        handler.post {
            if (started) {
                started = false
                stopInternal()
            }
        }
    }

    fun updateSettings(relayId: String, name: String, password: String) {
        handler.post {
            this.relayId = relayId
            this.name = name
            this.password = password
            updateStatusInternal()
        }
    }

    fun setCellularNetwork(network: Network?) {
        handler.post {
            cellNetwork = network
            updateStatusInternal()
        }
    }

    fun setWiFiNetwork(network: Network?) {
        handler.post {
            wifiNetwork = network
            updateStatusInternal()
        }
    }

    private fun startInternal() {
        if (!started) {
            return
        }
        try {
            super.start()
        } catch (e: Exception) {
            Log.i("Moblink", "Web server start failed with error: $e")
        }
        updateStatusInternal()
    }

    private fun stopInternal() {
        for (client in clients) {
            client.stop()
        }
        super.stop()
        updateStatusInternal()
    }

    private fun updateStatusInternal() {
        val status =
            if (!started) {
                "Not started"
            } else if (!webServerRunning) {
                "Starting..."
            } else if (cellNetwork == null) {
                "Waiting for Mobile data"
            } else if (wifiNetwork == null) {
                "Waiting for WiFi"
            } else {
                if (clients.count() == 1) {
                    "1 streamer connected"
                } else {
                    "${clients.count()} streamers connected"
                }
            }
        onStatusUpdated?.let { it(status) }
    }

    override fun onStart() {
        handler.post {
            Log.i("Moblink", "Web server started")
            webServerRunning = true
            updateStatusInternal()
        }
    }

    override fun onOpen(webSocket: WebSocket?, handshake: ClientHandshake?) {
        handler.post {
            Log.i("Moblink", "Open $handshake")
            val client = Client(this, webSocket!!)
            client.start()
            clients.add(client)
            updateStatusInternal()
        }
    }

    override fun onClose(webSocket: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        handler.post {
            Log.i("Moblink", "Close $reason")
            getClient(webSocket!!)?.also {
                it.stop()
                clients.remove(it)
            }
            updateStatusInternal()
        }
    }

    override fun onError(webSocket: WebSocket?, ex: java.lang.Exception?) {
        handler.post {
            Log.i("Moblink", "Web server error: $ex")
            getClient(webSocket)?.also {
                it.stop()
                clients.remove(it)
            }
            updateStatusInternal()
            if (webSocket == null) {
                webServerRunning = false
                updateStatusInternal()
                handler.postDelayed({ onError?.let { it() } }, 10000)
            }
        }
    }

    override fun onMessage(webSocket: WebSocket?, message: String?) {
        handler.post { getClient(webSocket!!)?.also { it.handleMessage(message!!) } }
    }

    private fun getClient(webSocket: WebSocket?): Client? {
        return try {
            clients.first { client -> webSocket === client.webSocket }
        } catch (_: Exception) {
            null
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

private fun base64Encode(input: ByteArray): String {
    return Base64.encodeToString(input, Base64.NO_WRAP)
}

private fun calcSha256(data: String): ByteArray {
    val sha256 = MessageDigest.getInstance("SHA-256")
    return sha256.digest(data.encodeUtf8().toByteArray())
}

private fun hashPassword(challenge: String, salt: String, password: String): String {
    var concatenated = "${password}${salt}"
    concatenated = "${base64Encode(calcSha256(concatenated))}${challenge}"
    return base64Encode(calcSha256(concatenated))
}

fun randomString(): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..64).map { allowedChars.random() }.joinToString("")
}
