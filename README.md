CHALLENGE WTC - Plataforma de Comunicação com Clientes
======================================================

Este repositório contém o código-fonte do aplicativo Android nativo desenvolvido para a Sprint 1 do "Challenge WTC". O objetivo é criar uma plataforma de comunicação moderna, similar a um aplicativo de mensagens, que se integra ao CRM do WTC para melhorar o relacionamento com os clientes.


1\. Tecnologias Utilizadas
--------------------------

A solução foi construída como um aplicativo Android nativo, utilizando as seguintes tecnologias e bibliotecas:

*   **Linguagem de Programação:** [Kotlin](https://kotlinlang.org/) (como linguagem principal).
    
*   **Interface de Usuário (UI):** [Jetpack Compose](https://developer.android.com/jetpack/compose), a toolkit moderna do Android para construir UIs nativas de forma declarativa.
    
*   **Backend (BaaS):** [Google Firebase](https://firebase.google.com/)
    
    *   **Autenticação (Task 1):** **Firebase Authentication** para gerenciar o login de Operadores e Clientes (via E-mail e Senha).
        
    *   **Banco de Dados (Tasks 2 & 4):** **Cloud Firestore** como banco de dados NoSQL em tempo real. Ele armazena as coleções users (perfis), clients (dados do CRM) e chats (mensagens).
        
    *   **Notificações (Task 3):** **Firebase Cloud Messaging (FCM)** para o recebimento de notificações push, gerenciado pelo MyFirebaseMessagingService.kt.
        
*   **Gerenciamento de Estado:** [Kotlin Flows](https://developer.android.com/kotlin/flow) (StateFlow, MutableStateFlow) para gerenciamento de estado reativo.
    
*   **Arquitetura:** MVVM (Model-View-ViewModel).
    
*   **Navegação:** [Jetpack Navigation for Compose](https://developer.android.com/jetpack/compose/navigation) para gerenciar a transição entre as diferentes telas (@Composable).
    
*   **Assincronismo:** Kotlin Coroutines (viewModelScope, suspend fun) para lidar com chamadas de rede e de banco de dados sem bloquear a UI.
    

2\. Arquitetura da Solução
--------------------------

A aplicação segue o padrão de arquitetura **MVVM (Model-View-ViewModel)**, que separa as responsabilidades da seguinte forma:

### Model (Modelo)

*   **Localização:** data/Models.kt
    
*   **Descrição:** Define as estruturas de dados (data classes) que representam a informação do aplicativo, como UserProfile, Client e Message. Essas classes incluem anotações do Firestore (@DocumentId, @ServerTimestamp) para o mapeamento automático dos dados.
    

### View (Visão)

*   **Localização:** MainActivity.kt
    
*   **Descrição:** Composta por funções @Composable que constroem a interface do usuário (ex: LoginScreen, CrmListScreen, ClientHomeScreen). A View é "burra": ela apenas exibe o estado fornecido pelo ViewModel e captura eventos do usuário (como cliques), repassando-os ao ViewModel para processamento.
    

### ViewModel (Visão-Modelo)

*   **Localização:** viewmodel/MainViewModel.kt
    
*   **Descrição:** É o "cérebro" da aplicação. Ele sobrevive a mudanças de configuração (como rotação de tela) e é responsável por:
    
    1.  **Manter o Estado:** Armazena o estado da UI (quem está logado, a lista de clientes, mensagens do chat) em StateFlows.
        
    2.  **Lógica de Negócios:** Contém toda a lógica, como login(), logout(), loadClients(), sendMessage(), etc.
        
    3.  **Comunicação com o Backend:** É a única camada que se comunica diretamente com os serviços do Firebase (Authentication e Firestore) para buscar e salvar dados.
        

### Fluxo de Dados (MVVM)

1.  **Ação do Usuário:** A **View** (ex: LoginScreen) captura um clique.
    
2.  **Processamento:** A View chama uma função no **ViewModel** (ex: viewModel.login(...)).
    
3.  **Lógica:** O **ViewModel** processa a lógica, chama o Firebase, e atualiza uma variável de estado (ex: \_userProfile.value = ...).
    
4.  **Reação:** A **View**, que está "coletando" (observando) esse StateFlow, reage automaticamente à mudança e se recompõe para exibir a nova tela (ex: HomeScreen).
    

3\. Fluxo da Aplicação
----------------------

A aplicação possui dois fluxos principais, dependendo do tipo de usuário logado.

### Fluxo de Autenticação (Task 1)

1.  O app é iniciado. O MainViewModel verifica se o auth.currentUser é nulo.
    
2.  Se for nulo, a NavHost (em MainActivity.kt) exibe a LoginScreen.
    
3.  O usuário insere e-mail e senha e clica em "Login".
    
4.  a. Ele faz login no Firebase Authentication.b. (Crucial) Em seguida, ele chama loadUserProfile() para buscar o documento do usuário na coleção users do Firestore.c. (Correção de Bug) Se o perfil não existir (ex: um novo cliente), o app o cria automaticamente no Firestore com isOperator = false.
    
5.  O userProfile (um StateFlow) é atualizado com os dados do perfil (incluindo o campo isOperator).
    
6.  O isLoggedIn é definido como true. A NavHost reage e navega para a HomeScreen.
    

### Fluxo "Home" (O Roteador)

A HomeScreen não é uma tela visível; ela é um "roteador" (@Composable) que decide qual tela mostrar:

1.  Ela observa o userProfile do ViewModel.
    
2.  Se userProfile for null (carregando), ela exibe um CircularProgressIndicator ("Carregando perfil...").
    
3.  Se userProfile.isOperator == true, ela exibe a **CrmListScreen (Fluxo do Operador)**.
    
4.  Se userProfile.isOperator == false, ela exibe a **ClientHomeScreen (Fluxo do Cliente)**.
    

### Fluxo do Operador (Tasks 4 & 5)

1.  **CRM no App (Task 4):** O Operador vê a CrmListScreen.
    
    *   O MainViewModel anexa um _listener_ em tempo real (addSnapshotListener) à coleção clients do Firestore.
        
    *   A lista de clientes é exibida.
        
    *   A barra de busca filtra a lista localmente usando combine (Kotlin Flows).
        
2.  **Anotações (Task 4):** O Operador clica em um cliente e navega para a ClientDetailScreen.
    
    *   Ele pode editar o campo "Anotações Rápidas" e salvar. viewModel.saveQuickNote() atualiza o documento do cliente no Firestore.
        
3.  **Campanha Express (Task 5):** Na CrmListScreen, o Operador clica em "Campanha".
    
    *   O app navega para a CampaignScreen.
        
    *   O envio é (atualmente) uma simulação que demonstra a UI e o fluxo, conforme viewModel.sendCampaign().
        

### Fluxo do Cliente (Tasks 2 & 3)

1.  **Tela de Mensagens (Task 1):** O Cliente vê a ClientHomeScreen ("Minhas Mensagens"), que é o seu ponto de entrada para receber comunicações.
    
2.  **Chat 1:1 (Task 2):** (Iniciado pelo Operador) A ChatScreen carrega mensagens de uma coleção aninhada (/chats/{topicId}/messages) em tempo real e permite o envio de novas mensagens (viewModel.sendMessage()).
    
3.  **Notificações (Task 3):**
    
    *   O MyFirebaseMessagingService.kt está registrado no AndroidManifest.xml.
        
    *   Quando o Firebase envia uma notificação push, este serviço a intercepta (onMessageReceived) e exibe uma notificação nativa no sistema do Android.
        
