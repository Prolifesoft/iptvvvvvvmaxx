package com.example.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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
        val newId = (100 + _tickets.value.size + 1).toString()
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

        // Simulate admin response after 6 seconds
        CoroutineScope(Dispatchers.Main).launch {
            delay(6000)
            replyToTicket(newId, channelName)
        }
    }

    private fun replyToTicket(ticketId: String, channelName: String?) {
        val currentList = _tickets.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == ticketId }
        if (index != -1) {
            val old = currentList[index]
            val replyMsg = if (!channelName.isNullOrBlank()) {
                "Merhaba, bildirdiğiniz '$channelName' yayını teknik destek ekibimiz tarafından incelendi ve yayın sunucusu güncellendi. İyi seyirler dileriz!"
            } else {
                "Merhaba, destek talebiniz incelendi ve gerekli teknik düzenlemeler yapıldı. Bildiriminiz için teşekkür ederiz."
            }
            val updated = old.copy(
                status = "Yanıtlandı",
                adminReply = replyMsg
            )
            currentList[index] = updated
            _tickets.value = currentList
            unreadNotification.value = updated
        }
    }

    fun dismissNotification() {
        unreadNotification.value = null
    }

    fun clearAllTickets() {
        _tickets.value = emptyList()
    }
}
