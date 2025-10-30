package com.wtc.challenge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wtc.challenge.data.Client
import com.wtc.challenge.data.Message
import com.wtc.challenge.viewmodel.MainViewModel

// --- Ponto de Entrada ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // TODO: Mover para um @Composable Theme
            MaterialTheme {
                AppNavigation()
            }
        }
    }
}

// --- Navegação Principal ---
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel = viewModel()
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val startDestination = if (isLoggedIn) "home" else "login"

    // Componente para exibir erros
    GlobalErrorSnackbar(viewModel)

    // Pede permissão de notificação (Task 3)
    RequestNotificationPermission()

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            // Decide qual tela Home mostrar (Operador vs Cliente)
            if (userProfile?.isOperator == true) {
                CrmListScreen(
                    viewModel = viewModel,
                    onClientClick = { clientId ->
                        navController.navigate("clientDetail/$clientId")
                    },
                    onLogoutClick = {
                        viewModel.logout()
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                        }
                    },
                    onCampaignClick = {
                        navController.navigate("campaign")
                    }
                )
            } else {
                // Tela "Home" do Cliente (ex: lista de chats)
                // TODO: Implementar a lista de chats do cliente
                ClientHomeScreen(onLogoutClick = {
                    viewModel.logout()
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                })
            }
        }

        composable(
            route = "clientDetail/{clientId}",
            arguments = listOf(navArgument("clientId") { type = NavType.StringType })
        ) { backStackEntry ->
            val clientId = backStackEntry.arguments?.getString("clientId") ?: ""
            ClientDetailScreen(
                viewModel = viewModel,
                clientId = clientId,
                onChatClick = { topicId ->
                    // TODO: Criar ou buscar o topicId 1:1
                    navController.navigate("chat/topic_123") // ID mockado
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = "chat/{topicId}",
            arguments = listOf(navArgument("topicId") { type = NavType.StringType })
        ) { backStackEntry ->
            val topicId = backStackEntry.arguments?.getString("topicId") ?: ""
            ChatScreen(
                viewModel = viewModel,
                topicId = topicId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("campaign") {
            CampaignScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}


// --- Task 1: Tela de Autenticação ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("operador@wtc.com") } // Mock
    var password by remember { mutableStateOf("123456") } // Mock
    val isLoading by viewModel.isLoading.collectAsState()

    // Observa o sucesso do login
    LaunchedEffect(key1 = viewModel) {
        viewModel.isLoggedIn.collect { loggedIn ->
            if (loggedIn) {
                onLoginSuccess()
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("WTC Challenge Login") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { viewModel.login(email, password) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Login")
                }
            }
        }
    }
}

// --- Task 4: Tela de CRM (Lista de Clientes) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmListScreen(
    viewModel: MainViewModel,
    onClientClick: (String) -> Unit,
    onLogoutClick: () -> Unit,
    onCampaignClick: () -> Unit
) {
    val clients by viewModel.filteredClients.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    // Carrega clientes (só será executado se for operador)
    LaunchedEffect(key1 = Unit) {
        // ViewModel já carrega os clientes no init se for operador
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CRM Clientes") },
                actions = {
                    IconButton(onClick = onCampaignClick) {
                        Icon(Icons.Default.Send, contentDescription = "Campanha Express")
                    }
                    IconButton(onClick = onLogoutClick) {
                        Icon(Icons.Default.Logout, contentDescription = "Sair")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* TODO: Tela de novo cliente */ }) {
                Icon(Icons.Default.Add, contentDescription = "Novo Cliente")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // Barra de Busca e Filtro
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.filterClients(it) // Filtra em tempo real
                },
                label = { Text("Buscar por nome ou tag...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true
            )

            // Lista de Clientes
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(clients, key = { it.id }) { client ->
                    ClientListItem(client = client, onClick = { onClientClick(client.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientListItem(client: Client, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(client.name) },
        supportingContent = { Text(client.email) },
        overlineContent = {
            Row {
                client.tags.forEach { tag ->
                    Badge(modifier = Modifier.padding(end = 4.dp)) { Text(tag) }
                }
            }
        },
        trailingContent = {
            Text(client.status, style = MaterialTheme.typography.labelSmall)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    Divider()
}

// --- Task 4: Detalhes do Cliente (e Anotações) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    viewModel: MainViewModel,
    clientId: String,
    onChatClick: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val client by viewModel.selectedClient.collectAsState()
    var note by remember(client) { mutableStateOf(client?.quickNote ?: "") }

    LaunchedEffect(key1 = clientId) {
        viewModel.getClientDetails(clientId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(client?.name ?: "Carregando...") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            client?.let {
                Text("Email: ${it.email}", style = MaterialTheme.typography.bodyLarge)
                Text("Status: ${it.status}", style = MaterialTheme.typography.bodyLarge)
                Text("Score: ${it.score}", style = MaterialTheme.typography.bodyLarge)
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    it.tags.forEach { tag ->
                        Badge(modifier = Modifier.padding(end = 4.dp)) { Text(tag) }
                    }
                }
                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Anotações Rápidas (Task 4)
                Text("Anotações Rápidas", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Digite uma nota...") },
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
                Button(
                    onClick = { viewModel.saveQuickNote(clientId, note) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Salvar Nota")
                }

                Spacer(Modifier.weight(1f))

                // Botão para Chat 1:1 (Task 2)
                Button(
                    onClick = { onChatClick(clientId) /* TODO: Passar ID do Tópico */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Iniciar Chat 1:1")
                }
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}


// --- Task 2: Tela de Chat ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    topicId: String,
    onBackClick: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val currentUserUid = viewModel.userProfile.collectAsState().value?.uid
    var textMessage by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(key1 = topicId) {
        viewModel.loadChatMessages(topicId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") }, // TODO: Mudar para nome do cliente/campanha
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Lista de Mensagens
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageItem(message = message, isFromCurrentUser = message.senderId == currentUserUid)
                }
            }

            // Input de Mensagem
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textMessage,
                    onValueChange = { textMessage = it },
                    placeholder = { Text("Digite sua mensagem...") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if(textMessage.isNotBlank()) {
                            viewModel.sendMessage(topicId, textMessage)
                            textMessage = ""
                            keyboardController?.hide()
                        }
                    })
                )
                IconButton(onClick = {
                    if(textMessage.isNotBlank()) {
                        viewModel.sendMessage(topicId, textMessage)
                        textMessage = ""
                        keyboardController?.hide()
                    }
                }) {
                    Icon(Icons.Default.Send, contentDescription = "Enviar")
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: Message, isFromCurrentUser: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isFromCurrentUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(Modifier.padding(12.dp)) {
                if (!isFromCurrentUser) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(text = message.text)
            }
        }
    }
}

// --- Task 5: Tela de Campanha Express ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    var segmentTag by remember { mutableStateOf("VIP") } // Mock
    var title by remember { mutableStateOf("Promoção Relâmpago!") } // Mock
    var message by remember { mutableStateOf("Use o cupom VIP10 para 10% OFF hoje!") } // Mock
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Campanha Express") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Enviar campanha para um segmento de clientes.", style = MaterialTheme.typography.bodyLarge)

            OutlinedTextField(
                value = segmentTag,
                onValueChange = { segmentTag = it },
                label = { Text("Segmento (Tag do Cliente)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título da Campanha") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Mensagem") },
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { viewModel.sendCampaign(segmentTag, title, message) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enviar Campanha")
                }
            }
        }
    }
}

// --- Telas Placeholder ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientHomeScreen(onLogoutClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minhas Mensagens") },
                actions = {
                    IconButton(onClick = onLogoutClick) {
                        Icon(Icons.Default.Logout, contentDescription = "Sair")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("TODO: Lista de Chats do Cliente")
        }
    }
}

// --- Componentes Utilitários ---

@Composable
fun GlobalErrorSnackbar(viewModel: MainViewModel) {
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    Box(Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun RequestNotificationPermission() {
    // Obrigatório para Android 13+ (API 33)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    Log.d("Perm", "Permissão de notificação concedida")
                } else {
                    Log.w("Perm", "Permissão de notificação negada")
                }
            }
        )

        // Verifica se a permissão já foi dada
        LaunchedEffect(key1 = true) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Pede a permissão
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
