package com.wtc.challenge.data

import com.google.firebase.firestore.DocumentId

// Modelos de dados para o Firestore

/**
 * Representa um Cliente no CRM (Task 4)
 * O construtor vazio é necessário para o Firestore.
 */
data class Client(
    @DocumentId val id: String = "",
    val name: String = "",
    val email: String = "",
    val tags: List<String> = emptyList(),
    val score: Int = 0,
    val status: String = "Novo", // "Novo", "Ativo", "Inativo"
    val quickNote: String = "" // Anotações rápidas
)

/**
 * Representa uma mensagem no Chat (Task 2)
 */
data class Message(
    @DocumentId val id: String = "",
    val senderId: String = "", // Pode ser UID do Operador ou Cliente
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isCampaign: Boolean = false // Identifica se é msg de campanha (Task 5)
)

/**
 * Representa um "Chat" ou "Tópico"
 * Pode ser 1:1 (operatorId, clientId)
 * Pode ser de Campanha (campaignId, clientId)
 */
data class ChatTopic(
    @DocumentId val id: String = "",
    val operatorId: String = "",
    val clientId: String = "",
    val campaignId: String? = null, // Se for chat de campanha
    val lastMessage: String = "",
    val lastTimestamp: Long = 0
)

/**
 * Representa um usuário (Operador ou Cliente) (Task 1)
 */
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val isOperator: Boolean = false,
    val fcmToken: String = "" // Token para notificações (Task 3)
)
