package com.example.business_care

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Parcelable
import android.util.Log
import android.widget.Toast // Import ajouté
import androidx.core.app.NotificationCompat
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
// Utiliser contentEquals au lieu de Arrays.equals
import kotlin.text.StringBuilder

class NfcBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NFC_TAG_RECEIVED = "com.example.business_care.ACTION_NFC_TAG_RECEIVED"
        private const val NFC_NOTIFICATION_CHANNEL_ID = "nfc_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("NfcBroadcastReceiver", "Broadcast received: ${intent?.action}")
        if (context == null || intent == null || intent.action != ACTION_NFC_TAG_RECEIVED) {
            Log.w("NfcBroadcastReceiver", "Invalid context or intent action.")
            return
        }

        // --- AJOUT DE LOGS POUR LES EXTRAS ---
        Log.d("NfcBroadcastReceiver", "--- Intent Extras START ---")
        intent.extras?.keySet()?.forEach { key ->
            val value = intent.extras?.get(key)
            // Attention: Certains extras peuvent être complexes (ex: Tag lui-même s'il était là)
            // Le log peut être tronqué ou afficher l'adresse mémoire.
            Log.d("NfcBroadcastReceiver", "Extra: Key=$key, Value=$value, Type=${value?.javaClass?.name}")
        } ?: Log.d("NfcBroadcastReceiver", "No extras found in intent.")
        Log.d("NfcBroadcastReceiver", "--- Intent Extras END ---")
        // --- FIN DES LOGS AJOUTÉS ---

        // Essayer de récupérer le Tag
        val tag: Tag? = intent.getParcelableCompat(NfcAdapter.EXTRA_TAG) // Clé standard

        if (tag == null) {
            Log.e("NfcBroadcastReceiver", "Tag is null using key NfcAdapter.EXTRA_TAG.")
            // On continue quand même pour voir si le parsing échoue gracieusement
            // et si la notification s'affiche avec un message par défaut.
        } else {
            Log.d("NfcBroadcastReceiver", "Tag FOUND using key NfcAdapter.EXTRA_TAG: ID = ${tag.id.toHexString()}")
        }

        // Passer le tag (potentiellement null) et le contexte à la fonction de parsing
        val content = parseNdefTagToString(tag, context)
        Log.d("NfcBroadcastReceiver", "Parsed content (might be error if tag was null): $content")

        // Afficher la notification même si le tag est null (avec un message par défaut)
        showNotification(context, content ?: context.getString(R.string.nfc_tag_detected_default))
    }

    // Renvoie une String décrivant le contenu, ou un message d'erreur/par défaut
    private fun parseNdefTagToString(tag: Tag?, context: Context?): String? {
        // Gérer le cas où le tag est null dès le début
        if (tag == null) {
            Log.w("NfcParseReceiver", "Tag object provided to parseNdefTagToString was null.")
            return context?.getString(R.string.nfc_error_tag_not_found) ?: "Tag non trouvé."
        }

        val ndef = Ndef.get(tag) ?: return context?.getString(R.string.nfc_error_not_ndef) ?: "Tag non compatible NDEF."

        return try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            ndef.close()

            if (ndefMessage == null) {
                Log.w("NfcParseReceiver", "NDEF message is null after connection.")
                context?.getString(R.string.nfc_error_empty_tag) ?: "Tag vide ou illisible (NDEF)."
            } else {
                val builder = StringBuilder()
                val records = ndefMessage.records
                Log.d("NfcParseReceiver", "Found ${records.size} NDEF records.")
                records.firstOrNull()?.let { record ->
                    builder.append(parseNdefRecordPayload(record, context))
                } ?: builder.append(context?.getString(R.string.nfc_empty_tag_content) ?: "Tag NFC vide")
                builder.toString()
            }

        } catch (e: Exception) { // Attrape IOException, FormatException, etc.
            Log.e("NfcParseReceiver", "Error reading NDEF tag: ${e.message}", e)
            context?.getString(R.string.nfc_error_read_generic, e.localizedMessage ?: e.message ?: "inconnue") ?: "Erreur lecture tag"
        } finally {
            if (ndef.isConnected) {
                try { ndef.close() } catch (e: IOException) { /* Ignorer */ }
            }
        }
    }

    // Fonction de parsing (identique à celle de MainActivity, passe le contexte pour les strings)
    private fun parseNdefRecordPayload(record: NdefRecord, context: Context?): String {
        val payload = record.payload
        val tnf = record.tnf
        val type = record.type

        return try {
            when {
                tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_TEXT) -> {
                    val status = payload[0].toInt()
                    val languageCodeLength = status and 0x3F
                    val isUtf16 = (status and 0x80) != 0
                    val encoding = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
                    String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, encoding)
                }
                tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_URI) -> {
                    val prefix = MainActivity.UriPrefix.MAP.getOrDefault(payload[0].toInt(), "")
                    prefix + String(payload, 1, payload.size - 1, StandardCharsets.UTF_8)
                }
                else -> {
                    try { payload.decodeToString() } catch (e: CharacterCodingException) { payload.toHexString() }
                }
            }
        } catch (e: Exception) {
            Log.e("NfcParse", "Error parsing record payload", e)
            context?.getString(R.string.nfc_error_payload_parse, payload.toHexString()) ?: "Erreur décodage: ${payload.toHexString()}"
        }
    }

    private fun showNotification(context: Context, contentText: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NFC_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.nfc_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.nfc_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Vous pourriez ajouter un extra ici pour indiquer à MainActivity qu'elle vient de la notif
            // putExtra("launched_from_notification", true)
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, mainActivityIntent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(context, NFC_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // Assurez-vous que l'icône existe
            .setContentTitle(context.getString(R.string.nfc_notification_title))
            .setContentText(contentText.take(50) + if (contentText.length > 50) "..." else "") // Aperçu court
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText)) // Texte complet si déplié
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Vérification de la permission POST_NOTIFICATIONS pour Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                try {
                    notificationManager.notify(NOTIFICATION_ID, builder.build())
                    Log.d("NfcBroadcastReceiver", "Notification shown.")
                } catch (e: SecurityException) {
                    // Ne devrait pas arriver si la permission est vérifiée, mais sécurité supplémentaire
                    Log.e("NfcBroadcastReceiver", "SecurityException showing notification despite permission check.", e)
                }
            } else {
                Log.w("NfcBroadcastReceiver", "POST_NOTIFICATIONS permission not granted.")
                // Afficher le Toast ici si la permission manque (pour Android 13+)
                Toast.makeText(context, context.getString(R.string.nfc_notification_permission_missing), Toast.LENGTH_LONG).show()
            }
        } else {
            // Versions antérieures à Android 13, pas de permission requise
            try {
                notificationManager.notify(NOTIFICATION_ID, builder.build())
                Log.d("NfcBroadcastReceiver", "Notification shown (pre-Tiramisu).")
            } catch (e: Exception) {
                // Capturer toute autre exception potentielle
                Log.e("NfcBroadcastReceiver", "Exception showing notification (pre-Tiramisu).", e)
            }
        }
    }

    // --- Fonctions Utilitaires (Idéalement dans un fichier séparé Utils.kt ou Extensions.kt) ---

    // Fonction pour obtenir Parcelable de manière compatible
    inline fun <reified T : Parcelable> Intent.getParcelableCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            this.getParcelableExtra(key) as? T
        }
    }

    // Fonction d'extension pour convertir ByteArray en String Hexadécimale
    fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

}