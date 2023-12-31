package com.example.jigglevoiceassistant.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.provider.ContactsContract
import android.provider.MediaStore
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
import com.example.jigglevoiceassistant.model.HoroscopeResponse
import com.example.jigglevoiceassistant.util.HoroscopeService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kwabenaberko.openweathermaplib.constant.Units
import com.kwabenaberko.openweathermaplib.implementation.OpenWeatherMapHelper
import com.kwabenaberko.openweathermaplib.implementation.callback.CurrentWeatherCallback
import com.kwabenaberko.openweathermaplib.model.currentweather.CurrentWeather
import com.ml.quaterion.text2summary.Text2Summary
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.jvm.internal.Ref

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

    private val HOROSCOPE_URL = "https://newastro.vercel.app"
    private val WEATHER_API_KEY = "e2f18082ff618ac5a563a175973a3360"

    private var bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private lateinit var cameraManager : CameraManager
    private lateinit var clipboardManager : ClipboardManager
    private lateinit var cameraID : String
    private lateinit var ringtone : Ringtone

    private var imageIndex: Int = 0
    private lateinit var imgUri : Uri
    @Suppress("DEPRECATION")
    private val imageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/assistant/"

    private lateinit var helper: OpenWeatherMapHelper

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
        helper = OpenWeatherMapHelper(WEATHER_API_KEY)



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

                        // not working
                        keeper.contains("send SMS") -> sendSMS()
                        keeper.contains("share file") -> shareAFile()
                        keeper.contains("share text message") -> shareATextMessage()
                        keeper.contains("call") -> callContact()
                        keeper.contains("copy to clipboard") -> clipBoardCopy()
                        keeper.contains("read last clipboard") -> clipBoardSpeak()
                        keeper.contains("read me") -> readMe()
                        keeper.contains("wake me up tomorrow") -> setAlarm()

                        //working
                        keeper.contains("weather") -> weather()
                        keeper.contains("horoscope") -> horoscope()
                        keeper.contains("clear") -> assistantViewModel.onClear()
                        keeper.contains("date") -> getDate()
                        keeper.contains("time") -> getTime()
                        keeper.contains("exit") -> makeAPhoneCall()
                        keeper.contains("my location") -> openGoogle()
                        keeper.contains("open Gmail") -> openGmail()
                        keeper.contains("open WhatsApp") -> openWhatsapp()
                        keeper.contains("open Facebook") -> openFacebook()
                        keeper.contains("open messages")-> openMessages()
                        keeper.contains("read my last SMS") -> readSMS()
                        keeper.contains("turn on Bluetooth") -> turnOnBluetooth()
                        keeper.contains("turn off Bluetooth") -> turnOffBluetooth()
                        keeper.contains("bluetooth devices") -> getAllPairedDevices()
                        keeper.contains("turn on flash") -> turnOnFlash()
                        keeper.contains("turn off flash") -> turnOffFlash()
                        keeper.contains("capture photo") || keeper.contains("open camera") -> capturePhoto()
                        keeper.contains("play ringtone") -> playRingtone()
                        keeper.contains("stop ringtone") -> stopRingtone()


                        // health
                        keeper.contains("do I have  fever") -> speak("Are you sweating.\n" + "Chills and shivering.\n" + "Headache.\n" + "Muscle aches.\n" + "Loss of appetite.\n" + "Irritability.\n" + "Dehydration.\n" + "General weakness. Then you might have a fever.")
                        keeper.contains("I have fever") || keeper.contains(" I have a fever")-> speak("If you're uncomfortable, take acetaminophen (Tylenol, others), ibuprofen (Advil, Motrin IB, others) or aspirin. Read the label carefully for proper dosage, and be careful not to take more than one medication containing acetaminophen, such as some cough and cold medicines. But Remember to visit a doctor soon!")
                        keeper.contains("medicines for fever") -> speak("If you're uncomfortable, take acetaminophen (Tylenol, others), ibuprofen (Advil, Motrin IB, others) or aspirin. Read the label carefully for proper dosage, and be careful not to take more than one medication containing acetaminophen, such as some cough and cold medicines. But Remember to visit a doctor soon!")
                        keeper.contains("I have stomach pain") || keeper.contains(" I have stomach ache")|| keeper.contains(" my stomach is paining")-> speak("For cramping from diarrhea, medicines that have loperamide (Imodium) or bismuth subsalicylate (Kaopectate or Pepto-Bismol) might make you feel better. For other types of pain, acetaminophen (Aspirin Free Anacin, Liquiprin, Panadol, Tylenol) might be helpful.")
                        keeper.contains("I have common cold please tell me what to do") || keeper.contains(" i have common cold") -> speak("Stay hydrated. Water, juice, clear broth or warm lemon water with honey helps loosen congestion and prevents dehydration. ...\n" + "Rest. Your body needs rest to heal.\n" + "Soothe a sore throat. ...\n" + "Combat stuffiness. ...\n" + "Relieve pain. ...\n" + "Sip warm liquids. ...\n" + "Try honey. ...\n" + "Add moisture to the air.")

                        // interaction
                        keeper.contains("how are you today") -> speak("I am fine , what about you?")
                        keeper.contains("do you know Siri") -> speak("Yes! She is my good friend and her apple family is great and Newton uncle is my uncle too")
                        keeper.contains("do you know Google assistant") -> speak("Yes she is my friend too. I learn a lot from her")
                        keeper.contains("did you have your food") -> speak("Sorry! I feed on data and your compliments")
                        keeper.contains("that was not good") -> speak("Sorry mate, I will tell you new joke and you know what is the best time to go to the dentist? Hmmmmmmmm.... toothache")
                        keeper.contains("I am fine too") -> speak("Great! Always take care of yourself.")
                        keeper.contains("how are you") -> speak("I am fine , what about you?")
                        keeper.contains("can you sing a song") -> speak("Sorry I am a very bad bathroom singer")
                        keeper.contains("how are you today") -> speak("I am fine , what about you?")
                        keeper.contains("hello") || keeper.contains("hi") || keeper.contains("hey") -> speak("Hello, how can I  help you?")
                        keeper.contains("creator") -> speak("Badri Akkala created me. He is my father and mother. He is my only family.")
                        keeper.contains("parent") -> speak("Badri Akkala created me. He is my father and mother. He is my only family.")
                        keeper.contains("father") -> speak("Badri Akkala created me. He is my father and mother. He is my only family.")
                        keeper.contains("mother") -> speak("Badri Akkala created me. He is my father and mother. He is my only family.")
                        keeper.contains("family") -> speak("Badri Akkala created me. He is my father and mother. He is my only family.")
                        keeper.contains("thank") -> speak("It's my job, let me know if there is something else")
                        keeper.contains("welcome") -> speak("Always for you")
                        keeper.contains("God exist") -> speak("God is one who created us. Badri Akkala created me, so God exists")
                        keeper.contains("welcome") -> speak("Always for you")
                        keeper.contains("joke") ->speak("The biggest joke is you think Badri Akkala likes you. He only loves me! Haa haa haa. Now go away and cry.")
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

    private fun turnOnFlash() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraID, true)
                speak("Flash turned on")
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            speak("Error Occured")
        }
    }

    private fun turnOffFlash() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraID, false)
                speak("Flash turned off")
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    fun clipBoardCopy() {
        val data = keeper.split("that").toTypedArray()[1].trim { it <= ' ' }
        if (!data.isEmpty()) {
            val clipData = ClipData.newPlainText("text", data)
            clipboardManager.setPrimaryClip(clipData)
            speak("Data copied to clipboard that is $data")
        }
    }

    fun clipBoardSpeak() {
        val item = clipboardManager.primaryClip!!.getItemAt(0)
        val pasteData = item.text.toString()
        if (pasteData != "") {
            speak("Data stored in last clipboard is " + pasteData)
        } else {
            speak("Clipboard is Empty")
        }
    }

    private fun capturePhoto() {
        if (ContextCompat.checkSelfPermission(
                this@AssistantActivity,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this@AssistantActivity,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                CAPTUREPHOTO
            )
        }
        else
        {
            val builder = StrictMode.VmPolicy.Builder()
            StrictMode.setVmPolicy(builder.build())
            imageIndex++
            val file: String = imageDirectory + imageIndex + ".jpg"
            val newFile = File(file)
            try {
                newFile.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            val outputFileUri = Uri.fromFile(newFile)
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri)
            startActivity(cameraIntent)
            speak("Photo  will be saved to $file")
        }
    }

    private fun playRingtone() {
        speak("Ringtone playing")
        ringtone.play()
    }

    private fun stopRingtone() {
        speak("Ringtone stopped")
        ringtone.stop()
    }

    private fun readMe() {
        //read image
        CropImage.startPickImageActivity(this@AssistantActivity)
    }


    private fun setAlarm() {
        val intent = packageManager.getLaunchIntentForPackage("com.android.alarm")
        intent?.let { startActivity(it) }
    }

    private fun weather() {
        if(keeper.contains("Fahrenheit")) {
            helper.setUnits(Units.IMPERIAL)
        }
        else if(keeper.contains("Celsius")) {
            helper.setUnits(Units.METRIC)
        }
        val keeperSplit = keeper.replace(" ".toRegex(), "").split("weather").toTypedArray()
        val city = keeperSplit[0]
        Log.d("chk","the city is" + keeperSplit)

//        helper.getCurrentWeatherByCityName("Lucknow", object : CurrentWeatherCallback {
        helper.getCurrentWeatherByCityName(city, object : CurrentWeatherCallback {
            override fun onSuccess(currentWeather: CurrentWeather) {
                speak("""
    Coordinates: ${currentWeather.coord.lat}, ${currentWeather.coord.lon}
    Weather Description: ${currentWeather.weather[0].description}
    Temperature: ${currentWeather.main.tempMax}
    Wind Speed: ${currentWeather.wind.speed}
    City, Country: ${currentWeather.name}, ${currentWeather.sys.country}
    """.trimIndent()
                )
            }

            override fun onFailure(throwable: Throwable) {
                speak("Error " + throwable.message)
            }
        })
    }


    private fun horoscope() {
        val retrofit = Retrofit.Builder()
            .baseUrl(HOROSCOPE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(HoroscopeService::class.java)

        val date = "2023-10-30"
        val language = "en"
        val keeperSplit = keeper.replace(" ".toRegex(), "").split("horoscope").toTypedArray()
        val zodiacSign = keeperSplit[0]

        val response = service.getZodiacHoroscope(zodiacSign, date, language)
        response.enqueue(object : Callback<HoroscopeResponse> {
            override fun onResponse(call: Call<HoroscopeResponse>, response: Response<HoroscopeResponse>) {
                if (response.isSuccessful) {
                    val horoscope = response.body()?.horoscope
                    if (horoscope != null) {
                        speak(zodiacSign + ", " + horoscope)
                    } else {
                        speak("Horoscope data not available.")
                    }
                } else {
                    speak("Failed to retrieve horoscope data.")
                }
            }

            override fun onFailure(call: Call<HoroscopeResponse>, t: Throwable) {
                speak("Error: ${t.message}")
            }
        })
    }


    private fun joke()
    {

    }

    private fun question()
    {

    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUESTCALL) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // when permission granted
                makeAPhoneCall()
            } else {
                // permission denied
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
        else if(requestCode == SENDSMS)
        {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // when permission granted
                sendSMS()
            } else {
                // permission denied
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
        else if(requestCode == READSMS)
        {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // when permission granted
                readSMS()
            } else {
                // permission denied
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
        else if(requestCode == SHAREAFILE)
        {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // when permission granted
                shareAFile()
            } else {
                // permission denied
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
        else if(requestCode == SHAREATEXTFILE)
        {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // when permission granted
                shareATextMessage()
            } else {
                // permission denied
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
        else if(requestCode == READCONTACTS)
        {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // when permission granted
                callContact()
            } else {
                // permission denied
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
        else if(requestCode == CAPTUREPHOTO)
        {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // when permission granted
                capturePhoto()
            } else {
                // permission denied
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SELECT_DOC && resultCode == RESULT_OK) {
            val filePath = data!!.data!!.path
            Log.d("chk", "path: $filePath")
            val file= File(filePath)
            val intentShare = Intent(Intent.ACTION_SEND)
            intentShare.type = "application/pdf"
            intentShare.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://$file"))
            startActivity(Intent.createChooser(intentShare, "Share the file ..."))
        }
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                speak("Bluetooth is on")
            } else {
                speak("Could'nt turn on bluetooth")
            }
        }

        if (requestCode == CropImage.PICK_IMAGE_CHOOSER_REQUEST_CODE && resultCode == RESULT_OK) {
            val imageUri = CropImage.getPickImageResultUri(this, data)
            imgUri = imageUri
            startCrop(imageUri)
        }

        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            val result : CropImage.ActivityResult = CropImage.getActivityResult(data)
            if (resultCode == RESULT_OK) {
                imgUri = result.uri
                try {
                    val inputStream = contentResolver.openInputStream(imgUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    getTextFromBitmap(bitmap)
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                }
                Toast.makeText(this, "Image captured successfully !", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun startCrop(imageUri: Uri) {
        CropImage.activity(imageUri).setGuidelines(CropImageView.Guidelines.ON).setMultiTouchEnabled(true).start(this@AssistantActivity)
    }

    private fun getTextFromBitmap(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val result = recognizer.process(image)
            .addOnSuccessListener { visionText ->
                // Task completed successfully
                // ...
                val resultText = visionText.text
                if(keeper.contains("summarise"))
                {
                    speak("Reading Image and Summarising it :\n" + summariseText(resultText))
                }
                else {
                    speak("Reading Image:\n" + resultText)

                }
            }
            .addOnFailureListener { e ->
                // Task failed with an exception
                // ...
                Toast.makeText(this@AssistantActivity, "Error: " + e.message, Toast.LENGTH_SHORT).show()
            }
    }

    private fun summariseText(text: String): String? {
        val summary: Ref.ObjectRef<*> = Ref.ObjectRef<Any?>()
        summary.element = Text2Summary.Companion.summarize(text, 0.4f) as Nothing?
        return summary.element as String
    }


    override fun onDestroy() {
        super.onDestroy()
        // destroying
        textToSpeech.stop()
        textToSpeech.shutdown()
        speechRecognizer.cancel()
        speechRecognizer.destroy()
        Log.i(logsr, "destroy")
        Log.i(logtts, "destroy")
    }
    
}