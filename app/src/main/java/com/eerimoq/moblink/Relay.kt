package com.eerimoq.moblink

import android.net.Network
import android.os.Handler
import android.os.HandlerThread
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import okhttp3.WebSocket

class Relay {
    var webSocket: WebSocket? = null
    var wiFiNetwork: Network? = null
    var cellularNetwork: Network? = null
    var streamerSocket: DatagramSocket? = null
    var destinationSocket: DatagramSocket? = null
    var relayId = ""
    var streamerUrl = ""
    var password = ""
    var name = ""
    val handlerThread = HandlerThread("Something")
    var handler: Handler? = null
    val okHttpClient = OkHttpClient.Builder().pingInterval(5, TimeUnit.SECONDS).build()
    var started = false
    var connected = false
    var wrongPassword = false
}

fun startStreamerReceiver(
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
