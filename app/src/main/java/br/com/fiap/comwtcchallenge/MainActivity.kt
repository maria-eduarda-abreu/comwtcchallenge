/*
 * CORREÇÃO CRÍTICA (Bug de Lógica):
 * A "Tela Home" agora mostra um "Carregando..." (CircularProgressIndicator)
 * enquanto espera o 'userProfile' ser carregado.
 * Isso corrige o bug que mandava o Operador para a tela de Cliente.
 */
package br.com.fiap.comwtcchallenge

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.fiap.comwtcchallenge.data.Client
import br.com.fiap.comwtcchallenge.data.Message
import br.com.fiap.comwtcchallenge.ui.theme.ComwtcchallengeTheme
import br.com.fiap.comwtcchallenge.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    // (Task 3) Pede permissão de notificação
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("Permission", "Permissão de notificação concedida.")
        } else {
            Log.w("Permission", "Permissão de notificação negada.")
            // Mostrar um aviso se a permissão for crucial
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askNotificationPermission() // Pergunta assim que o app abre

        setContent {
            ComwtcchallengeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

// --- Navegação Principal ---
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object ClientDetail : Screen("clientDetail/{clientId}") {
        fun createRoute(clientId: String) = "clientDetail/$clientId"
    }
    object Chat : Screen("chat/{topicId}") {
        fun createRoute(topicId: String) = "chat/$topicId"
    }
    object Campaign : Screen("campaign")
}

@Composable
fun AppNavigation(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Mostra um Snackbar de erro
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = if (isLoggedIn) Screen.Home.route else Screen.Login.route
            ) {
                // (Task 1) Tela de Login
                composable(Screen.Login.route) {
                    LoginScreen(
                        onLoginClick = { email, password ->
                            viewModel.login(email, password)
                        }
                    )
                }

                // Tela "Home" - Decide entre Operador e Cliente
                composable(Screen.Home.route) {
                    val userProfile by viewModel.userProfile.collectAsState()

                    // *** ESTA É A CORREÇÃO CRÍTICA ***
                    if (userProfile == null) {
                        // Se estamos logados mas o perfil AINDA não carregou,
                        // mostramos um loading.
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                            Text("Carregando perfil...", modifier = Modifier.padding(top = 60.dp))
                        }
                    } else {
                        // QUANDO o perfil carregar, aí sim decidimos a tela
                        if (userProfile?.isOperator == true) {
                            // (Task 4) Home do Operador
                            CrmListScreen(navController, viewModel)
                        } else {
                            // Home do Cliente
                            ClientHomeScreen(navController, viewModel)
                        }
                    }
                }

                // (Task 4) Tela de Detalhes do Cliente
                composable(Screen.ClientDetail.route) { backStackEntry ->
                    val clientId = backStackEntry.arguments?.getString("clientId")
                    if (clientId != null) {
                        LaunchedEffect(clientId) {
                            viewModel.getClientDetails(clientId)
                        }
                        val client by viewModel.selectedClient.collectAsState()
                        client?.let {
                            ClientDetailScreen(it, navController, viewModel)
                        }
                    }
                }

                // (Task 2) Tela de Chat
                composable(Screen.Chat.route) { backStackEntry ->
                    val topicId = backStackEntry.arguments?.getString("topicId") ?: "general"
                    LaunchedEffect(topicId) {
                        viewModel.loadChatMessages(topicId)
                    }
                    val messages by viewModel.messages.collectAsState()
                    ChatScreen(
                        topicId = topicId,
                        messages = messages,
                        onSendMessage = { msg -> viewModel.sendMessage(topicId, msg) },
                        navController = navController
                    )
                }

                // (Task 5) Tela de Campanha Express
                composable(Screen.Campaign.route) {
                    CampaignScreen(
                        onSendCampaign = { segment, title, message ->
                            viewModel.sendCampaign(segment, title, message)
                        },
                        navController = navController
                    )
                }
            }

            // Loading overlay
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}


// --- Telas (Tasks) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginClick: (String, String) -> Unit) {
    var email by remember { mutableStateOf("operador@wtc.com") }
    var password by remember { mutableStateOf("123456") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("WTC Challenge Login", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { onLoginClick(email, password) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }
    }
}

