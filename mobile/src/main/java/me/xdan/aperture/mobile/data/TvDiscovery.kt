package me.xdan.aperture.mobile.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import me.xdan.aperture.companion.protocol.CompanionProtocol
import java.net.Inet4Address
import java.util.concurrent.Executor

data class DiscoveredTv(
    val serviceName: String,
    val name: String,
    val tvId: String,
    val host: String,
    val port: Int
) {
    val baseUrl: String get() = "http://${if (':' in host) "[$host]" else host}:$port"
}

/** mDNS discovery of Aperture TVs on the current network. */
class TvDiscovery(private val context: Context) {

    /** emits the full list of TVs found so far; stops discovering when collection ends. */
    fun discover(): Flow<List<DiscoveredTv>> = callbackFlow {
        val nsd = context.getSystemService(NsdManager::class.java)
        if (nsd == null) {
            close()
            return@callbackFlow
        }
        val found = linkedMapOf<String, DiscoveredTv>()
        // run callbacks inline. nsd still posts one after we unregister, which crashed
        // when this was a thread pool that had already been shut down.
        val executor = Executor(Runnable::run)
        val infoCallbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()

        fun publish(info: NsdServiceInfo) {
            val tv = info.toDiscoveredTv() ?: return
            synchronized(found) {
                found[info.serviceName] = tv
                trySend(found.values.toList())
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Discovery failed: $errorCode")
                close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= 34) {
                    val callback = object : NsdManager.ServiceInfoCallback {
                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                        override fun onServiceUpdated(serviceInfo: NsdServiceInfo) = publish(serviceInfo)
                        override fun onServiceLost() {
                            synchronized(found) {
                                found.remove(serviceInfo.serviceName)
                                trySend(found.values.toList())
                            }
                        }
                        override fun onServiceInfoCallbackUnregistered() = Unit
                    }
                    synchronized(infoCallbacks) { infoCallbacks[serviceInfo.serviceName] = callback }
                    runCatching { nsd.registerServiceInfoCallback(serviceInfo, executor, callback) }
                } else {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) = publish(serviceInfo)
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(found) {
                    found.remove(serviceInfo.serviceName)
                    trySend(found.values.toList())
                }
            }
        }

        trySend(emptyList())
        nsd.discoverServices(CompanionProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(discoveryListener) }
            if (Build.VERSION.SDK_INT >= 34) {
                synchronized(infoCallbacks) {
                    infoCallbacks.values.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
                }
            }
        }
    }

    private fun NsdServiceInfo.toDiscoveredTv(): DiscoveredTv? {
        val address = if (Build.VERSION.SDK_INT >= 34) {
            hostAddresses.firstOrNull { it is Inet4Address } ?: hostAddresses.firstOrNull()
        } else {
            @Suppress("DEPRECATION") host
        } ?: return null
        val tvId = attributes[CompanionProtocol.TXT_TV_ID]?.let(::String).orEmpty()
        return DiscoveredTv(
            serviceName = serviceName,
            name = serviceName.removePrefix("Aperture · ").ifBlank { "Aperture TV" },
            tvId = tvId,
            host = address.hostAddress ?: return null,
            port = port
        )
    }

    private companion object {
        const val TAG = "TvDiscovery"
    }
}
