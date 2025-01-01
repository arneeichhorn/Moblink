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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNetwork(NetworkCapabilities.TRANSPORT_CELLULAR, "Cellular")
        requestNetwork(NetworkCapabilities.TRANSPORT_WIFI, "WiFi")
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