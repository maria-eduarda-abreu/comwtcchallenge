/*
 * VERSÃO FINAL DE ENTREGA
 *
 * Esta versão inclui:
 * 1. @DocumentId em TODAS as classes (corrige o login).
 * 2. @ServerTimestamp no 'Message' (corrige o chat).
 * 3. 'score: Long' no 'Client' (corrige o crash do tipo de dado).
 * 4. 'status: List<String>' no 'Client' (corrige o crash de tipo de dado).
 * 5. 'isOperator: Boolean = false' (corrige a lógica de fallback).
 */
package br.com.fiap.comwtcchallenge.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// (Task 4) Modelo do Cliente (para o CRM)
data class Client(
    @DocumentId val id: String = "",
    val name: String = "",
    val email: String = "",
    val tags: List<String> = emptyList(),
    val score: Long = 0, // <-- Corrige o crash de tipo (Long vs Int)
    val status: List<String> = emptyList(), // <-- Corrige o crash de tipo (Lista vs String)
    val quickNote: String = ""
)

// (Task 2) Modelo da Mensagem (para o Chat)
data class Message(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    @ServerTimestamp val timestamp: Date? = null, // <-- Corrige o chat
    val isCampaign: Boolean = false
)

// (Task 1) Modelo do Perfil de Usuário (para Autenticação)
data class UserProfile(
    @DocumentId val uid: String = "", // <-- Corrige a falha de leitura
    val email: String = "",
    val isOperator: Boolean = true, // <-- Corrige a lógica de fallback
    val fcmToken: String = ""
)

