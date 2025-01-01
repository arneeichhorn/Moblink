package com.example.moblink

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import com.example.moblink.ui.theme.MoblinkTheme
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread
import okhttp3.WebSocket
import okhttp3.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private var webSocket: WebSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNetwork(NetworkCapabilities.TRANSPORT_CELLULAR, "Cellular")
        requestNetwork(NetworkCapabilities.TRANSPORT_WIFI, "WiFi")
        val okHttpClient = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("ws://192.168.50.203:7777").build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("Moblink","Websocket opened")
                super.onOpen(webSocket, response)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i("Moblink","Websocket message received: $text")
                super.onMessage(webSocket, text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("Moblink","Websocket closed $reason (code $code)")
                super.onClosed(webSocket, code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.i("Moblink","Websocket failure $t")
                super.onFailure(webSocket, t, response)
            }
        })
        setContent {
            MoblinkTheme {
                Surface(color = Color.Cyan) {
                    Text("Moblink")
                }
            }
        }
    }

    private fun requestNetwork(transportType: Int, type: String) {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(transportType)
            .build()
        val networkCallback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.i("Moblink", "$type available")
                sendDatagramOverNetwork(network, "mys-lang.org", 7777, type)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.i("Moblink", "$type lost")
            }
        }
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.requestNetwork(networkRequest, networkCallback)
    }

    private fun sendDatagramOverNetwork(network: Network, host: String, port: Int, message: String) {
        thread {
            val socket = DatagramSocket()
            network.bindSocket(socket)
            while (true) {
                try {
                    val buffer = message.toByteArray()
                    val address = InetAddress.getByName(host)
                    val packet = DatagramPacket(buffer, buffer.size, address, port)
                    socket.send(packet)
                } catch (e: Exception) {
                    Log.e("Moblink", "Error sending datagram", e)
                }
                Thread.sleep(1000)
            }
        }
    }
}