package com.wtc.challenge.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wtc.challenge.R

/**
 * Task 3: Serviço de Notificações
 * Recebe mensagens Push (FCM)
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    /**
     * Chamado quando uma nova mensagem push é recebida.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "From: ${remoteMessage.from}")

        // Checa se a mensagem tem dados (payload)
        remoteMessage.data.isNotEmpty().let {
            Log.d("FCM", "Message data payload: " + remoteMessage.data)
            // Aqui você pode tratar a mensagem (ex: salvar no DB local, disparar broadcast)
        }

        // Checa se a mensagem tem uma notificação (payload)
        remoteMessage.notification?.let {
            Log.d("FCM", "Message Notification Body: ${it.body}")
            // Exibe a notificação PUSH
            sendNotification(it.title ?: "Nova Mensagem", it.body ?: "Você tem uma nova mensagem.")
        }
    }

    /**
     * Chamado quando o FCM gera um novo token para o dispositivo.
     * Devemos salvar este token no perfil do usuário no Firestore.
     */
    override fun onNewToken(token: String) {
        Log.d("FCM", "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        // Atualiza o token do usuário logado no Firestore
        auth.currentUser?.uid?.let { uid ->
            db.collection("users").document(uid)
                .update("fcmToken", token)
                .addOnSuccessListener { Log.d("FCM", "Token updated for user $uid") }
                .addOnFailureListener { e -> Log.w("FCM", "Error updating token", e) }
        }
    }

    /**
     * Cria e exibe uma notificação push simples.
     */
    private fun sendNotification(title: String, messageBody: String) {
        val channelId = "wtc_channel_id"
        val channelName = "WTC Notificações"

        // Cria o NotificationChannel (Obrigatório para Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // TODO: Trocar por ícone de notificação
            .setContentTitle(title)
            .setContentText(messageBody)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            // Permissão é necessária para API 33+
            if (ActivityCompat.checkSelfPermission(
                    this@MyFirebaseMessagingService,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Se não tiver permissão, não faz nada.
                // A permissão deve ser pedida na MainActivity.
                Log.w("FCM", "Sem permissão para postar notificações.")
                return
            }
            notify(0 /* ID da notificação */, builder.build())
        }
    }
}
