package com.eerimoq.moblink

import android.util.Log
import java.net.InetSocketAddress
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer

class WebServer(address: InetSocketAddress?) : WebSocketServer(address) {
    override fun onStart() {
        Log.i("Moblink", "Web server started successfully")
    }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        Log.i("Moblink", "Open $handshake")
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        Log.i("Moblink", "Close $reason")
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        Log.i("Moblink", "Message $message")
    }

    override fun onError(conn: WebSocket?, ex: java.lang.Exception?) {
        Log.i("Moblink", "Error $ex")
    }
}
