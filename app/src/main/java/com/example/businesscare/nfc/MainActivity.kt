package com.example.businesscare.nfc

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
import com.example.businesscare.R
import com.example.businesscare.databinding.ActivityNfcBinding
import com.example.businesscare.ui.settings.SettingsActivity
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets

enum class NfcMode {
    READ, WRITE
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNfcBinding
    private var nfcAdapter: NfcAdapter? = null
    private val NFC_NOTIFICATION_CHANNEL_ID = "nfc_channel"
    private var currentMode: NfcMode = NfcMode.READ

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
            binding.nfcContentTextview.text = getString(R.string.nfc_not_supported)
            binding.buttonReadMode.isEnabled = false
            binding.buttonWriteMode.isEnabled = false
            binding.nfcWriteEdittext.isEnabled = false
        } else if (!nfcAdapter!!.isEnabled) {
            binding.nfcContentTextview.text = getString(R.string.nfc_disabled)
            binding.buttonReadMode.isEnabled = false
            binding.buttonWriteMode.isEnabled = false
            binding.nfcWriteEdittext.isEnabled = false
        } else {
            setupReadMode()
        }

        binding.buttonReadMode.setOnClickListener { setupReadMode() }
        binding.buttonWriteMode.setOnClickListener { setupWriteMode() }

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
        binding.nfcContentTextview.text = getString(R.string.nfc_status_read_mode)
        binding.nfcWriteEdittext.visibility = View.GONE
        binding.buttonReadMode.alpha = 1.0f
        binding.buttonWriteMode.alpha = 0.5f
    }

    private fun setupWriteMode() {
        currentMode = NfcMode.WRITE
        binding.nfcContentTextview.text = getString(R.string.nfc_status_write_mode)
        binding.nfcWriteEdittext.visibility = View.VISIBLE
        binding.buttonReadMode.alpha = 0.5f
        binding.buttonWriteMode.alpha = 1.0f
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("NfcDev", "onNewIntent called. Mode: $currentMode, Action = ${intent.action}")
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action: String? = intent.action
        Log.d("NfcDev", "Handling intent. Action: $action, Mode: $currentMode")

        if (NfcAdapter.ACTION_NDEF_DISCOVERED == action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == action) {

            val tag: Tag? = intent.getParcelableCompat(NfcAdapter.EXTRA_TAG)

            if (tag != null) {
                Log.d("NfcDev", "Tag detected: ID = ${tag.id.toHexString()}")
                Toast.makeText(this, getString(R.string.nfc_tag_detected_foreground), Toast.LENGTH_SHORT).show()

                when (currentMode) {
                    NfcMode.READ -> {
                        Log.d("NfcDev", "Mode READ: Parsing tag content.")
                        parseNdefMessageAndDisplay(tag)
                    }
                    NfcMode.WRITE -> {
                        Log.d("NfcDev", "Mode WRITE: Attempting to write.")
                        val textToWrite = binding.nfcWriteEdittext.text.toString()
                        if (textToWrite.isNotEmpty()) {
                            val message = createNdefTextMessage("fr", textToWrite)
                            if (message != null) {
                                writeNdefMessageToTag(tag, message)
                            } else {
                                Log.e("NfcWrite", "Erreur lors de la création du NdefMessage.")
                                Toast.makeText(this, "Erreur création message.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.w("NfcWrite", "Champ de texte vide. Ecriture annulée.")
                            Toast.makeText(this, getString(R.string.nfc_write_empty_warning), Toast.LENGTH_SHORT).show()
                            binding.nfcContentTextview.text = getString(R.string.nfc_status_write_mode)
                        }
                    }
                }
            } else {
                Log.e("NfcDev", "Tag object is null in intent")
                binding.nfcContentTextview.text = getString(R.string.nfc_error_tag_not_found)
            }
        } else {
            Log.d("NfcDev", "Intent not related to NFC discovery: $action")
        }
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter?.isEnabled == true) {
            setupNfcForegroundDispatch()
        } else if (nfcAdapter != null) {
            binding.nfcContentTextview.text = getString(R.string.nfc_disabled)
            binding.buttonReadMode.isEnabled = false
            binding.buttonWriteMode.isEnabled = false
            binding.nfcWriteEdittext.isEnabled = false
        }
    }

    override fun onPause() {
        super.onPause()
        disableNfcForegroundDispatch()
    }

    private fun setupNfcForegroundDispatch() {
        nfcAdapter?.let { adapter ->
            Log.d("NfcDev", "Setting up foreground dispatch for Read/Write.")

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                addCategory(Intent.CATEGORY_DEFAULT)
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                try { addDataType("*/*") } catch (e: MalformedMimeTypeException) {
                    Log.e("NfcDev", "Erreur type MIME NDEF", e); throw RuntimeException("Erreur type MIME NDEF", e) }
            }
            val tech = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED).apply { addCategory(Intent.CATEGORY_DEFAULT) }
            val tagDisc = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED).apply { addCategory(Intent.CATEGORY_DEFAULT) }

            val intentFiltersArray = arrayOf(ndef, tech, tagDisc)
            val techListsArray = arrayOf(arrayOf(Ndef::class.java.name), arrayOf(NdefFormatable::class.java.name))

            adapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
            Log.d("NfcDev", "Foreground dispatch enabled for NDEF, TECH, TAG.")
        }
    }

    private fun disableNfcForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
        Log.d("NfcDev", "Foreground dispatch disabled")
    }

    private fun createNdefTextMessage(language: String, text: String): NdefMessage? {
        if (language.length > 63) {
            Log.e("NfcWrite", "Code langue trop long: $language")
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
            Log.e("NfcWrite", "Erreur création NdefRecord texte", e)
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
                Log.w("NfcWrite", "Tag non supporté (ni NDEF ni NdefFormatable).")
                Toast.makeText(this, getString(R.string.nfc_error_not_ndef_or_formattable), Toast.LENGTH_LONG).show()
                binding.nfcContentTextview.text = getString(R.string.nfc_error_not_ndef_or_formattable)
            }
        }
    }

    private fun writeToNdefTag(ndef: Ndef, message: NdefMessage) {
        try {
            ndef.connect()
            if (!ndef.isWritable) {
                Log.w("NfcWrite", "Tag NDEF non modifiable.")
                Toast.makeText(this, getString(R.string.nfc_write_error_read_only), Toast.LENGTH_SHORT).show()
                binding.nfcContentTextview.text = getString(R.string.nfc_write_error_read_only)
                return
            }
            val maxSize = ndef.maxSize
            val messageSize = message.toByteArray().size
            if (maxSize < messageSize) {
                Log.w("NfcWrite", "Espace insuffisant NDEF ($messageSize > $maxSize).")
                val errorMsg = getString(R.string.nfc_write_error_no_space, messageSize, maxSize)
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                binding.nfcContentTextview.text = errorMsg
                return
            }

            ndef.writeNdefMessage(message)
            Log.i("NfcWrite", "Message NDEF écrit.")
            Toast.makeText(this, getString(R.string.nfc_write_success), Toast.LENGTH_SHORT).show()
            binding.nfcContentTextview.text = "${getString(R.string.nfc_write_success)}\nContenu: ${parseNdefRecordPayload(message.records[0])}"

        } catch (e: TagLostException) {
            Log.e("NfcWrite", "Tag NDEF perdu.", e)
            Toast.makeText(this, getString(R.string.nfc_write_error_tag_lost), Toast.LENGTH_SHORT).show()
            binding.nfcContentTextview.text = getString(R.string.nfc_write_error_tag_lost)
        } catch (e: IOException) {
            Log.e("NfcWrite", "Erreur I/O NDEF.", e)
            val errorMsg = getString(R.string.nfc_write_error_io, e.localizedMessage)
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.nfcContentTextview.text = errorMsg
        } catch (e: FormatException) {
            Log.e("NfcWrite", "Erreur Format NDEF.", e)
            val errorMsg = getString(R.string.nfc_write_error_format, e.localizedMessage)
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.nfcContentTextview.text = errorMsg
        } catch (e: Exception) {
            Log.e("NfcWrite", "Erreur inconnue NDEF.", e)
            val errorMsg = getString(R.string.nfc_write_error_unknown, e.localizedMessage)
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.nfcContentTextview.text = errorMsg
        } finally {
            try { ndef.close() } catch (e: IOException) { Log.w("NfcWrite", "Erreur fermeture NDEF.", e) }
        }
    }

    private fun writeToNdefFormatableTag(formatable: NdefFormatable, message: NdefMessage) {
        try {
            formatable.connect()
            formatable.format(message)
            Log.i("NfcWrite", "Tag formaté et message écrit.")
            Toast.makeText(this, getString(R.string.nfc_write_format_success), Toast.LENGTH_SHORT).show()
            binding.nfcContentTextview.text = "${getString(R.string.nfc_write_format_success)}\nContenu: ${parseNdefRecordPayload(message.records[0])}"

        } catch (e: TagLostException) {
            Log.e("NfcWrite", "Tag NdefFormatable perdu.", e)
            Toast.makeText(this, getString(R.string.nfc_write_error_tag_lost), Toast.LENGTH_SHORT).show()
            binding.nfcContentTextview.text = getString(R.string.nfc_write_error_tag_lost)
        } catch (e: IOException) {
            Log.e("NfcWrite", "Erreur I/O NdefFormatable.", e)
            val errorMsg = getString(R.string.nfc_format_error_io, e.localizedMessage)
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.nfcContentTextview.text = errorMsg
        } catch (e: FormatException) {
            Log.e("NfcWrite", "Erreur Format NdefFormatable.", e)
            val errorMsg = getString(R.string.nfc_format_error_format, e.localizedMessage)
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.nfcContentTextview.text = errorMsg
        } catch (e: Exception) {
            Log.e("NfcWrite", "Erreur inconnue NdefFormatable.", e)
            val errorMsg = getString(R.string.nfc_format_error_unknown, e.localizedMessage)
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.nfcContentTextview.text = errorMsg
        } finally {
            try { formatable.close() } catch (e: IOException) { Log.w("NfcWrite", "Erreur fermeture NdefFormatable.", e) }
        }
    }

    private fun parseNdefMessageAndDisplay(tag: Tag) {
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            binding.nfcContentTextview.text = getString(R.string.nfc_error_not_ndef)
            return
        }

        try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            if (ndefMessage == null) {
                binding.nfcContentTextview.text = getString(R.string.nfc_error_empty_tag)
                return
            }

            val builder = StringBuilder("Contenu Lu (${ndefMessage.records.size}):\n")
            ndefMessage.records.forEachIndexed { index, record ->
                val payloadString = parseNdefRecordPayload(record)
                builder.append("\n[${index+1}] $payloadString")
            }
            binding.nfcContentTextview.text = builder.toString()

        } catch (e: IOException) {
            Log.e("NfcRead", "IOException: ${e.message}", e)
            binding.nfcContentTextview.text = getString(R.string.nfc_error_io, e.localizedMessage ?: "Erreur IO")
        } catch (e: android.nfc.FormatException) {
            Log.e("NfcRead", "FormatException: ${e.message}", e)
            binding.nfcContentTextview.text = getString(R.string.nfc_error_format, e.localizedMessage ?: "Format invalide")
        } catch (e: Exception) {
            Log.e("NfcRead", "General Exception: ${e.message}", e)
            binding.nfcContentTextview.text = getString(R.string.nfc_error_generic, e.localizedMessage ?: "Erreur inconnue")
        } finally {
            try { ndef.close() } catch (e: IOException) { }
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
                } catch (e: Exception) { "Erreur texte: ${payload.toHexString()}" }
            }
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI) -> {
                try {
                    val prefix = UriPrefix.MAP.getOrDefault(payload[0].toInt(), "")
                    prefix + String(payload, 1, payload.size - 1, StandardCharsets.UTF_8)
                } catch (e: Exception) { "Erreur URI: ${payload.toHexString()}" }
            }
            else -> {
                try { payload.toString(StandardCharsets.UTF_8) }
                catch (e: CharacterCodingException) { payload.toHexString() }
                catch (e: Exception) { "Type Inconnu: ${payload.toHexString()}" }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.nfc_channel_name)
            val descriptionText = getString(R.string.nfc_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NFC_NOTIFICATION_CHANNEL_ID, name, importance).apply {
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
            // Ajoutez d'autres préfixes au besoin
        )
    }
}