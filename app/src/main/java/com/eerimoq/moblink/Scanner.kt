package com.eerimoq.moblink

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.Inet4Address

const val serviceType = "_moblink._tcp"

class Scanner(private val nsdManager: NsdManager, private val onFound: (String) -> Unit) {
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        log("Scanner start")
        listener = createNsdListenerCallback()
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        log("Scanner stop")
        if (listener != null) {
            nsdManager.stopServiceDiscovery(listener)
            listener = null
        }
    }

    private fun createNsdListenerCallback(): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                log("Service found: ${service.serviceName}")
                nsdManager.resolveService(service, createResolveCallback())
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                log("Service lost ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
        }
    }

    private fun createResolveCallback(): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

            override fun onServiceResolved(service: NsdServiceInfo) {
                val address =
                    if (service.host is Inet4Address) {
                        service.host.hostAddress
                    } else {
                        "[${service.host.hostAddress}]"
                    }
                val url = "ws://${address}:${service.port}"
                log("Resolved service ${service.serviceName}: $url")
                onFound(url)
            }
        }
    }
}
