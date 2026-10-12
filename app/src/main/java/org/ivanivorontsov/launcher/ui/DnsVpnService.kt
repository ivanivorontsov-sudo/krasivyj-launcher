package org.ivanivorontsov.launcher.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * DNS-only local VPN. The TUN route covers only the virtual DNS address; normal app traffic
 * stays on the underlying network. IPv4 UDP/53 is forwarded to AdGuard DNS.
 */
class DnsVpnService : VpnService() {
    @Volatile private var tun: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            stopSelf()
            return START_NOT_STICKY
        }
        val family = intent?.getBooleanExtra(EXTRA_FAMILY, false) ?: getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(EXTRA_FAMILY, false)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(EXTRA_FAMILY, family).apply()
        startForeground(NOTIFICATION_ID, notification(family))
        if (running.compareAndSet(false, true)) {
            worker = thread(name = "UyutDnsTunnel") { runTunnel(family) }
        } else {
            stopTunnel()
            running.set(true)
            worker = thread(name = "UyutDnsTunnel") { runTunnel(family) }
        }
        return START_STICKY
    }

    private fun runTunnel(family: Boolean) {
        val resolver = if (family) "94.140.14.15" else "94.140.14.14"
        try {
            val builder = Builder()
                .setSession(if (family) "СуперСмарт · AdGuard Family DNS" else "СуперСмарт · AdGuard DNS")
                .setMtu(1500)
                .addAddress(VIRTUAL_DNS, 32)
                .addDnsServer(VIRTUAL_DNS)
                .addRoute(VIRTUAL_DNS, 32)
            tun = builder.establish() ?: throw IllegalStateException("VPN permission was not granted")
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).putBoolean(KEY_ERROR, false).apply()
            val input = FileInputStream(tun!!.fileDescriptor)
            val output = FileOutputStream(tun!!.fileDescriptor)
            val packet = ByteArray(32767)
            DatagramSocket().use { socket ->
                if (!protect(socket)) throw IllegalStateException("Unable to protect DNS upstream socket")
                socket.soTimeout = 2500
                while (running.get()) {
                    val length = try { input.read(packet) } catch (_: Exception) { if (running.get()) continue else break }
                    if (length < 28) continue
                    val version = (packet[0].toInt() ushr 4) and 0xF
                    val ihl = (packet[0].toInt() and 0xF) * 4
                    if (version != 4 || ihl < 20 || length < ihl + 8) continue
                    val protocol = packet[9].toInt() and 0xFF
                    if (protocol != 17) continue
                    val srcPort = u16(packet, ihl)
                    val dstPort = u16(packet, ihl + 2)
                    if (dstPort != 53) continue
                    val dnsLen = u16(packet, ihl + 4) - 8
                    if (dnsLen < 12 || ihl + 8 + dnsLen > length) continue
                    val srcIp = packet.copyOfRange(12, 16)
                    val dstIp = packet.copyOfRange(16, 20)
                    val dns = packet.copyOfRange(ihl + 8, ihl + 8 + dnsLen)
                    try {
                        val dest = DatagramPacket(dns, dns.size, InetAddress.getByName(resolver), 53)
                        socket.send(dest)
                        val buf = ByteArray(4096)
                        val response = DatagramPacket(buf, buf.size)
                        socket.receive(response)
                        val replyDns = response.data.copyOf(response.length)
                        val reply = ipv4Udp(srcIp = dstIp, dstIp = srcIp, srcPort = 53, dstPort = srcPort, payload = replyDns)
                        output.write(reply)
                    } catch (_: Exception) {
                        // A single upstream timeout should not kill the tunnel.
                    }
                }
            }
        } catch (_: Exception) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ERROR, true).apply()
        } finally {
            try { tun?.close() } catch (_: Exception) {}
            tun = null
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
            running.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopTunnel() {
        running.set(false)
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        worker?.interrupt()
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    private fun notification(family: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "СуперСмарт · DNS", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(this, 1, Intent(this, DnsVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("СуперСмарт · DNS включён")
            .setContentText(if (family) "AdGuard Family · фильтр сайтов" else "AdGuard DNS · стандартный")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Выключить DNS", stop).build())
            .build()
    }

    private fun ipv4Udp(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val total = 20 + 8 + payload.size
        val b = ByteArray(total)
        b[0] = 0x45
        put16(b, 2, total)
        b[8] = 64
        b[9] = 17
        System.arraycopy(srcIp, 0, b, 12, 4)
        System.arraycopy(dstIp, 0, b, 16, 4)
        put16(b, 10, checksum(b, 0, 20))
        put16(b, 20, srcPort)
        put16(b, 22, dstPort)
        put16(b, 24, 8 + payload.size)
        System.arraycopy(payload, 0, b, 28, payload.size)
        // IPv4 UDP checksum zero is valid and widely supported.
        return b
    }

    private fun checksum(b: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val hi = (b[i].toInt() and 0xFF) shl 8
            val lo = if (i + 1 < offset + length) b[i + 1].toInt() and 0xFF else 0
            sum += hi or lo
            i += 2
        }
        while ((sum ushr 16) != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
    private fun put16(b: ByteArray, i: Int, v: Int) { b[i] = (v ushr 8).toByte(); b[i + 1] = v.toByte() }

    companion object {
        const val ACTION_START = "org.ivanivorontsov.launcher.DNS_START"
        const val ACTION_STOP = "org.ivanivorontsov.launcher.DNS_STOP"
        const val EXTRA_FAMILY = "family_dns"
        const val PREFS = "uyut_dns"
        const val KEY_ERROR = "dns_error"
        const val KEY_ENABLED = "enabled"
        private const val CHANNEL = "uyut_dns_vpn"
        private const val NOTIFICATION_ID = 7042
        private const val VIRTUAL_DNS = "10.111.0.1"
    }
}
