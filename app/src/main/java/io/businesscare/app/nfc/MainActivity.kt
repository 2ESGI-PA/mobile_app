package io.businesscare.app.nfc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentFilter.MalformedMimeTypeException
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.businesscare.app.R
import io.businesscare.app.databinding.ActivityNfcBinding
import io.businesscare.app.ui.settings.SettingsActivity
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets

enum class NfcMode {
    READ, WRITE
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNfcBinding 
    private var nfcAdapter: NfcAdapter? = null
    private val nfcNotificationChannelId = "nfc_channel"
    private var currentMode: NfcMode = NfcMode.READ

    companion object {
        private const val TAG = "NfcActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        binding = ActivityNfcBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarNfc)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            binding.textViewNfcStatusContent.text = getString(R.string.nfc_not_supported)
            binding.buttonNfcReadMode.isEnabled = false
            binding.buttonNfcWriteMode.isEnabled = false
            binding.editTextNfcWriteContent.isEnabled = false
        } else if (!nfcAdapter!!.isEnabled) {
            binding.textViewNfcStatusContent.text = getString(R.string.nfc_disabled)
            binding.buttonNfcReadMode.isEnabled = false
            binding.buttonNfcWriteMode.isEnabled = false
            binding.editTextNfcWriteContent.isEnabled = false
        } else {
            setupReadMode()
        }

        binding.buttonNfcReadMode.setOnClickListener { setupReadMode() }
        binding.buttonNfcWriteMode.setOnClickListener { setupWriteMode() }

        createNotificationChannel()
        intent?.let { handleIntent(it) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.schedule_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupReadMode() {
        currentMode = NfcMode.READ
        binding.textViewNfcStatusContent.text = getString(R.string.nfc_status_read_mode)
        binding.editTextNfcWriteContent.visibility = View.GONE
        binding.buttonNfcReadMode.alpha = 1.0f
        binding.buttonNfcWriteMode.alpha = 0.5f
    }

    private fun setupWriteMode() {
        currentMode = NfcMode.WRITE
        binding.textViewNfcStatusContent.text = getString(R.string.nfc_status_write_mode)
        binding.editTextNfcWriteContent.visibility = View.VISIBLE
        binding.buttonNfcReadMode.alpha = 0.5f
        binding.buttonNfcWriteMode.alpha = 1.0f
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, getString(R.string.nfc_log_nfc_dev_new_intent, currentMode.name, intent.action))
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action: String? = intent.action
        Log.d(TAG, getString(R.string.nfc_log_nfc_dev_handling_intent, action ?: "null", currentMode.name))

        if (NfcAdapter.ACTION_NDEF_DISCOVERED == action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == action) {

            val tag: Tag? = intent.getParcelableCompat(NfcAdapter.EXTRA_TAG)

            if (tag != null) {
                Log.d(TAG, getString(R.string.nfc_log_nfc_dev_tag_detected, tag.id.toHexString()))
                Toast.makeText(this, getString(R.string.nfc_tag_detected_foreground), Toast.LENGTH_SHORT).show()

                when (currentMode) {
                    NfcMode.READ -> {
                        Log.d(TAG, getString(R.string.nfc_log_nfc_dev_mode_read))
                        parseNdefMessageAndDisplay(tag)
                    }
                    NfcMode.WRITE -> {
                        Log.d(TAG, getString(R.string.nfc_log_nfc_dev_mode_write))
                        val textToWrite = binding.editTextNfcWriteContent.text.toString()
                        if (textToWrite.isNotEmpty()) {
                            val message = createNdefTextMessage(getString(R.string.nfc_language_code_for_ndef_text), textToWrite)
                            if (message != null) {
                                writeNdefMessageToTag(tag, message)
                            } else {
                                Log.e(TAG, getString(R.string.nfc_log_nfc_write_error_creating_ndef_message))
                                Toast.makeText(this, getString(R.string.nfc_error_creating_message), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.w(TAG, getString(R.string.nfc_log_nfc_write_empty_field))
                            Toast.makeText(this, getString(R.string.nfc_write_empty_warning), Toast.LENGTH_SHORT).show()
                            binding.textViewNfcStatusContent.text = getString(R.string.nfc_status_write_mode)
                        }
                    }
                }
            } else {
                Log.e(TAG, getString(R.string.nfc_log_nfc_dev_tag_object_null))
                binding.textViewNfcStatusContent.text = getString(R.string.nfc_error_tag_not_found)
            }
        } else {
            Log.d(TAG, getString(R.string.nfc_log_nfc_dev_intent_not_nfc, action ?: "null"))
        }
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter?.isEnabled == true) {
            setupNfcForegroundDispatch()
        } else if (nfcAdapter != null) {
            binding.textViewNfcStatusContent.text = getString(R.string.nfc_disabled)
            binding.buttonNfcReadMode.isEnabled = false
            binding.buttonNfcWriteMode.isEnabled = false
            binding.editTextNfcWriteContent.isEnabled = false
        }
    }

    override fun onPause() {
        super.onPause()
        disableNfcForegroundDispatch()
    }

    private fun setupNfcForegroundDispatch() {
        nfcAdapter?.let { adapter ->
            Log.d(TAG, getString(R.string.nfc_log_nfc_dev_setup_fg_dispatch))

            val intent = Intent(this, this.javaClass).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlag)


            val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                try { addDataType("*/*") } catch (e: MalformedMimeTypeException) {
                    Log.e(TAG, getString(R.string.nfc_log_nfc_dev_error_mime_ndef), e); throw RuntimeException(getString(R.string.nfc_log_nfc_dev_error_mime_ndef), e) }
            }
            val tech = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
            val tagDisc = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)

