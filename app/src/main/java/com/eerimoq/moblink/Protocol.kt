package com.eerimoq.moblink

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class Present(val dummy: Boolean? = null)

@Serializable data class Result(val ok: Present? = null, val wrongPassword: Present? = null)

@Serializable data class Authentication(val challenge: String, val salt: String)

@Serializable data class StartTunnelRequest(val address: String, val port: Int)

@Serializable
data class RequestData(val startTunnel: StartTunnelRequest? = null, val status: Present? = null)

@Serializable data class Hello(val apiVersion: String, val authentication: Authentication)

@Serializable data class Identified(val result: Result)

@Serializable data class Request(val id: Int, val data: RequestData)

@Serializable data class StartTunnelResponse(val port: Int)

@Serializable data class StatusResponse(val batteryPercentage: Int? = null)

@Serializable
data class ResponseData(
    val startTunnel: StartTunnelResponse? = null,
    val status: StatusResponse? = null,
)

@Serializable data class Identify(val id: String, val name: String, val authentication: String)

@Serializable data class Response(val id: Int, val result: Result, val data: ResponseData)

@Serializable
data class MessageToRelay(
    val hello: Hello? = null,
    val identified: Identified? = null,
    val request: Request? = null,
) {
    companion object {
        fun fromJson(text: String): MessageToRelay {
            return Json.decodeFromString(text)
        }
    }
}

@Serializable
data class MessageToStreamer(val identify: Identify? = null, val response: Response? = null) {
    fun toJson(): String {
        return Json.encodeToString(this)
    }
}
