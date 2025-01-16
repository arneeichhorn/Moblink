package com.eerimoq.moblink

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

const val serviceType = "_moblink._tcp"

class Scanner(
    private val nsdManager: NsdManager,
) {
    fun setup() {
        nsdManager.discoverServices(
            serviceType,
            NsdManager.PROTOCOL_DNS_SD,
            createNsgListenerCallback(),
        )
    }

    private fun createNsgListenerCallback(): NsdManager.DiscoveryListener {
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
                log("Resolved service ${service.serviceName}: ${service.host}:${service.port}")
            }
        }
    }
}
