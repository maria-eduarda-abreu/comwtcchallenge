/*
 * CORREÇÃO CRÍTICA (Bug do Login):
 * A função de login agora ESPERA (await) o perfil do usuário
 * ser carregado ANTES de definir isLoggedIn = true.
 * Isso corrige o bug que mandava o Operador para a tela de Cliente.
 *
 * CORREÇÃO 2 (Build Error):
 * Corrigido o uso de .await() na função de login.
 * Corrigido o operador .asStateFlow() para o .stateIn() moderno.
 * * CORREÇÃO 3 (Crash na Inicialização):
 * Otimizado o bloco 'init' para usar .onEach e .launchIn,
 * um padrão mais seguro para coletar 'Flows' no ViewModel.
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

    // CORREÇÃO: Trocado .asStateFlow() por .stateIn()
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
        }.stateIn( // Este é o operador correto
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedClient = MutableStateFlow<Client?>(null)
    val selectedClient: StateFlow<Client?> = _selectedClient.asStateFlow()

    // --- Chat (Task 2) ---
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // Bloco 'init' CORRIGIDO
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

    // FUNÇÃO CORRIGIDA
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Email e senha não podem estar em branco."
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                authResult.user?.uid?.let { uid ->
                    // CORREÇÃO: Agora 'loadUserProfile' é suspend
                    loadUserProfile(uid)
                    // Só muda o login para 'true' DEPOIS que o perfil foi carregado
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

    // A função loadUserProfile agora é 'suspend'
    private suspend fun loadUserProfile(uid: String) {
        try {
            val doc = db.collection("users").document(uid).get().await()
            _userProfile.value = doc.toObject<UserProfile>()
            Log.d("ViewModel", "Perfil carregado: ${_userProfile.value}")
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

    private fun loadClients() {
        // Listener em tempo real
        db.collection("clients")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ClientsError", "Erro ao carregar clientes", e)
                    _errorMessage.value = "Erro ao carregar clientes."
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    _clients.value = snapshot.toObjects()
                    Log.d("ViewModel", "Clientes carregados: ${_clients.value.size}")
                }
            }
    }

    fun filterClients(query: String) {
        _searchQuery.value = query
    }

    fun getClientDetails(clientId: String) {
        // O listener de 'clients' já atualiza a lista
        // Apenas filtramos o cliente selecionado
        _selectedClient.value = _clients.value.find { it.id == clientId }

        // Se não encontrar (improvável), busca manualmente
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

    fun saveQuickNote(clientId: String, note: String) {
        _isLoading.value = true
        db.collection("clients").document(clientId)
            .update("quickNote", note)
            .addOnSuccessListener {
                _isLoading.value = false
                // Atualiza o flow local
                _selectedClient.value = _selectedClient.value?.copy(quickNote = note)
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = "Erro ao salvar nota: ${e.message}"
            }
    }

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
                    _messages.value = snapshot.toObjects()
                }
            }
    }

    fun sendMessage(topicId: String, text: String) {
        val user = _userProfile.value ?: return
        if (text.isBlank()) return

        val message = Message(
            text = text,
            senderId = user.uid,
            senderName = user.email.split("@").firstOrNull() ?: "Usuário"
            // timestamp é automático pelo Firebase (FieldValue.serverTimestamp())
        )

        db.collection("chats").document(topicId).collection("messages")
            .add(message)
            .addOnFailureListener { e ->
                _errorMessage.value = "Erro ao enviar mensagem: ${e.message}"
            }
    }

    // --- Campanha (Task 5) ---
    fun sendCampaign(segmentTag: String, title: String, message: String) {
        _isLoading.value = true
        // Lógica de placeholder
        Log.d("Campaign", "Enviando campanha para tag '$segmentTag': $title - $message")

        // SIMULAÇÃO: Isso idealmente seria uma Cloud Function
        // 1. A function buscaria todos os clientes com a tag
        // 2. Criaria um novo chat de campanha
        // 3. Adicionaria todos os clientes a esse chat
        // 4. Enviaria a mensagem
        // 5. Dispararia as PUSH notifications

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

