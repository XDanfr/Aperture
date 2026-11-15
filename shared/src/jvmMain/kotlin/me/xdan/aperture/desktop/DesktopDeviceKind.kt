package me.xdan.aperture.desktop

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import me.xdan.aperture.companion.protocol.DeviceKind
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Laptop or desktop, judged by whether the computer has a built-in battery. The phone app uses it
 * to pick an icon, so a wrong guess only costs a slightly different picture.
 */
internal fun detectDesktopDeviceKind(): String =
    if (runCatching(::hasInternalBattery).getOrDefault(false)) DeviceKind.LAPTOP else DeviceKind.DESKTOP

private fun hasInternalBattery(): Boolean = when {
    Platform.isMac() -> {
        val process = ProcessBuilder("pmset", "-g", "batt").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor(2, TimeUnit.SECONDS)
        "InternalBattery" in output
    }
    Platform.isWindows() -> {
        // SYSTEM_POWER_STATUS: BatteryFlag (byte 1) is 128 when the system has no battery.
        val status = Memory(12).apply { clear() }
        Kernel32Power.INSTANCE.GetSystemPowerStatus(status) &&
            status.getByte(1).toInt() and 0xFF != NO_SYSTEM_BATTERY
    }
    else -> File("/sys/class/power_supply").listFiles().orEmpty().any { supply ->
        // Peripherals (mice, headsets) report scope "Device"; the laptop's own battery doesn't.
        File(supply, "type").readTextOrNull() == "Battery" && File(supply, "scope").readTextOrNull() != "Device"
    }
}

private fun File.readTextOrNull(): String? = runCatching { readText().trim() }.getOrNull()

private const val NO_SYSTEM_BATTERY = 128

@Suppress("FunctionName")
private interface Kernel32Power : Library {
    fun GetSystemPowerStatus(status: Pointer): Boolean

    companion object {
        val INSTANCE: Kernel32Power by lazy { Native.load("kernel32", Kernel32Power::class.java) }
    }
}
