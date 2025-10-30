package com.wtc.challenge.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.wtc.challenge.data.Client
import com.wtc.challenge.data.Message
import com.wtc.challenge.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainViewModel : ViewModel() {

    private val auth = Firebase.auth
    private val db = Firebase.firestore

    // --- Estado da UI ---
    // Indica se o usuário está logado
    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // Perfil do usuário logado (Operador ou Cliente)
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    // Mensagem de erro (ex: falha no login)
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Status de carregamento
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // --- Task 4: CRM ---
    private val _clients = MutableStateFlow<List<Client>>(emptyList())
    val clients: StateFlow<List<Client>> = _clients.asStateFlow()
    private val _filteredClients = MutableStateFlow<List<Client>>(emptyList())
    val filteredClients: StateFlow<List<Client>> = _filteredClients.asStateFlow()
    private val _selectedClient = MutableStateFlow<Client?>(null)
    val selectedClient: StateFlow<Client?> = _selectedClient.asStateFlow()


    // --- Task 2: Chat ---
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    init {
        // Observa o estado de autenticação
        auth.addAuthStateListener { firebaseAuth ->
            _isLoggedIn.value = firebaseAuth.currentUser != null
            if (_isLoggedIn.value) {
                loadUserProfile(firebaseAuth.currentUser!!.uid)
            } else {
                _userProfile.value = null
            }
        }
    }

    // --- Task 1: Autenticação ---
    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                auth.signInWithEmailAndPassword(email, pass).await()
                // Sucesso, o listener do init vai carregar o perfil
            } catch (e: Exception) {
                Log.w("Auth", "Falha no login", e)
                _errorMessage.value = "Login falhou: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    fun logout() {
        auth.signOut()
    }

    // Carrega o perfil do usuário (para saber se é Operador)
    private fun loadUserProfile(uid: String) {
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(uid).get().await()
                _userProfile.value = doc.toObject<UserProfile>()
                // Se for operador, carrega a lista de clientes do CRM
                if (_userProfile.value?.isOperator == true) {
                    loadClients()
                }
            } catch (e: Exception) {
                Log.w("ViewModel", "Falha ao carregar perfil", e)
                _errorMessage.value = "Falha ao carregar perfil."
            }
        }
    }

    // --- Task 4: CRM no App ---
    private fun loadClients() {
        // Escuta em tempo real por mudanças na coleção de clientes
        db.collection("clients")
            .orderBy("name")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("CRM", "Erro ao carregar clientes", e)
                    _errorMessage.value = "Erro ao carregar clientes."
                    return@addSnapshotListener
                }

                val clientList = snapshots?.mapNotNull { it.toObject<Client>() } ?: emptyList()
                _clients.value = clientList
                _filteredClients.value = clientList // Inicia com a lista completa
            }
    }

    // Filtra a lista de clientes (Busca)
    fun filterClients(query: String) {
        if (query.isBlank()) {
            _filteredClients.value = _clients.value
        } else {
            _filteredClients.value = _clients.value.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            }
        }
    }

    fun getClientDetails(clientId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val doc = db.collection("clients").document(clientId).get().await()
                _selectedClient.value = doc.toObject<Client>()
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao carregar cliente."
            }
            _isLoading.value = false
        }
    }

    // Salva anotações rápidas
    fun saveQuickNote(clientId: String, note: String) {
        viewModelScope.launch {
            try {
                db.collection("clients").document(clientId)
                    .update("quickNote", note)
                    .await()
                // Atualiza o cliente selecionado localmente
                _selectedClient.update { it?.copy(quickNote = note) }
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao salvar nota."
            }
        }
    }


    // --- Task 2: Chat Integrado ---
    fun loadChatMessages(topicId: String) {
        // Escuta em tempo real por novas mensagens
        db.collection("chat_topics").document(topicId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("Chat", "Erro ao carregar mensagens", e)
                    _errorMessage.value = "Erro ao carregar mensagens."
                    return@addSnapshotListener
                }
                _messages.value = snapshots?.mapNotNull { it.toObject<Message>() } ?: emptyList()
            }
    }

    fun sendMessage(topicId: String, text: String) {
        val uid = auth.currentUser?.uid ?: return
        val name = _userProfile.value?.email ?: "Usuário"

        val message = Message(
            senderId = uid,
            senderName = name,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            try {
                // Adiciona a nova mensagem
                db.collection("chat_topics").document(topicId)
                    .collection("messages")
                    .add(message)
                    .await()

                // Atualiza o "lastMessage" do tópico
                db.collection("chat_topics").document(topicId)
                    .update(
                        mapOf(
                            "lastMessage" to text,
                            "lastTimestamp" to message.timestamp
                        )
                    ).await()

            } catch (e: Exception) {
                Log.w("Chat", "Erro ao enviar mensagem", e)
                _errorMessage.value = "Erro ao enviar mensagem."
            }
        }
    }

    // --- Task 5: Campanha Express ---
    fun sendCampaign(segmentTag: String, title: String, messageText: String) {
        // 1. Acha todos os clientes com a tag
        // 2. Cria um "ChatTopic" de campanha para cada um
        // 3. Adiciona a mensagem de campanha nesse tópico
        // 4. (No Lado do Servidor/Cloud Function) Dispara a notificação push
        // Esta é uma operação complexa, idealmente feita por um Backend/Cloud Function
        // Para a demo, vamos apenas simular o envio:

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Filtra clientes localmente (simulação)
                val targetClients = _clients.value.filter { it.tags.contains(segmentTag) }
                if(targetClients.isEmpty()) {
                    _errorMessage.value = "Nenhum cliente encontrado no segmento '$segmentTag'"
                    _isLoading.value = false
                    return@launch
                }

                Log.d("Campaign", "Enviando campanha para ${targetClients.size} clientes...")

                // TODO: Implementar a lógica de criação de N chats de campanha
                // Por enquanto, apenas logamos

                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao enviar campanha."
                _isLoading.value = false
            }
        }
    }

    // Limpa a mensagem de erro
    fun clearError() {
        _errorMessage.value = null
    }
}
