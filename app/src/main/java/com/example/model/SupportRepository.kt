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
    val status: String,
    val statusCode: String?,
    val adminReply: String?,
    val timestamp: String
)

object SupportRepository {
    private const val TAG = "SupportRepository"
    private val _tickets = MutableStateFlow<List<SupportTicket>>(emptyList())
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
            statusCode = "open",
            adminReply = null,
            timestamp = getCurrentTime()
        )

        CoroutineScope(Dispatchers.IO).launch {
            sendTicketToOdoo(newTicket)
        }
    }

    private suspend fun sendTicketToOdoo(ticket: SupportTicket) = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://maxxbilisim.com/api/v1/support/create"
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
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val rootJson = JSONObject(responseStr)
                val resultObj = rootJson.optJSONObject("result")
                val status = resultObj?.optString("status") ?: rootJson.optString("status")
                if (status == "success") {
                    syncTicketsFromOdoo()
                } else {
                    Log.w(TAG, "Support ticket creation not successful")
                }
            } else {
                conn.disconnect()
                Log.w(TAG, "Support ticket HTTP error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending support ticket to Odoo")
        }
    }

    suspend fun syncTicketsFromOdoo() = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://maxxbilisim.com/api/v1/support/get"
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
            val code = conn.responseCode
            if (code in 200..299) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val rootJson = JSONObject(responseStr)
                val resultObj = rootJson.optJSONObject("result")
                val status = resultObj?.optString("status") ?: rootJson.optString("status")

                if (status == "success" || resultObj != null) {
                    val json = resultObj ?: rootJson
                    val ticketsArray = json.optJSONArray("tickets")
                    if (ticketsArray != null) {
                        val list = mutableListOf<SupportTicket>()
                        for (i in 0 until ticketsArray.length()) {
                            val item = ticketsArray.getJSONObject(i)
                            val tId = item.optString("ticket_id", item.optString("id", "100"))
                            val title = item.optString("title", "Destek Talebi")
                            val chName = item.optString("channel_name", item.optString("channelName", ""))
                            val msg = item.optString("message", item.optString("msg", ""))
                            val rawStatus = item.optString("status", item.optString("state", item.optString("stage", "Açık")))
                            val statusCode = item.optString("status_code", item.optString("statusCode", "open"))
                            val adminReply = item.optString("admin_reply", item.optString("adminReply", item.optString("reply", item.optString("answer", item.optString("response", item.optString("note", ""))))))
                            val timestamp = item.optString("timestamp", getCurrentTime())

                            val hasReply = !adminReply.isNullOrBlank()
                            val displayStatus = if (hasReply || rawStatus.equals("solved", true) || rawStatus.equals("Yanıtlandı", true) || rawStatus.equals("closed", true) || rawStatus.equals("done", true) || rawStatus.equals("answered", true)) "Yanıtlandı" else "Açık"

                            list.add(
                                SupportTicket(
                                    id = tId,
                                    title = title,
                                    channelName = if (chName.isBlank()) null else chName,
                                    message = msg,
                                    status = displayStatus,
                                    statusCode = statusCode,
                                    adminReply = if (adminReply.isBlank()) null else adminReply,
                                    timestamp = timestamp
                                )
                            )
                        }
                        _tickets.value = list

                        val replied = list.find { !it.adminReply.isNullOrBlank() }
                        if (replied != null && (unreadNotification.value?.id != replied.id || unreadNotification.value?.adminReply != replied.adminReply)) {
                            unreadNotification.value = replied
                        }
                    } else if (resultObj != null) {
                        _tickets.value = emptyList()
                    }
                } else {
                    Log.w(TAG, "Sync status not success")
                }
            } else {
                conn.disconnect()
                Log.w(TAG, "Sync HTTP error")
            }
        } catch (e: Exception) {
            Log.i(TAG, "Sync tickets from Odoo attempt failed, keeping last successful list.")
        }
    }

    fun dismissNotification() {
        unreadNotification.value = null
    }

    fun clearAllTickets() {
        _tickets.value = emptyList()
    }
}
