package com.example.jigglevoiceassistant.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.provider.ContactsContract
import android.provider.Telephony
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.jigglevoiceassistant.R
import com.example.jigglevoiceassistant.data.AssistantDatabase
import com.example.jigglevoiceassistant.data.AssistantViewModelFactory
import com.example.jigglevoiceassistant.databinding.ActivityAssistantBinding
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
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


        // check if speech recognition available
        checkIfSpeechRecognizerAvailable()


        // holding FAB
        binding.fab1.setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    textToSpeech.stop()
                    speechRecognizer.startListening(recognizerIntent)
                }
                MotionEvent.ACTION_UP -> {
                    speechRecognizer.stopListening()
                }
            }
            false
        }



    }


    private fun checkIfSpeechRecognizerAvailable() {
        if(SpeechRecognizer.isRecognitionAvailable(this)) { Log.d(logsr, "yes") }
        else { Log.d(logsr, "false") }
    }

    fun speak(text: String) {
        // speaking TextToSpeech
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        assistantViewModel.sendMessageToDatabase(keeper, text)
    }

    fun getTime() {
        val calendar = Calendar.getInstance()
        val format = SimpleDateFormat("HH:mm:ss")
        val time: String = format.format(calendar.getTime())
        speak("The time is $time")
    }

    fun getDate() {
        val calendar = Calendar.getInstance()
        val formattedDate = DateFormat.getDateInstance(DateFormat.FULL).format(calendar.time)
        val splitDate = formattedDate.split(",").toTypedArray()
        val date = splitDate[1].trim { it <= ' ' }
        speak("The date is $date")
    }

    private fun makeAPhoneCall() {
        val keeperSplit = keeper.replace(" ".toRegex(), "").split("o").toTypedArray()
        val number = keeperSplit[2]
        if (number.trim { it <= ' ' }.length > 0) {
            if (ContextCompat.checkSelfPermission(this@AssistantActivity, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this@AssistantActivity, arrayOf(Manifest.permission.CALL_PHONE), REQUESTCALL)
            } else {
                // passing intent
                val dial = "tel:$number"
                speak("Calling $number")
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse(dial)))
            }
        } else {
            // invalid phone
            Toast.makeText(this@AssistantActivity, "Enter Phone Number", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSMS() {
        Log.d("keeper", "Done0")
        if (ContextCompat.checkSelfPermission(this@AssistantActivity, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@AssistantActivity, arrayOf(Manifest.permission.SEND_SMS), SENDSMS)
            Log.d("keeper", "Done1")
        }
        else{
            Log.d("keeper", "Done2")
            val keeperReplaced = keeper.replace(" ".toRegex(), "")
            val number = keeperReplaced.split("o").toTypedArray()[1].split("t").toTypedArray()[0]
            val message = keeper.split("that").toTypedArray()[1]
            Log.d("chk", number + message)
            val mySmsManager = SmsManager.getDefault()
            mySmsManager.sendTextMessage(number.trim { it <= ' ' }, null, message.trim { it <= ' ' }, null, null)
            speak("Message sent that $message")
        }
    }

    private fun readSMS() {
        if (ContextCompat.checkSelfPermission(this@AssistantActivity, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@AssistantActivity, arrayOf(Manifest.permission.READ_SMS), READSMS)
        }
        else {
            val cursor = contentResolver.query(Uri.parse("content://sms"), null, null, null, null)
            cursor!!.moveToFirst()
            speak("Your last message was " + cursor.getString(12))
        }
    }

    private fun openMessages() {
        val intent = packageManager.getLaunchIntentForPackage(Telephony.Sms.getDefaultSmsPackage(this))
        intent?.let { startActivity(it) }
        speak("Message Opened!")
    }

    private fun openFacebook() {
        val intent = packageManager.getLaunchIntentForPackage("com.facebook.katana")
        intent?.let { startActivity(it) }
        speak("Facebook Opened!")
    }

    private fun openWhatsapp() {
        val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
        intent?.let { startActivity(it) }
        speak("Whatsapp Opened!")
    }

    private fun openGmail() {
        val intent = packageManager.getLaunchIntentForPackage("com.google.android.gm")
        intent?.let { startActivity(it) }
        speak("Gmail Opened!")
    }

    private fun openGoogle() {
        val intent = packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
        intent?.let { startActivity(it) }
        speak("Maps Opened for your location!")
    }

    private fun shareAFile() {
        if (ContextCompat.checkSelfPermission(this@AssistantActivity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@AssistantActivity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), SHAREAFILE)
        }
        else{
            val builder = StrictMode.VmPolicy.Builder()
            StrictMode.setVmPolicy(builder.build())
            val myFileIntent = Intent(Intent.ACTION_GET_CONTENT)
            myFileIntent.type = "application/pdf"
            startActivityForResult(myFileIntent, REQUEST_CODE_SELECT_DOC)
        }
    }

    private fun shareATextMessage() {
        if (ContextCompat.checkSelfPermission(this@AssistantActivity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@AssistantActivity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), SHAREATEXTFILE)
        }
        else{
            val builder = StrictMode.VmPolicy.Builder()
            StrictMode.setVmPolicy(builder.build())
            val message = keeper.split("that").toTypedArray()[1]
            //        String subject = keeper.split("with")[1];
            val intentShare = Intent(Intent.ACTION_SEND)
            intentShare.type = "text/plain"
            //        intentShare.putExtra(Intent.EXTRA_SUBJECT,subject);
            intentShare.putExtra(Intent.EXTRA_TEXT, message)
            startActivity(Intent.createChooser(intentShare, "Sharing Text"))
        }
    }



    private fun callContact() {
        if (ContextCompat.checkSelfPermission(
                this@AssistantActivity,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this@AssistantActivity,
                arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
                READCONTACTS
            )
        }
        else
        {
            val name = keeper.split("call").toTypedArray()[1].trim { it <= ' ' }
            Log.d("chk", name)
            try {
                val cursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE),
                    "DISPLAY_NAME = '$name'", null, null)
                cursor!!.moveToFirst()
                val number = cursor.getString(0)
                // number must not have any spaces
                if (number.trim { it <= ' ' }.length > 0) {

                    // runtime message
                    if (ContextCompat.checkSelfPermission(this@AssistantActivity,
                            Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this@AssistantActivity, arrayOf(Manifest.permission.CALL_PHONE), REQUESTCALL)
                    } else {
                        // passing intent
                        val dial = "tel:$number"
                        startActivity(Intent(Intent.ACTION_CALL, Uri.parse(dial)))
                    }
                } else {
                    // invalid phone
                    Toast.makeText(this@AssistantActivity, "Enter Phone Number", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                speak("Something went wrong")
            }
        }
    }


    private fun turnOnBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            speak("Turning On Bluetooth...")
            //intent to on bluetooth
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            startActivityForResult(intent, REQUEST_ENABLE_BT)
        } else {
            speak("Bluetooth is already on")
        }
    }


    @SuppressLint("MissingPermission")
    private fun turnOffBluetooth() {
        if (bluetoothAdapter.isEnabled()) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            bluetoothAdapter.disable()
            speak("Turning Bluetooth Off")
        } else {
            speak("Bluetooth is already off")
        }
    }


    private fun getAllPairedDevices() {
        if (bluetoothAdapter.isEnabled()) {
            speak("Paired Devices are ")
            var text = ""
            var count = 1
            val devices: Set<BluetoothDevice> = bluetoothAdapter.getBondedDevices()
            for (device in devices) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                text += "\nDevice: $count ${device.name}, $device"
                count += 1
            }
            speak(text)
        } else {
            //bluetooth is off so can't get paired devices
            speak("Turn on bluetooth to get paired devices")
        }
    }







}