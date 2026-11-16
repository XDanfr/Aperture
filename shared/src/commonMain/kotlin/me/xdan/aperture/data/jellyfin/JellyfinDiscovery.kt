package me.xdan.aperture.data.jellyfin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.xdan.aperture.platform.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * Finds Jellyfin servers with the "who is JellyfinServer?" UDP broadcast on port 7359.
 *
 * The SDK only sends to 255.255.255.255, which many Android devices drop. This also sends to each
 * interface's own broadcast address (e.g. 192.168.1.255), which Jellyfin answers just the same.
 */
internal object JellyfinDiscovery {
    private const val PORT = 7359
    private const val MESSAGE = "who is JellyfinServer?"
    private const val RECEIVE_TIMEOUT_MS = 800
    private const val MAX_REPLIES = 32

    @Serializable
    private data class Reply(
        @SerialName("Address") val address: String? = null,
        @SerialName("Id") val id: String? = null,
        @SerialName("Name") val name: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun discover(rounds: Int = 3): Flow<JellyfinAccounts.DiscoveredServer> = flow {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = RECEIVE_TIMEOUT_MS
            val targets = broadcastAddresses()
            val seen = mutableSetOf<String>()
            // A few rounds, since UDP replies (or the broadcast itself) can be lost.
            repeat(rounds) {
                targets.forEach { address ->
                    runCatching {
                        val bytes = MESSAGE.toByteArray()
                        socket.send(DatagramPacket(bytes, bytes.size, address, PORT))
                    }.onFailure { Log.w(TAG, "Couldn't broadcast to $address: ${it.message}") }
                }
                var replies = 0
                while (replies < MAX_REPLIES && currentCoroutineContext().isActive) {
                    val buffer = ByteArray(2048)
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                    replies++
                    val reply = runCatching {
                        json.decodeFromString<Reply>(String(packet.data, 0, packet.length))
                    }.getOrNull() ?: continue
                    val id = reply.id ?: continue
                    if (!seen.add(id)) continue
                    // Prefer the server's advertised address; fall back to where the reply came from.
                    val address = reply.address?.takeIf { it.isNotBlank() }
                        ?: "http://${packet.address.hostAddress}:8096"
                    emit(JellyfinAccounts.DiscoveredServer(id, reply.name ?: "Jellyfin", JellyfinAccounts.normalise(address)))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** The limited broadcast plus every up, non-loopback interface's own broadcast address. */
    private fun broadcastAddresses(): List<InetAddress> {
        val subnets = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
        }.getOrDefault(emptyList())
        return (subnets + InetAddress.getByName("255.255.255.255")).distinct()
    }

    private const val TAG = "JellyfinDiscovery"
}
