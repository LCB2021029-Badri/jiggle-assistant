package com.example.jigglevoiceassistant.assistant

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ClipboardManager
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.Ringtone
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.jigglevoiceassistant.R
import com.example.jigglevoiceassistant.data.AssistantDatabase
import com.example.jigglevoiceassistant.data.AssistantViewModelFactory
import com.example.jigglevoiceassistant.databinding.ActivityAssistantBinding

class AssistantActivity : AppCompatActivity() {


    private lateinit var binding: ActivityAssistantBinding
    private lateinit var assistantViewModel : AssistantViewModel

    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var keeper : String

    // log statements
    private val logtts = "TTS"
    private val logsr = "SR"
    private val logkeeper = "keeper"

    private var REQUESTCALL = 1
    private var SENDSMS = 2
    private var READSMS = 3
    private var SHAREAFILE = 4
    private var SHAREATEXTFILE = 5
    private var READCONTACTS = 6
    private var CAPTUREPHOTO = 7

    private val REQUEST_CODE_SELECT_DOC: Int = 100
    private val REQUEST_ENABLE_BT = 101

    private var bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private lateinit var cameraManager : CameraManager
    private lateinit var clipboardManager : ClipboardManager
    private lateinit var cameraID : String
    private lateinit var ringtone : Ringtone

    private var imageIndex: Int = 0
    private lateinit var imgUri : Uri
    @Suppress("DEPRECATION")
    private val imageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/assistant/"

//    @TODO weather api
//    @TODO horoscope api

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assistant)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_assistant)


        val application = requireNotNull(this).application
        val dataSource = AssistantDatabase.getInstance(application).assistantDao
        val viewModelFactory = AssistantViewModelFactory(dataSource, application)

        assistantViewModel = ViewModelProvider(this, viewModelFactory)
            .get(AssistantViewModel::class.java)
        binding.assistantViewModel = assistantViewModel

        val adapter = AssistantAdapter()
        binding.recyclerView.adapter = adapter
        assistantViewModel.messages.observe(this, Observer {
            it?.let {
                adapter.data = it
            }
        })
        binding.setLifecycleOwner(this)


    }
}