package me.xdan.aperture.companion

import me.xdan.aperture.platform.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * small HTTP/1.1 server for the phone companion. one request per connection,
 * except event streams, which stay open. saves pulling in a server library.
 */
internal class CompanionHttpServer(
    private val scope: CoroutineScope,
    private val handler: Handler
) {
    fun interface Handler {
        suspend fun handle(request: Request, response: ResponseWriter)
    }

    data class Request(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
        val remoteAddress: String
    ) {
        val bearerToken: String?
            get() = headers["authorization"]?.removePrefix("Bearer ")?.trim()
    }

    class ResponseWriter internal constructor(private val socket: Socket) {
        private val out: OutputStream = socket.getOutputStream()
        var committed = false
            private set

        fun send(status: Int, body: String, contentType: String = "application/json; charset=utf-8") {
            val bytes = body.toByteArray()
            writeHead(status, "Content-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n")
            out.write(bytes)
            out.flush()
        }

        fun sendBytes(status: Int, bytes: ByteArray, contentType: String) {
            writeHead(status, "Content-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nCache-Control: max-age=3600\r\nConnection: close\r\n")
            out.write(bytes)
            out.flush()
        }

        /** starts an event stream. returns a writer that throws [IOException] once the phone disconnects. */
        fun startEventStream(): EventStream {
            writeHead(200, "Content-Type: text/event-stream\r\nCache-Control: no-cache\r\nConnection: keep-alive\r\n")
            out.flush()
            socket.soTimeout = 0
            return EventStream(out)
        }

        private fun writeHead(status: Int, headers: String) {
            check(!committed)
            committed = true
            out.write("HTTP/1.1 $status ${reason(status)}\r\n$headers\r\n".toByteArray())
        }
    }

    class EventStream internal constructor(private val out: OutputStream) {
        @Synchronized
        fun send(event: String, data: String) {
            out.write("event: $event\ndata: $data\n\n".toByteArray())
            out.flush()
        }

        @Synchronized
        fun heartbeat() {
            out.write(": ping\n\n".toByteArray())
            out.flush()
        }
    }

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val clients = mutableSetOf<Socket>()

    val port: Int get() = serverSocket?.localPort ?: -1

    /** binds the first free port from [preferredPort]; falls back to an ephemeral port. */
    fun start(preferredPort: Int, attempts: Int): Int {
        serverSocket?.let { return it.localPort }
        val socket = (0 until attempts).firstNotNullOfOrNull { offset ->
            runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(preferredPort + offset))
                }
            }.getOrNull()
        } ?: ServerSocket(0)
        serverSocket = socket
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val client = try {
                    socket.accept()
                } catch (_: SocketException) {
                    break
                }
                synchronized(clients) { clients += client }
                launch { serve(client) }
            }
        }
        return socket.localPort
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        synchronized(clients) {
            clients.forEach { runCatching { it.close() } }
            clients.clear()
        }
    }

    private suspend fun serve(socket: Socket) {
        try {
            socket.soTimeout = 10_000
            socket.tcpNoDelay = true
            val request = readRequest(socket) ?: return
            val response = ResponseWriter(socket)
            try {
                handler.handle(request, response)
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Request failed: ${request.method} ${request.path}", e)
                if (!response.committed) response.send(500, """{"error":"internal"}""")
            }
            if (!response.committed) response.send(404, """{"error":"not_found"}""")
        } catch (_: IOException) {
            // the phone went away; nothing to report.
        } finally {
            synchronized(clients) { clients -= socket }
            runCatching { socket.close() }
        }
    }

    private fun readRequest(socket: Socket): Request? {
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = input.readLine() ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = input.readLine() ?: return null
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            if (headers.size > 64) return null
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length !in 0..MAX_BODY) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) return null
            read += n
        }
        return Request(
            method = parts[0].uppercase(),
            path = parts[1].substringBefore('?'),
            headers = headers,
            body = String(body),
            remoteAddress = socket.inetAddress?.hostAddress.orEmpty()
        )
    }

    private fun InputStream.readLine(): String? {
        val builder = StringBuilder()
        while (true) {
            val c = read()
            if (c < 0) return if (builder.isEmpty()) null else builder.toString()
            if (c == '\n'.code) return builder.toString().trimEnd('\r')
            builder.append(c.toChar())
            if (builder.length > 8_192) throw IOException("Header line too long")
        }
    }

    private companion object {
        const val TAG = "CompanionServer"
        const val MAX_BODY = 64 * 1024

        fun reason(status: Int) = when (status) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            409 -> "Conflict"
            429 -> "Too Many Requests"
            else -> "Error"
        }
    }
}
