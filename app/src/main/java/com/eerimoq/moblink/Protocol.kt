package com.eerimoq.moblink

val apiVersion = "1.0"

data class Present(val dummy: Boolean? = null)

data class Result(val ok: Present?, val wrongPassword: Present?)

data class Authentication(val challenge: String, val salt: String)

data class Hello(
    val apiVersion: String,
    val id: String,
    val name: String,
    val authentication: Authentication,
)

data class Identified(val result: Result)

data class StartTunnelRequest(val address: String, val port: Int)

data class MessageRequestData(val startTunnel: StartTunnelRequest?, val status: Present?)

data class MessageRequest(val id: Int, val data: MessageRequestData)

data class StartTunnelResponseData(val port: Int)

data class StatusResponseData(val batteryPercentage: Int?)

data class ResponseData(val startTunnel: StartTunnelResponseData?, val status: StatusResponseData?)

data class MessageResponse(val id: Int, val result: Result, val data: ResponseData)

data class Identify(val authentication: String)

data class MessageToClient(
    val hello: Hello?,
    val identified: Identified?,
    val response: MessageResponse?,
)

data class MessageToServer(val identify: Identify?, val request: MessageRequest?)
