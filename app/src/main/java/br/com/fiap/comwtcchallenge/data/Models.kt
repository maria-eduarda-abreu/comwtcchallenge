/*
 * CORREÇÃO: A linha 'package' foi ajustada para o seu projeto.
 */
package br.com.fiap.comwtcchallenge.data

import com.google.firebase.firestore.DocumentId

data class Client(
    @DocumentId val id: String = "",
    val name: String = "",
    val email: String = "",
    val tags: List<String> = emptyList(),
    val score: Int = 0,
    val status: String = "Novo",
    val quickNote: String = ""
)

data class Message(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isCampaign: Boolean = false
)

data class ChatTopic(
    @DocumentId val id: String = "",
    val operatorId: String = "",
    val clientId: String = "",
    val campaignId: String? = null,
    val lastMessage: String = "",
    val lastTimestamp: Long = 0
)

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val isOperator: Boolean = false,
    val fcmToken: String = ""
)
