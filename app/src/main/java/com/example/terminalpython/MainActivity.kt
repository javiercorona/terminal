package com.example.terminalpython

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var outputText: TextView
    private lateinit var inputEditText: EditText
    private lateinit var runButton: Button
    private lateinit var speakButton: Button
    private lateinit var cameraButton: Button
    private lateinit var helpButton: Button
    private lateinit var clearButton: Button
    private lateinit var py: Python

    // ← New: store the URI for captured images
    private lateinit var imageUri: Uri

    companion object {
        const val AUDIO_PERMISSION_CODE = 101
        const val CAMERA_PERMISSION_CODE = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initPython()
        setupButtons()

        // Request all runtime permissions in one go:
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            CAMERA_PERMISSION_CODE
        )
    }

    private fun initViews() {
        outputText    = findViewById(R.id.outputText)
        inputEditText = findViewById(R.id.inputEditText)
        runButton     = findViewById(R.id.btnRun)
        speakButton   = findViewById(R.id.btnSpeak)
        cameraButton  = findViewById(R.id.btnCamera)
        helpButton    = findViewById(R.id.btnHelp)
        clearButton   = findViewById(R.id.btnClear)
    }

    private fun initPython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        py = Python.getInstance()
    }

    private fun setupButtons() {
        runButton.setOnClickListener {
            executeCommand(inputEditText.text.toString())
            inputEditText.text.clear()
        }
        speakButton.setOnClickListener { startVoiceInput() }
        cameraButton.setOnClickListener { captureImageForScanning() }
        helpButton.setOnClickListener   { showHelp() }
        clearButton.setOnClickListener  { outputText.text = "" }

        inputEditText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                executeCommand(inputEditText.text.toString())
                inputEditText.text.clear()
                true
            } else false
        }
    }

    private fun executeCommand(command: String) {
        if (command.isBlank()) return
        appendToOutput(">>> $command")

        // 1) Web-search interceptor
        if (command.startsWith("search ")) {
            val q = Uri.encode(command.removePrefix("search ").trim())
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com/search?q=$q")))
            return
        }

        // 2) Code-editor launcher
        if (command.startsWith("editcode ")) {
            val file = command.removePrefix("editcode ").trim()
            startActivity(
                Intent(this, CodeEditorActivity::class.java)
                    .putExtra(CodeEditorActivity.EXTRA_FILE, file)
            )
            return
        }

        // 3) Fallback to Python
        try {
            val res = py.getModule("terminal")
                .callAttr("execute_command", command)
                .toString()
            appendToOutput(res)
        } catch (e: Exception) {
            appendToOutput("Error: ${e.message}")
        }
    }

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_CODE
            )
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your command")
        }
        speechLauncher.launch(intent)
    }

    private fun captureImageForScanning() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE
            )
            return
        }
        // Create a temp file and get its URI
        val photoFile = File.createTempFile(
            "scan_${System.currentTimeMillis()}",
            ".jpg",
            getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        )
        imageUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            photoFile
        )
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        cameraLauncher.launch(intent)
    }

    private fun showHelp() {
        val helpText = py.getModule("terminal").callAttr("show_help").toString()
        appendToOutput(helpText)
    }

    private fun appendToOutput(text: String) {
        outputText.append(text + "\n")
    }

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.getOrNull(0)
            spoken?.let { inputEditText.setText(it) }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            try {
                // 1) Load the photo as an ML Kit InputImage
                val image = InputImage.fromFilePath(this, imageUri)

                // 2) Run on-device text recognition
                val recognizer = TextRecognition
                    .getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val raw = visionText.text.trim()
                        if (raw.isEmpty()) {
                            appendToOutput("No text detected.")
                            return@addOnSuccessListener
                        }

                        // 3) Show what we saw
                        appendToOutput("Detected: $raw")

                        // 4) Build a “translate” command for your Python terminal
                        val targetLang = "en"
                        val cmd = "translate $targetLang $raw"
                        appendToOutput(">>> $cmd")

                        // 5) Execute it through your Terminal.execute_command
                        val translation = py.getModule("terminal")
                            .callAttr("execute_command", cmd)
                            .toString()
                        appendToOutput(translation)
                    }
                    .addOnFailureListener { e ->
                        appendToOutput("OCR error: ${e.message}")
                    }

            } catch (e: Exception) {
                appendToOutput("Image load error: ${e.message}")
            }
        }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            AUDIO_PERMISSION_CODE -> {
                if (grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
                    startVoiceInput()
                }
            }
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
                    // Removed auto-launch of camera here.
                    appendToOutput("Camera permission granted. Tap the Camera button or type 'scan' to open it.")
                } else {
                    appendToOutput("Camera permission denied.")
                }
            }
        }
    }

}