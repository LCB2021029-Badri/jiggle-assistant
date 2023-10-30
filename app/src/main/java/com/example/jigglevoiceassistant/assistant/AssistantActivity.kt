package com.example.jigglevoiceassistant.assistant

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ClipboardManager
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.MotionEvent
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.jigglevoiceassistant.R
import com.example.jigglevoiceassistant.data.AssistantDatabase
import com.example.jigglevoiceassistant.data.AssistantViewModelFactory
import com.example.jigglevoiceassistant.databinding.ActivityAssistantBinding
import java.util.Locale

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



        // databinding
        binding = DataBindingUtil.setContentView(this, R.layout.activity_assistant)
        val application = requireNotNull(this).application
        val dataSource = AssistantDatabase.getInstance(application).assistantDao
        val viewModelFactory = AssistantViewModelFactory(dataSource, application)
        assistantViewModel = ViewModelProvider(this, viewModelFactory)
            .get(AssistantViewModel::class.java)
        binding.assistantViewModel = assistantViewModel




        // recyclerview
        val adapter = AssistantAdapter()
        binding.recyclerView.adapter = adapter
        assistantViewModel.messages.observe(this, Observer {
            it?.let {
                adapter.data = it
            }
        })
        binding.setLifecycleOwner(this)




        //initializing lateinits
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            cameraID = cameraManager.cameraIdList[0] // 0 is for back camera and 1 is for front camera
        }
        catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        ringtone = RingtoneManager.getRingtone(applicationContext, RingtoneManager.getDefaultUri(
            RingtoneManager.TYPE_RINGTONE))
