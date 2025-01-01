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
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import com.google.gson.Gson

data class Result(val ok: Boolean)

data class Authentication(val challenge: String, val salt: String)

data class Hello(val apiVersion: String, val authentication: Authentication)

data class Identified(val result: Result)

data class StartTunnelRequest(val address: String, val port: Int)

data class MessageRequest(val startTunnel: StartTunnelRequest?)

data class MessageToClient(val hello: Hello?, val identified: Identified?, val request: MessageRequest?)

data class StartTunnelResponseData(val port: Int)

data class ResponseData(val startTunnel: StartTunnelResponseData?)

data class MessageResponse(val id: Int, val result: Result, val data: ResponseData)

data class Identify(val id: String, val name: String, val authentication: String)

data class MessageToServer(val identify: Identify?, val response: MessageResponse?)

class MainActivity : ComponentActivity() {
    private var webSocket: WebSocket? = null
    private var streamerSocket: DatagramSocket? = null
    private var destinationSocket: DatagramSocket? = null
    private val streamerUrl = "ws://192.168.50.203:7777"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNetwork(NetworkCapabilities.TRANSPORT_CELLULAR, "Cellular")
        requestNetwork(NetworkCapabilities.TRANSPORT_WIFI, "WiFi")
        val okHttpClient = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build()
        val request = Request.Builder().url(streamerUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("Moblink","Websocket opened")
                super.onOpen(webSocket, response)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i("Moblink","Websocket message received: $text")
                super.onMessage(webSocket, text)
                handleMessage(text)
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

    private fun handleMessage(text: String) {
        try {
            val message = Gson().fromJson(text, MessageToClient::class.java)
            Log.i("Moblink", "Got: $message")
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
        val identify = Identify("00B46871-4053-40CE-8181-07A02F82887F", "Android", "1234")
        val message = MessageToServer(identify, null)
        val text = Gson().toJson(message, MessageToServer::class.java)
        webSocket?.send(text)
    }

    private fun handleMessageIdentified(message: Identified) {
    }

    private fun handleMessageRequest(request: MessageRequest) {
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