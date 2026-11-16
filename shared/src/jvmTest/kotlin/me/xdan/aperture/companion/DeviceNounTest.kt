package me.xdan.aperture.companion

import me.xdan.aperture.companion.protocol.CompanionProtocol
import me.xdan.aperture.companion.protocol.DeviceKind
import me.xdan.aperture.companion.protocol.DeviceOs
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceNounTest {
    @Test
    fun namesTheDeviceTheWayPeopleDo() {
        assertEquals("TV", DeviceKind.noun(DeviceKind.TV, DeviceOs.ANDROID))
        assertEquals("TV", DeviceKind.noun(DeviceKind.TV, ""))
        assertEquals("Mac", DeviceKind.noun(DeviceKind.LAPTOP, DeviceOs.MAC))
        assertEquals("PC", DeviceKind.noun(DeviceKind.DESKTOP, DeviceOs.WINDOWS))
        assertEquals("computer", DeviceKind.noun(DeviceKind.LAPTOP, DeviceOs.LINUX))
        // An older desktop version sends no OS.
        assertEquals("computer", DeviceKind.noun(DeviceKind.DESKTOP, ""))
    }

    @Test
    fun theOsTravelsInThePairingCode() {
        val uri = CompanionProtocol.pairingUri("192.168.1.20", 47651, "123456", "tv1", "Living room", DeviceKind.LAPTOP, DeviceOs.MAC)
        val invite = CompanionProtocol.parsePairingUri(uri)!!
        assertEquals(DeviceKind.LAPTOP, invite.deviceKind)
        assertEquals(DeviceOs.MAC, invite.deviceOs)
        // Codes from older versions have no OS.
        val old = CompanionProtocol.parsePairingUri("aperture://pair?h=192.168.1.20&p=47651&c=123456&id=tv1&n=TV&k=tv")!!
        assertEquals("", old.deviceOs)
    }
}
