package com.eerimoq.moblink

import kotlinx.serialization.Serializable

@Serializable data class Present(val dummy: Boolean? = null)

@Serializable data class Result(val ok: Present? = null, val wrongPassword: Present? = null)

@Serializable data class Authentication(val challenge: String, val salt: String)

@Serializable data class Hello(val apiVersion: String, val authentication: Authentication)

@Serializable data class Identified(val result: Result)

@Serializable data class StartTunnelRequest(val address: String, val port: Int)

@Serializable
data class MessageRequestData(
    val startTunnel: StartTunnelRequest? = null,
    val status: Present? = null,
)

@Serializable data class MessageRequest(val id: Int, val data: MessageRequestData)

@Serializable data class StartTunnelResponseData(val port: Int)

@Serializable data class StatusResponseData(val batteryPercentage: Int? = null)

@Serializable
data class ResponseData(
    val startTunnel: StartTunnelResponseData? = null,
    val status: StatusResponseData? = null,
)

@Serializable data class MessageResponse(val id: Int, val result: Result, val data: ResponseData)

@Serializable data class Identify(val id: String, val name: String, val authentication: String)

@Serializable
data class MessageToRelay(
    val hello: Hello? = null,
    val identified: Identified? = null,
    val request: MessageRequest? = null,
)

@Serializable
data class MessageToStreamer(val identify: Identify? = null, val response: MessageResponse? = null)
