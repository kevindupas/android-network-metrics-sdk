package com.kevindupas.networkmetrics.measurement

import com.kevindupas.networkmetrics.model.UdpResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

private const val PACKET_COUNT = 50
private const val INTERVAL_MS = 20L
private const val TIMEOUT_MS = 3000
private const val PROBE_PAYLOAD = "PING"

internal class PacketLossMeasurement(
    private val host: String,
    private val udpPort: Int,
    private val tcpPort: Int,
) {

    suspend fun measure(): UdpResult? = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext null
        if (probeUdp()) measureUdp() else measureTcp()
    }

    private fun probeUdp(): Boolean {
        return try {
            val socket = DatagramSocket()
            socket.soTimeout = 2000
            val addr = InetAddress.getByName(host)
            val payload = PROBE_PAYLOAD.toByteArray()
            socket.send(DatagramPacket(payload, payload.size, addr, udpPort))
            val buf = ByteArray(64)
            socket.receive(DatagramPacket(buf, buf.size))
            socket.close()
            true
        } catch (_: Exception) { false }
    }

    private fun measureUdp(): UdpResult {
        var sent = 0
        var received = 0
        val socket = DatagramSocket()
        socket.soTimeout = TIMEOUT_MS
        val addr = InetAddress.getByName(host)

        repeat(PACKET_COUNT) { seq ->
            val payload = "SEQ:$seq".toByteArray()
            socket.send(DatagramPacket(payload, payload.size, addr, udpPort))
            sent++
            Thread.sleep(INTERVAL_MS)
            try {
                val buf = ByteArray(64)
                socket.receive(DatagramPacket(buf, buf.size))
                received++
            } catch (_: Exception) {}
        }
        socket.close()
        val loss = if (sent == 0) 100.0 else (sent - received) * 100.0 / sent
        return UdpResult(sent, received, loss, "udp")
    }

    private fun measureTcp(): UdpResult? {
        return try {
            var sent = 0
            var received = 0
            val socket = Socket(host, tcpPort)
            socket.soTimeout = TIMEOUT_MS
            val out = socket.getOutputStream()
            val inp = socket.getInputStream()

            repeat(PACKET_COUNT) { seq ->
                val payload = ByteArray(4)
                payload[0] = (seq shr 24).toByte()
                payload[1] = (seq shr 16).toByte()
                payload[2] = (seq shr 8).toByte()
                payload[3] = seq.toByte()
                out.write(payload)
                sent++
                Thread.sleep(INTERVAL_MS)
                try {
                    val buf = ByteArray(4)
                    var read = 0
                    while (read < 4) read += inp.read(buf, read, 4 - read)
                    received++
                } catch (_: Exception) {}
            }
            socket.close()
            val loss = if (sent == 0) 100.0 else (sent - received) * 100.0 / sent
            UdpResult(sent, received, loss, "tcp")
        } catch (_: Exception) { null }
    }
}
