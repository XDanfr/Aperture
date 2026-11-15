package me.xdan.aperture.companion

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import me.xdan.aperture.companion.protocol.CompanionProtocol
import me.xdan.aperture.platform.Log
import java.net.Inet4Address

/** NSD, ConnectivityManager and the ACCESS_LOCAL_NETWORK permission (Android 17+). */
class AndroidCompanionPlatform(private val context: Context) : CompanionPlatform {
    private var nsdListener: NsdManager.RegistrationListener? = null

    override val hasLocalNetworkPermission: Boolean
        get() = Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(
            context, CompanionHub.LOCAL_NETWORK_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED

    override val deviceName: String
        get() = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    override fun localIpv4Address(): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = connectivity.activeNetwork ?: return null
        return connectivity.getLinkProperties(network)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    override fun advertise(serviceName: String, port: Int, txt: Map<String, String>) {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = CompanionProtocol.SERVICE_TYPE
            setPort(port)
            txt.forEach { (key, value) -> setAttribute(key, value) }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Advertised ${serviceInfo.serviceName} on $port")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed: $errorCode")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        nsdListener = listener
    }

    override fun stopAdvertising() {
        val listener = nsdListener ?: return
        nsdListener = null
        runCatching { context.getSystemService(NsdManager::class.java)?.unregisterService(listener) }
    }

    private companion object {
        const val TAG = "CompanionHub"
    }
}