// (Task 4) Tela Principal do Operador (Lista de Clientes)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmListScreen(navController: NavController, viewModel: MainViewModel) {
    val clients by viewModel.filteredClients.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CRM Clientes") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Campaign.route) }) {
                        Text("Campanha", fontSize = 12.sp) // Botão para Task 5
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.Default.Close, contentDescription = "Sair")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Barra de Busca
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.filterClients(it)
                },
                label = { Text("Buscar por nome ou tag") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
            // Lista de Clientes
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(clients) { client ->
                    ClientListItem(client) {
                        navController.navigate(Screen.ClientDetail.createRoute(client.id))
                    }
                }
            }
        }
    }
}

@Composable
fun ClientListItem(client: Client, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(client.name, fontWeight = FontWeight.Bold)
                Text(client.email, fontSize = 14.sp)
            }
            Text(client.tags.joinToString(", "), fontSize = 12.sp)
        }
    }
}

// (Task 4 - Detalhe) Tela de Detalhes do Cliente
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(client: Client, navController: NavController, viewModel: MainViewModel) {
    var note by remember { mutableStateOf(client.quickNote) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(client.name) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                .padding(16.dp)
        ) {
            Text("Email: ${client.email}", style = MaterialTheme.typography.bodyLarge)
            Text("Status: ${client.status}", style = MaterialTheme.typography.bodyLarge)
            Text("Score: ${client.score}", style = MaterialTheme.typography.bodyLarge)
            Text("Tags: ${client.tags.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))

            // Botão de Chat (Task 2)
            Button(
                onClick = {
                    // Mockado: O ideal seria ter um "topicId" para cada cliente
                    navController.navigate(Screen.Chat.createRoute("topic_123"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Iniciar Chat 1:1")
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Anotações Rápidas (Task 4)
            Text("Anotações Rápidas", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                label = { Text("Digite anotações...") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.saveQuickNote(client.id, note)
                    navController.popBackStack() // Volta
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Salvar Anotação")
            }
        }
    }
}

// (Task 2) Tela de Chat
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    topicId: String,
    messages: List<Message>,
    onSendMessage: (String) -> Unit,
    navController: NavController
) {
    var text by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat: $topicId") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                contentPadding = PaddingValues(8.dp)
            ) {
                items(messages) { message ->
                    MessageItem(message)
                }
            }
            // Input de Envio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Digite sua mensagem...") }
                )
                IconButton(onClick = {
                    onSendMessage(text)
                    text = ""
                }) {
                    Icon(Icons.Filled.Send, contentDescription = "Enviar")
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: Message) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        // Alinha a mensagem (mockado, o ideal seria checar o senderId)
        horizontalAlignment = if (message.senderName == "Operador") Alignment.End else Alignment.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.senderName == "Operador") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp)
            )
        }
        Text(
            text = message.senderName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}


// (Task 5) Tela de Campanha Express
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignScreen(
    onSendCampaign: (String, String, String) -> Unit,
    navController: NavController
) {
    var segmentTag by remember { mutableStateOf("VIP") }
    var title by remember { mutableStateOf("Promoção Relâmpago!") }
    var message by remember { mutableStateOf("Só hoje, 20% OFF!") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Campanha Express") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Enviar Campanha", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = segmentTag,
                onValueChange = { segmentTag = it },
                label = { Text("Segmento (Tag do Cliente)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título da Campanha") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Mensagem (com links, etc)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    onSendCampaign(segmentTag, title, message)
                    navController.popBackStack() // Volta
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enviar Campanha Agora")
            }
        }
    }
}


// --- Tela Home do Cliente ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientHomeScreen(navController: NavController, viewModel: MainViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minhas Mensagens") },
                actions = {
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.Default.Close, contentDescription = "Sair")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("TODO: Lista de Chats do Cliente", fontSize = 18.sp)
            // Aqui você chamaria o viewModel para carregar os chats
            // onde o userProfile.uid é um participante.
        }
    }
}

