/*
 * VERSÃO FINAL DE ENTREGA
 *
 * Esta versão inclui:
 * 1. Correção do Bug de Login: A função de login agora ESPERA (await) o perfil do usuário
 * ser carregado ANTES de definir isLoggedIn = true, corrigindo o bug do operador.
 * 2. Criação de Perfil: A função 'loadUserProfile' agora cria um novo perfil de
 * cliente (com isOperator = false) se um perfil não for encontrado no Firestore.
 * Isso corrige o bug do "carregamento infinito" para novos usuários.
 */
package br.com.fiap.comwtcchallenge.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.fiap.comwtcchallenge.data.Client
import br.com.fiap.comwtcchallenge.data.Message
import br.com.fiap.comwtcchallenge.data.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainViewModel : ViewModel() {

    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = Firebase.firestore

    // Loading e Erros
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Autenticação (Task 1) ---
    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    // --- CRM (Task 4) ---
    private val _clients = MutableStateFlow<List<Client>>(emptyList())
    private val _searchQuery = MutableStateFlow("")

    val filteredClients: StateFlow<List<Client>> =
        combine(_clients, _searchQuery) { clients, query ->
            if (query.isBlank()) {
                clients
            } else {
                clients.filter {
                    it.name.contains(query, ignoreCase = true) ||
                            it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedClient = MutableStateFlow<Client?>(null)
    val selectedClient: StateFlow<Client?> = _selectedClient.asStateFlow()

    // --- Chat (Task 2) ---
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()


    init {
        // Se já está logado (usuário abriu o app de novo), carrega o perfil
        auth.currentUser?.uid?.let { uid ->
            viewModelScope.launch {
                loadUserProfile(uid)
            }
        }

        // Inicia UM listener para REAGIR a mudanças no perfil
        // e carregar os clientes SE for operador.
        _userProfile.onEach { profile ->
            if (profile?.isOperator == true) {
                Log.d("ViewModel", "Perfil é operador, carregando clientes...")
                loadClients()
            } else {
                Log.d("ViewModel", "Perfil não é operador ou é nulo, limpando clientes.")
                _clients.value = emptyList() // Limpa se não for operador
            }
        }.launchIn(viewModelScope) // Inicia a coleta no escopo do ViewModel
    }

    // (Task 1) Função de Login
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Email e senha não podem estar em branco."
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // 1. Faz o login no Authentication
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                authResult.user?.uid?.let { uid ->

                    // 2. CORREÇÃO CRÍTICA: Espera o perfil ser carregado do Firestore
                    loadUserProfile(uid)

                    // 3. Só então, define como logado para a UI navegar
                    _isLoggedIn.value = true
                } ?: throw Exception("Usuário não encontrado após o login.")

            } catch (e: Exception) {
                Log.w("LoginError", "Erro no login: ${e.message}")
                _errorMessage.value = "Erro no login: ${e.localizedMessage}"
                _isLoggedIn.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    // (Task 1) Carrega o perfil do Firestore OU CRIA SE NÃO EXISTIR
    private suspend fun loadUserProfile(uid: String) {
        try {
            // 1. Tenta buscar o documento
            val doc = db.collection("users").document(uid).get().await()

            // 2. Checamos se o documento existe
            if (doc.exists()) {
                // Se JÁ EXISTE (Ex: Operador), apenas carregamos
                _userProfile.value = doc.toObject<UserProfile>()
                Log.d("ViewModel", "Perfil carregado: ${_userProfile.value}")

            } else {
                // 3. Se NÃO EXISTE (Ex: Novo Cliente), nós o criamos
                Log.w("ViewModel", "Perfil não encontrado no Firestore. Criando um novo...")
                val email = auth.currentUser?.email ?: "email.desconhecido@teste.com"

                // Cria o novo perfil com 'isOperator = false' por padrão
                val newUserProfile = UserProfile(uid = uid, email = email, isOperator = false)

                // Salva o novo perfil no Firestore
                db.collection("users").document(uid).set(newUserProfile).await()

                // E define o perfil no app
                _userProfile.value = newUserProfile
                Log.d("ViewModel", "Novo perfil criado e carregado: $newUserProfile")
            }

        } catch (e: Exception) {
            Log.w("ProfileError", "Erro ao carregar perfil: ${e.message}")
            _errorMessage.value = "Erro ao carregar perfil do usuário."
            _userProfile.value = null
        }
    }

    fun logout() {
        auth.signOut()
        _isLoggedIn.value = false
        _userProfile.value = null
    }

    // (Task 4) Carrega a lista de clientes (em tempo real)
    private fun loadClients() {
        db.collection("clients")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ClientsError", "Erro ao carregar clientes", e)
                    _errorMessage.value = "Erro ao carregar clientes."
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    // Tenta converter. Se falhar, o app vai crashar e veremos no Logcat.
                    try {
                        _clients.value = snapshot.toObjects()
                        Log.d("ViewModel", "Clientes carregados: ${_clients.value.size}")
                    } catch (e: Exception) {
                        Log.e("ClientsError", "CRASH AO CONVERTER CLIENTES: ${e.message}")
                        _errorMessage.value = "Erro de dados: Verifique os tipos no Firestore (ex: status, score)."
                    }
                }
            }
    }

    // (Task 4) Filtra a lista de clientes (localmente)
    fun filterClients(query: String) {
        _searchQuery.value = query
    }

    // (Task 4) Carrega detalhes de um cliente específico
    fun getClientDetails(clientId: String) {
        _selectedClient.value = _clients.value.find { it.id == clientId }

        if (_selectedClient.value == null) {
            viewModelScope.launch {
                try {
                    val doc = db.collection("clients").document(clientId).get().await()
                    _selectedClient.value = doc.toObject<Client>()
                } catch (e: Exception) {
                    _errorMessage.value = "Erro ao buscar detalhes do cliente."
                }
            }
        }
    }

    // (Task 4) Salva uma anotação rápida
    fun saveQuickNote(clientId: String, note: String) {
        _isLoading.value = true
        db.collection("clients").document(clientId)
            .update("quickNote", note)
            .addOnSuccessListener {
                _isLoading.value = false
                _selectedClient.value = _selectedClient.value?.copy(quickNote = note)
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = "Erro ao salvar nota: ${e.message}"
            }
    }

    // (Task 2) Carrega mensagens de um chat
    fun loadChatMessages(topicId: String) {
        db.collection("chats").document(topicId).collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ChatError", "Erro ao carregar mensagens", e)
                    _errorMessage.value = "Erro ao carregar chat."
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    try {
                        _messages.value = snapshot.toObjects()
                    } catch (e: Exception) {
                        Log.e("ChatError", "CRASH AO CONVERTER MENSAGENS: ${e.message}")
                        _errorMessage.value = "Erro de dados: Verifique os tipos no chat (ex: timestamp)."
                    }
                }
            }
    }

    // (Task 2) Envia uma mensagem
    fun sendMessage(topicId: String, text: String) {
        val user = _userProfile.value ?: return
        if (text.isBlank()) return

        val message = Message(
            text = text,
            senderId = user.uid,
            senderName = user.email.split("@").firstOrNull() ?: "Usuário"
            // timestamp é automático pelo @ServerTimestamp no Models.kt
        )

        db.collection("chats").document(topicId).collection("messages")
            .add(message)
            .addOnFailureListener { e ->
                _errorMessage.value = "Erro ao enviar mensagem: ${e.message}"
            }
    }

    // (Task 5) Envia uma campanha (Simulação)
    fun sendCampaign(segmentTag: String, title: String, message: String) {
        _isLoading.value = true
        Log.d("Campaign", "Enviando campanha para tag '$segmentTag': $title - $message")

        // SIMULAÇÃO: Isso idealmente seria uma Cloud Function
        // que dispararia as notificações (Task 3)
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500) // Simula chamada de rede
            _isLoading.value = false
            _errorMessage.value = "Campanha enviada (simulação)!"
        }
    }

    // --- Utils ---
    fun clearError() {
        _errorMessage.value = null
    }
}

