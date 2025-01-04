package com.eerimoq.moblink

data class Empty(val dummy: Boolean?)

data class Result(val ok: Empty?, val wrongPassword: Empty?)

data class Authentication(val challenge: String, val salt: String)

data class Hello(val apiVersion: String, val authentication: Authentication)

data class Identified(val result: Result)

data class StartTunnelRequest(val address: String, val port: Int)

data class MessageRequestData(val startTunnel: StartTunnelRequest?)

data class MessageRequest(val id: Int, val data: MessageRequestData)

data class StartTunnelResponseData(val port: Int)

data class ResponseData(val startTunnel: StartTunnelResponseData?)

data class MessageResponse(val id: Int, val result: Result, val data: ResponseData)

data class Identify(val id: String, val name: String, val authentication: String)

data class MessageToClient(
    val hello: Hello?,
    val identified: Identified?,
    val request: MessageRequest?,
)

data class MessageToServer(val identify: Identify?, val response: MessageResponse?)