//        @TODO: helper = openweatherapi



        //initialzing textToSpeech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result: Int = textToSpeech.setLanguage(Locale.ENGLISH)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(logtts, "Language not supported")
                }
                else{
                    Log.e(logtts, "Language supported")
                }
            }
            else{
                Log.e(logtts, "Initialization failed")
            }
        }



        //initializing speechRecognition
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(bundle: Bundle) {}
            override fun onBeginningOfSpeech() {
                Log.d("SR", "started")
            }
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(bytes: ByteArray) {}
            override fun onEndOfSpeech() {
                Log.d("SR", "ended")
            }
            override fun onError(i: Int) {}
            //main one
            override fun onResults(bundle: Bundle) {
                // getting data
                val data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (data != null) {
                    keeper = data[0]
                    Log.d(logkeeper, keeper)
                    when {
                        keeper.contains("Hello") -> speak("Hello! How can I help you?")
                        keeper.contains("thank") -> speak("It's my job, let me know if there is something else")
                        keeper.contains("welcome") -> speak("Always for you")
                        keeper.contains("clear") -> assistantViewModel.onClear()
                        keeper.contains("date") -> getDate()
                        keeper.contains("time") -> getTime()
                        keeper.contains("exit") -> makeAPhoneCall()
                        keeper.contains("send SMS") -> sendSMS()
                        keeper.contains("read my last SMS") -> readSMS()
                        keeper.contains("open Gmail") -> openGmail()
                        keeper.contains("my location") -> openGoogle()
                        keeper.contains("open WhatsApp") -> openWhatsapp()
                        keeper.contains("open Facebook") -> openFacebook()
                        keeper.contains("open messages")-> openMessages()
                        keeper.contains("share a file") -> shareAFile()
                        keeper.contains("share text message") -> shareATextMessage()
                        keeper.contains("call") -> callContact()
                        keeper.contains("turn on Bluetooth") -> turnOnBluetooth()
                        keeper.contains("turn off Bluetooth") -> turnOffBluetooth()
                        keeper.contains("devices") -> getAllPairedDevices()
                        keeper.contains("turn on flash") -> turnOnFlash()
                        keeper.contains("turn off flash") -> turnOffFlash()
                        keeper.contains("copy to clipboard") -> clipBoardCopy()
                        keeper.contains("read last clipboard") -> clipBoardSpeak()
                        keeper.contains("capture photo") -> capturePhoto()
                        keeper.contains("play ringtone") -> playRingtone()
                        keeper.contains("stop ringtone") || keeper.contains("stop ringtone") -> stopRingtone()
                        keeper.contains("read me") -> readMe()
                        keeper.contains("wake me up tomorrow") -> setAlarm()
                        keeper.contains("weather") -> weather()
                        keeper.contains("horoscope") -> horoscope()
                        keeper.contains("do I have covid") -> speak("If You have these symtomps then you can have covid. COVID-19 affects different people in different ways. Most infected people will develop mild to moderate illness and recover without hospitalization.\n" +
                                "Most common symptoms:\n" +
                                "Fever\n" +
                                "Cough\n" +
                                "Tiredness\n" +
                                "Loss of taste or smell\n" +
                                "Less common symptoms:\n" +
                                "Sore throat\n" +
                                "Headache\n" +
                                "Aches and pains\n" +
                                "Diarrhoea\n" +
                                "A rash on skin, or discolouration of fingers or toes\n" +
                                "Red or irritated eyes\n" +
                                "Serious symptoms:\n" +
                                "Difficulty breathing or shortness of breath\n" +
                                "Loss of speech or mobility, or confusion\n" +
                                "Chest pain\n" +
                                "Seek immediate medical attention if you have serious symptoms. Always call before visiting your doctor or health facility.\n" +
                                "People with mild symptoms who are otherwise healthy should manage their symptoms at home.\n" +
                                "On average it takes 5–6 days from when someone is infected with the virus for symptoms to show, however it can take up to 14 days.")
                        keeper.contains("joke") ->speak("The biggest joke is you think you look like a hero")
                        keeper.contains("do I have  fever") -> speak("Are you sweating.\n" +
                                "Chills and shivering.\n" +
                                "Headache.\n" +
                                "Muscle aches.\n" +
                                "Loss of appetite.\n" +
                                "Irritability.\n" +
                                "Dehydration.\n" +
                                "General weakness. Then you might have a fever.")
                        keeper.contains("I have fever") || keeper.contains(" I have a fever")-> speak("If you're uncomfortable, take acetaminophen (Tylenol, others), ibuprofen (Advil, Motrin IB, others) or aspirin. Read the label carefully for proper dosage, and be careful not to take more than one medication containing acetaminophen, such as some cough and cold medicines. But Remember to visit a doctor soon!")
                        keeper.contains("medicines for fever") -> speak("If you're uncomfortable, take acetaminophen (Tylenol, others), ibuprofen (Advil, Motrin IB, others) or aspirin. Read the label carefully for proper dosage, and be careful not to take more than one medication containing acetaminophen, such as some cough and cold medicines. But Remember to visit a doctor soon!")
                        keeper.contains("I have stomach pain") || keeper.contains(" I have stomach ache")|| keeper.contains(" my stomach is paining")-> speak("For cramping from diarrhea, medicines that have loperamide (Imodium) or bismuth subsalicylate (Kaopectate or Pepto-Bismol) might make you feel better. For other types of pain, acetaminophen (Aspirin Free Anacin, Liquiprin, Panadol, Tylenol) might be helpful.")
                        keeper.contains("I have common cold please tell me what to do") || keeper.contains(" i have common cold") -> speak("Stay hydrated. Water, juice, clear broth or warm lemon water with honey helps loosen congestion and prevents dehydration. ...\n" +
                                "Rest. Your body needs rest to heal.\n" +
                                "Soothe a sore throat. ...\n" +
                                "Combat stuffiness. ...\n" +
                                "Relieve pain. ...\n" +
                                "Sip warm liquids. ...\n" +
                                "Try honey. ...\n" +
                                "Add moisture to the air.")
                        keeper.contains("how are you today") -> speak("I am fine , what about you?")
                        keeper.contains("do you know Siri") -> speak("Yes! She is my good friend and her apple family is great and Newton uncle is my uncle too")
                        keeper.contains("do you know Google assistant") -> speak("Yes she is my friend too. I learn a lot from her")
                        keeper.contains("did you have your food") -> speak("Sorry! I feed on data and your compliments")
                        keeper.contains("that was not good") -> speak("Sorry mate, I will tell you new joke and you know what is the best time to go to the dentist? Hmmmmmmmm.... toothache")
                        keeper.contains("I am fine too") -> speak("Great! Always take care of yourself.")
                        keeper.contains("how are you") -> speak("I am fine , what about you?")
                        keeper.contains("can you sing a song") -> speak("Sorry I am a very bad bathroom singer")
                        keeper.contains("how are you today") -> speak("I am fine , what about you?")
                        keeper.contains("how are you today") -> speak("I am fine , what about you?")
                        keeper.contains("hello") || keeper.contains("hi") || keeper.contains("hey") -> speak("Hello, how can I  help you?")
                        else -> speak("Sorry, I am still training on that!")
                    }

                }
            }
            override fun onPartialResults(bundle: Bundle) {}
            override fun onEvent(i: Int, bundle: Bundle) {}
        })


    }



}