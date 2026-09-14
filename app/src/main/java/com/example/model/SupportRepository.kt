package com.example.model

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SupportTicket(
    val id: String,
    val title: String,
    val channelName: String?,
    val message: String,
    val status: String, // "Açık" or "Yanıtlandı"
    val adminReply: String?,
    val timestamp: String
)

object SupportRepository {
    private const val TAG = "SupportRepository"
    private val _tickets = MutableStateFlow<List<SupportTicket>>(
        listOf(
            SupportTicket(
                id = "100",
                title = "Sistem Hoşgeldiniz Talebi",
                channelName = "Genel Sistem",
                message = "MAXX PLAYER destek birimine hoş geldiniz. Yayınlarda yaşadığınız sorunları buradan bildirebilirsiniz.",
                status = "Yanıtlandı",
                adminReply = "Teknik destek ekibimiz 7/24 hizmetinizdedir. Bildirdiğiniz kanallar anında incelenir.",
                timestamp = getCurrentTime()
            )
        )
    )
    val tickets: MutableStateFlow<List<SupportTicket>> = _tickets

    val unreadNotification = MutableStateFlow<SupportTicket?>(null)

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    fun createTicket(title: String, channelName: String?, message: String) {
        val newId = (System.currentTimeMillis() % 100000).toString()
        val newTicket = SupportTicket(
            id = newId,
            title = title,
            channelName = channelName,
            message = message,
            status = "Açık",
            adminReply = null,
            timestamp = getCurrentTime()
        )
        _tickets.value = listOf(newTicket) + _tickets.value

        // Send to Odoo backend asynchronously so web administrators can see, edit, and reply to it
        CoroutineScope(Dispatchers.IO).launch {
            sendTicketToOdoo(newTicket)
        }
    }

    private suspend fun sendTicketToOdoo(ticket: SupportTicket) = withContext(Dispatchers.IO) {
        try {
            val odooUrl = DeviceManager.getOdooServerUrl().trimEnd('/')
            val endpoint = "$odooUrl/api/v1/support/create"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val params = JSONObject().apply {
                put("device_id", DeviceManager.getDeviceId())
                put("device_key", DeviceManager.getDeviceKey())
                put("ticket_id", ticket.id)
                put("title", ticket.title)
                put("channel_name", ticket.channelName ?: "")
                put("message", ticket.message)
                put("timestamp", ticket.timestamp)
            }
            val payload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", params)
            }
            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            val code = conn.responseCode
            if (code in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Support ticket sent to Odoo successfully: $resp")
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending support ticket to Odoo: ${e.message}")
        }
    }

    suspend fun syncTicketsFromOdoo() = withContext(Dispatchers.IO) {
        try {
            val odooUrl = DeviceManager.getOdooServerUrl().trimEnd('/')
            val endpoint = "$odooUrl/api/v1/support/get"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val params = JSONObject().apply {
                put("device_id", DeviceManager.getDeviceId())
                put("device_key", DeviceManager.getDeviceKey())
            }
            val payload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", params)
            }
            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            if (conn.responseCode in 200..299) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val rootJson = JSONObject(responseStr)
                val json = rootJson.optJSONObject("result") ?: rootJson
                val ticketsArray = json.optJSONArray("tickets")
                if (ticketsArray != null) {
                    val list = mutableListOf<SupportTicket>()
                    for (i in 0 until ticketsArray.length()) {
                        val item = ticketsArray.getJSONObject(i)
                        val tId = item.optString("ticket_id", item.optString("id", "100"))
                        val title = item.optString("title", "Destek Talebi")
                        val chName = item.optString("channel_name", "")
                        val msg = item.optString("message", "")
                        val status = item.optString("status", "Açık")
                        val adminReply = item.optString("admin_reply", item.optString("adminReply", ""))
                        val timestamp = item.optString("timestamp", getCurrentTime())

                        list.add(
                            SupportTicket(
                                id = tId,
                                title = title,
                                channelName = if (chName.isBlank()) null else chName,
                                message = msg,
                                status = status,
                                adminReply = if (adminReply.isBlank()) null else adminReply,
                                timestamp = timestamp
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        _tickets.value = list
                        val replied = list.find { it.status == "Yanıtlandı" && !it.adminReply.isNullOrBlank() }
                        if (replied != null && unreadNotification.value?.id != replied.id) {
                            unreadNotification.value = replied
                        }
                    }
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.i(TAG, "Sync tickets from Odoo attempt: ${e.message}")
        }
    }

    fun dismissNotification() {
        unreadNotification.value = null
    }

    fun clearAllTickets() {
        _tickets.value = emptyList()
    }
}

