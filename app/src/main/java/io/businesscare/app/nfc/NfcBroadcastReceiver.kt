package io.businesscare.app.nfc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import io.businesscare.app.R
import io.businesscare.app.util.AppConstants
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import kotlin.text.StringBuilder

class NfcBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NfcBroadcastReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, context?.getString(R.string.nfc_broadcast_log_invalid_intent) ?: "Invalid context or intent.")
            return
        }
        Log.d(TAG, context.getString(R.string.nfc_broadcast_log_received, intent.action ?: "null"))

        if (intent.action != AppConstants.ACTION_NFC_TAG_RECEIVED) {
            Log.w(TAG, context.getString(R.string.nfc_broadcast_log_invalid_intent) + " Action: ${intent.action}")
            return
        }


        val tag: Tag? = intent.getParcelableCompat(NfcAdapter.EXTRA_TAG)

        if (tag == null) {
            Log.e(TAG, context.getString(R.string.nfc_broadcast_log_tag_null))
        } else {
            Log.d(TAG, context.getString(R.string.nfc_broadcast_log_tag_found, tag.id.toHexString()))
        }

        val content = parseNdefTagToString(tag, context)
        Log.d(TAG, context.getString(R.string.nfc_broadcast_log_parsed_content, content ?: "null"))

        showNotification(context, content ?: context.getString(R.string.nfc_tag_detected_default))
    }

    private fun parseNdefTagToString(tag: Tag?, context: Context): String {
        if (tag == null) {
            Log.w(TAG, context.getString(R.string.nfc_parse_log_tag_null_in_parseNdef))
            return context.getString(R.string.nfc_error_tag_not_found)
        }

        val ndef = Ndef.get(tag) ?: return context.getString(R.string.nfc_error_not_ndef)

        return try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            ndef.close() 

            if (ndefMessage == null) {
                Log.w(TAG, context.getString(R.string.nfc_parse_log_ndef_message_null))
                context.getString(R.string.nfc_error_empty_tag)
            } else {
                val builder = StringBuilder()
                val records = ndefMessage.records
                Log.d(TAG, context.getString(R.string.nfc_parse_log_ndef_records_found, records.size))

                records.firstOrNull()?.let { record ->
                    builder.append(parseNdefRecordPayload(record, context))
                } ?: builder.append(context.getString(R.string.nfc_empty_tag_content))
                builder.toString()
            }

        } catch (e: Exception) {
            Log.e(TAG, context.getString(R.string.nfc_parse_log_error_reading_ndef, e.message ?: e.javaClass.simpleName), e)
            context.getString(R.string.nfc_error_read_generic, e.localizedMessage ?: e.message ?: context.getString(R.string.error_unknown_default_fallback))
        } finally {
            if (ndef.isConnected) {
                try { ndef.close() } catch (e: IOException) { }
            }
        }
    }

    private fun parseNdefRecordPayload(record: NdefRecord, context: Context): String {
        val payload = record.payload
        val tnf = record.tnf
        val type = record.type

        return try {
            when {
                tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_TEXT) -> {
                    val status = payload[0].toInt()
                    val languageCodeLength = status and 0x3F

                    String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, StandardCharsets.UTF_8)
                }
                tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_URI) -> {
                    val prefix = io.businesscare.app.nfc.MainActivity.UriPrefix.MAP.getOrDefault(payload[0].toInt(), "") 
                    prefix + String(payload, 1, payload.size - 1, StandardCharsets.UTF_8)
                }
                else -> {
                    try { payload.decodeToString() } catch (e: CharacterCodingException) { payload.toHexString() }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, context.getString(R.string.nfc_parse_log_error_parsing_payload), e)
            context.getString(R.string.nfc_error_payload_parse, payload.toHexString())
        }
    }

    private fun showNotification(context: Context, contentText: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConstants.NFC_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.nfc_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.nfc_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }

       
        val mainAppActivityIntent = Intent(context, io.businesscare.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, mainAppActivityIntent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(context, AppConstants.NFC_NOTIFICATION_CHANNEL_ID) 
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.nfc_notification_title))
            .setContentText(contentText.take(50) + if (contentText.length > 50) "..." else "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                try {
                    notificationManager.notify(AppConstants.NOTIFICATION_ID_NFC, builder.build()) 
                    Log.d(TAG, context.getString(R.string.nfc_broadcast_log_notification_shown))
                } catch (e: SecurityException) {
                    Log.e(TAG, context.getString(R.string.nfc_broadcast_log_security_exception_notification), e)
                }
            } else {
                Log.w(TAG, context.getString(R.string.nfc_broadcast_log_permission_not_granted))
                Toast.makeText(context, context.getString(R.string.nfc_notification_permission_missing), Toast.LENGTH_LONG).show()
            }
        } else {
            try {
                notificationManager.notify(AppConstants.NOTIFICATION_ID_NFC, builder.build()) 
                Log.d(TAG, context.getString(R.string.nfc_broadcast_log_notification_shown_pre_tiramisu))
            } catch (e: Exception) { 
                Log.e(TAG, context.getString(R.string.nfc_broadcast_log_exception_pre_tiramisu), e)
            }
        }
    }

    inline fun <reified T : Parcelable> Intent.getParcelableCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            this.getParcelableExtra(key) as? T
        }
    }

    fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}