            val intentFiltersArray = arrayOf(ndef, tech, tagDisc)

            val techListsArray = arrayOf(arrayOf(Ndef::class.java.name), arrayOf(NdefFormatable::class.java.name))

            adapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
            Log.d(TAG, getString(R.string.nfc_log_nfc_dev_fg_dispatch_enabled))
        }
    }

    private fun disableNfcForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
        Log.d(TAG, getString(R.string.nfc_log_nfc_dev_fg_dispatch_disabled))
    }

    private fun createNdefTextMessage(language: String, text: String): NdefMessage? {
        if (language.length > 63) {
            Log.e(TAG, getString(R.string.nfc_log_nfc_write_lang_code_too_long, language))
            return null
        }
        try {
            val langBytes = language.toByteArray(StandardCharsets.US_ASCII)
            val textBytes = text.toByteArray(StandardCharsets.UTF_8)
            val recordPayload = ByteArray(1 + langBytes.size + textBytes.size)

            recordPayload[0] = langBytes.size.toByte()
            System.arraycopy(langBytes, 0, recordPayload, 1, langBytes.size)
            System.arraycopy(textBytes, 0, recordPayload, 1 + langBytes.size, textBytes.size)

            val record = NdefRecord(
                NdefRecord.TNF_WELL_KNOWN,
                NdefRecord.RTD_TEXT,
                ByteArray(0),
                recordPayload
            )
            return NdefMessage(arrayOf(record))
        } catch (e: Exception) {
            Log.e(TAG, getString(R.string.nfc_log_nfc_write_error_creating_text_record), e)
            return null
        }
    }

    private fun writeNdefMessageToTag(tag: Tag, message: NdefMessage) {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            writeToNdefTag(ndef, message)
        } else {
            val formatable = NdefFormatable.get(tag)
            if (formatable != null) {
                writeToNdefFormatableTag(formatable, message)
            } else {
                Log.w(TAG, getString(R.string.nfc_log_nfc_write_tag_not_supported))
                Toast.makeText(this, getString(R.string.nfc_error_not_ndef_or_formattable), Toast.LENGTH_LONG).show()
                binding.textViewNfcStatusContent.text = getString(R.string.nfc_error_not_ndef_or_formattable)
            }
        }
    }

    private fun writeToNdefTag(ndef: Ndef, message: NdefMessage) {
        try {
            ndef.connect()
            if (!ndef.isWritable) {
                Log.w(TAG, getString(R.string.nfc_log_nfc_write_tag_not_writable))
                Toast.makeText(this, getString(R.string.nfc_write_error_read_only), Toast.LENGTH_SHORT).show()
                binding.textViewNfcStatusContent.text = getString(R.string.nfc_write_error_read_only)
                return
            }
            val maxSize = ndef.maxSize
            val messageSize = message.toByteArray().size
            if (maxSize < messageSize) {
                Log.w(TAG, getString(R.string.nfc_log_nfc_write_insufficient_space, messageSize, maxSize))
                val errorMsg = getString(R.string.nfc_write_error_no_space, messageSize, maxSize)
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                binding.textViewNfcStatusContent.text = errorMsg
                return
            }

            ndef.writeNdefMessage(message)
            Log.i(TAG, getString(R.string.nfc_log_nfc_write_message_written))
            Toast.makeText(this, getString(R.string.nfc_write_success), Toast.LENGTH_SHORT).show()
            binding.textViewNfcStatusContent.text = getString(R.string.nfc_written_content_display_success, getString(R.string.nfc_write_success), parseNdefRecordPayload(message.records[0]))

        } catch (e: TagLostException) {
            Log.e(TAG, getString(R.string.nfc_log_nfc_write_tag_lost_ndef), e)
            Toast.makeText(this, getString(R.string.nfc_write_error_tag_lost), Toast.LENGTH_SHORT).show()
            binding.textViewNfcStatusContent.text = getString(R.string.nfc_write_error_tag_lost)
        } catch (e: IOException) {
            Log.e(TAG, getString(R.string.nfc_log_nfc_write_io_error_ndef), e)
            val errorMsg = getString(R.string.nfc_write_error_io, e.localizedMessage ?: getString(R.string.nfc_read_error_io_default))
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.textViewNfcStatusContent.text = errorMsg
        } catch (e: FormatException) {
            Log.e(TAG, getString(R.string.nfc_log_nfc_write_format_error_ndef), e)
            val errorMsg = getString(R.string.nfc_write_error_format, e.localizedMessage ?: getString(R.string.nfc_read_error_format_default))
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.textViewNfcStatusContent.text = errorMsg
        } catch (e: Exception) {
            Log.e(TAG, getString(R.string.nfc_log_nfc_write_unknown_error_ndef), e)
            val errorMsg = getString(R.string.nfc_write_error_unknown, e.localizedMessage ?: getString(R.string.nfc_read_error_unknown_default))
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.textViewNfcStatusContent.text = errorMsg
        } finally {
            try { ndef.close() } catch (e: IOException) { Log.w(TAG, getString(R.string.nfc_log_nfc_write_error_closing_ndef), e) }
        }
    }

    private fun writeToNdefFormatableTag(formatable: NdefFormatable, message: NdefMessage) {
        try {
            formatable.connect()
            formatable.format(message)
            Log.i(TAG, getString(R.string.nfc_log_nfc_write_tag_formatted))
            Toast.makeText(this, getString(R.string.nfc_write_format_success), Toast.LENGTH_SHORT).show()
            binding.textViewNfcStatusContent.text = getString(R.string.nfc_written_content_display_success, getString(R.string.nfc_write_format_success), parseNdefRecordPayload(message.records[0]))

        } catch (e: TagLostException) {
            Log.e(TAG, getString(R.string.nfc_log_nfc_write_tag_lost_formatable), e)
            Toast.makeText(this, getString(R.string.nfc_write_error_tag_lost), Toast.LENGTH_SHORT).show()
            binding.textViewNfcStatusContent.text = getString(R.string.nfc_write_error_tag_lost)
        } catch (e: IOException) {
            Log.e(TAG, getString(R.string.nfc_log_nfc_write_io_error_formatable), e)
            val errorMsg = getString(R.string.nfc_format_error_io, e.localizedMessage ?: getString(R.string.nfc_read_error_io_default))
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.textViewNfcStatusContent.text = errorMsg
        } catch (e: FormatException) {
            Log.e(TAG, getString(R.string.nfc_log_nfc_write_format_error_formatable), e)
            val errorMsg = getString(R.string.nfc_format_error_format, e.localizedMessage ?: getString(R.string.nfc_read_error_format_default))
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.textViewNfcStatusContent.text = errorMsg
        } catch (e: Exception) {
            Log.e(TAG, getString(R.string.nfc_log_nfc_write_unknown_error_formatable), e)
            val errorMsg = getString(R.string.nfc_format_error_unknown, e.localizedMessage ?: getString(R.string.nfc_read_error_unknown_default))
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.textViewNfcStatusContent.text = errorMsg
        } finally {
            try { formatable.close() } catch (e: IOException) { Log.w(TAG, getString(R.string.nfc_log_nfc_write_error_closing_formatable), e) }
        }
    }

    private fun parseNdefMessageAndDisplay(tag: Tag) {
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            binding.textViewNfcStatusContent.text = getString(R.string.nfc_error_not_ndef)
            return
        }

        try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            if (ndefMessage == null) {
                binding.textViewNfcStatusContent.text = getString(R.string.nfc_error_empty_tag)
                return
            }

            val builder = StringBuilder()
            ndefMessage.records.forEachIndexed { index, record ->
                val payloadString = parseNdefRecordPayload(record)
                if(index > 0) builder.append("\n")
                builder.append("[$${index+1}] $payloadString")
            }
            binding.textViewNfcStatusContent.text = getString(R.string.nfc_text_parsed_display, ndefMessage.records.size, builder.toString())

        } catch (e: IOException) {
            Log.e(TAG, "IOException: ${e.message}", e)
            binding.textViewNfcStatusContent.text = getString(R.string.nfc_error_io, e.localizedMessage ?: getString(R.string.nfc_read_error_io_default))
        } catch (e: android.nfc.FormatException) {
            Log.e(TAG, "FormatException: ${e.message}", e)
            binding.textViewNfcStatusContent.text = getString(R.string.nfc_error_format, e.localizedMessage ?: getString(R.string.nfc_read_error_format_default))
        } catch (e: Exception) {
            Log.e(TAG, "General Exception: ${e.message}", e)
            binding.textViewNfcStatusContent.text = getString(R.string.nfc_error_generic, e.localizedMessage ?: getString(R.string.nfc_read_error_unknown_default))
        } finally {
            try { ndef.close() } catch (e: IOException) { /* Ignorer */ }
        }
    }

    private fun parseNdefRecordPayload(record: NdefRecord): String {
        val payload = record.payload
        return when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                try {
                    val status = payload[0].toInt()
                    val languageCodeLength = status and 0x3F
                    String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, StandardCharsets.UTF_8)
                } catch (e: Exception) { getString(R.string.nfc_text_payload_error, payload.toHexString()) }
            }
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI) -> {
                try {
                    val prefix = UriPrefix.MAP.getOrDefault(payload[0].toInt(), "")
                    prefix + String(payload, 1, payload.size - 1, StandardCharsets.UTF_8)
                } catch (e: Exception) { getString(R.string.nfc_uri_payload_error, payload.toHexString()) }
            }
            else -> {
                try { payload.toString(StandardCharsets.UTF_8) }
                catch (e: CharacterCodingException) { payload.toHexString() } 
                catch (e: Exception) { getString(R.string.nfc_unknown_payload_type, payload.toHexString()) }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.nfc_channel_name)
            val descriptionText = getString(R.string.nfc_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(nfcNotificationChannelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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

    object UriPrefix {
        val MAP: Map<Int, String> = mapOf(
            0x00 to "", 0x01 to "http://www.", 0x02 to "https://www.", 0x03 to "http://",
            0x04 to "https://", 0x05 to "tel:", 0x06 to "mailto:", 0x1D to "file://"

        ).withDefault { "" }
    }
}