// ERRO CORRIGIDO AQUI: A linha do "package" foi atualizada.
package br.com.fiap.comwtcchallenge.services

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
import br.com.fiap.comwtcchallenge.R // Import corrigido para o R

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
        }

        // Checa se a mensagem tem uma notificação (payload)
        remoteMessage.notification?.let {
            Log.d("FCM", "Message Notification Body: ${it.body}")
            sendNotification(it.title ?: "Nova Mensagem", it.body ?: "Você tem uma nova mensagem.")
        }
    }

    /**
     * Chamado quando o FCM gera um novo token para o dispositivo.
     */
    override fun onNewToken(token: String) {
        Log.d("FCM", "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
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
            // Certifique-se que você tem um ic_launcher em "mipmap"
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(
                    this@MyFirebaseMessagingService,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("FCM", "Sem permissão para postar notificações.")
                return
            }
            notify(0 /* ID da notificação */, builder.build())
        }
    }
}
