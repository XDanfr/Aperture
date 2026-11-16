package me.xdan.aperture.companion

/** The OS services the companion server needs: addressing, service discovery and identity. */
interface CompanionPlatform {
    /** False until the user grants local network access, where the OS requires it. */
    val hasLocalNetworkPermission: Boolean

    /** This device's name as people know it on their network. */
    val deviceName: String

    /** A [me.xdan.aperture.companion.protocol.DeviceKind], so the phone shows a fitting icon. */
    val deviceKind: String get() = me.xdan.aperture.companion.protocol.DeviceKind.TV
    /** A [me.xdan.aperture.companion.protocol.DeviceOs]. */
    val deviceOs: String get() = me.xdan.aperture.companion.protocol.DeviceOs.ANDROID

    /** The LAN IPv4 address phones should connect to, if any. */
    fun localIpv4Address(): String?

    /** Advertise the companion service over DNS-SD. */
    fun advertise(serviceName: String, port: Int, txt: Map<String, String>)

    /** Stop advertising. Safe to call when nothing is advertised. */
    fun stopAdvertising()
}
