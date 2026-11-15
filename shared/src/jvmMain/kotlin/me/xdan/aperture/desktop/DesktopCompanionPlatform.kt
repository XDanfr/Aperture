package me.xdan.aperture.desktop

import me.xdan.aperture.companion.CompanionPlatform
import me.xdan.aperture.companion.protocol.CompanionProtocol
import me.xdan.aperture.platform.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/** Advertises the companion server with JmDNS, which the phone app's NSD discovery finds. */
class DesktopCompanionPlatform : CompanionPlatform {
    private var jmdns: JmDNS? = null

    override val hasLocalNetworkPermission: Boolean = true

    override val deviceName: String by lazy {
        runCatching { InetAddress.getLocalHost().hostName.removeSuffix(".local") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "Aperture desktop"
    }

    override val deviceKind: String by lazy(::detectDesktopDeviceKind)

    override fun localIpv4Address(): String? = lanAddress()?.hostAddress

    private fun lanAddress(): Inet4Address? = runCatching {
        NetworkInterface.networkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .sortedByDescending { it.isSiteLocalAddress }
            .firstOrNull()
    }.getOrNull()

    @Synchronized
    override fun advertise(serviceName: String, port: Int, txt: Map<String, String>) {
        stopAdvertising()
        val address = lanAddress() ?: return
        Thread({
            runCatching {
                val dns = JmDNS.create(address, deviceName)
                dns.registerService(ServiceInfo.create("${CompanionProtocol.SERVICE_TYPE}.local.", serviceName, port, 0, 0, txt))
                synchronized(this) { jmdns = dns }
                Log.i(TAG, "Advertised $serviceName on $port")
            }.onFailure { Log.w(TAG, "mDNS unavailable", it) }
        }, "companion-mdns").apply { isDaemon = true }.start()
    }

    @Synchronized
    override fun stopAdvertising() {
        val dns = jmdns ?: return
        jmdns = null
        Thread({ runCatching { dns.unregisterAllServices(); dns.close() } }, "companion-mdns-stop")
            .apply { isDaemon = true }
            .start()
    }

    private companion object {
        const val TAG = "CompanionHub"
    }
}
