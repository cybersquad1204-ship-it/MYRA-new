package com.myra.assistant.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.myra.assistant.model.AppCommand
import com.myra.assistant.service.AccessibilityHelperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _commandResult = MutableLiveData<String?>()
    val commandResult: LiveData<String?> = _commandResult

    private val context: Context get() = getApplication()

    private val appPackageMap = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "telegram" to "org.telegram.messenger",
        "snapchat" to "com.snapchat.android",
        "settings" to "com.android.settings",
        "calculator" to "com.google.android.calculator",
        "calendar" to "com.google.android.calendar",
        "clock" to "com.google.android.deskclock",
        "phone" to "com.google.android.dialer",
        "contacts" to "com.google.android.contacts",
        "play store" to "com.android.vending",
        "amazon" to "in.amazon.mShop.android.shopping",
        "flipkart" to "com.flipkart.android",
        "paytm" to "net.one97.paytm",
        "phonepe" to "com.phonepe.app",
        "gpay" to "com.google.android.apps.nbu.paisa.user",
        "zoom" to "us.zoom.videomeetings",
        "meet" to "com.google.android.apps.tachyon",
        "teams" to "com.microsoft.teams",
        "tiktok" to "com.zhiliaoapp.musically",
        "discord" to "com.discord",
        "linkedin" to "com.linkedin.android"
    )

    fun executeCommand(command: AppCommand) {
        viewModelScope.launch(Dispatchers.IO) {
            when (command.type) {
                "OPEN_APP" -> openApp(command.params["app_name"] ?: "")
                "CLOSE_APP" -> {
                    if (AccessibilityHelperService.isEnabled(context)) {
                        AccessibilityHelperService.instance?.closeCurrentApp()
                        _commandResult.postValue("App band kar diya gaya hai.")
                    } else {
                        _commandResult.postValue("Accessibility service on nahi hai, pehle use enable karo.")
                    }
                }
                "PRIME_CALL" -> callPrimeContact(command.params["index"]?.toIntOrNull() ?: 0)
                "PRIME_MSG" -> messagePrimeContact(command.params["index"]?.toIntOrNull() ?: 0)
                "CALL" -> callByName(command.params["name"] ?: "")
                "SMS" -> sendSms(command.params["name"] ?: "", command.params["message"] ?: "")
                "WHATSAPP_MSG" -> sendWhatsApp(command.params["name"] ?: "", command.params["message"] ?: "")
                "WHATSAPP_CALL" -> openWhatsAppChat(command.params["name"] ?: "")
                "VOLUME_UP" -> {
                    adjustVolume(AudioManager.ADJUST_RAISE)
                    _commandResult.postValue("Volume badha diya hai.")
                }
                "VOLUME_DOWN" -> {
                    adjustVolume(AudioManager.ADJUST_LOWER)
                    _commandResult.postValue("Volume kam kar diya hai.")
                }
                "FLASHLIGHT_ON" -> setFlashlight(true)
                "FLASHLIGHT_OFF" -> setFlashlight(false)
                "WIFI_ON" -> setWifiHint(true)
                "WIFI_OFF" -> setWifiHint(false)
                "BLUETOOTH_ON" -> setBluetooth(true)
                "BLUETOOTH_OFF" -> setBluetooth(false)
                else -> _commandResult.postValue(null)
            }
        }
    }

    private fun openApp(appNameRaw: String) {
        val appName = appNameRaw.trim().lowercase()
        val pm = context.packageManager
        val pkg = appPackageMap.entries.firstOrNull { appName.contains(it.key) }?.value
        var launchIntent = pkg?.let { pm.getLaunchIntentForPackage(it) }

        if (launchIntent == null) {
            // Fallback: scan installed apps by label
            val installed = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            val match = installed.firstOrNull {
                pm.getApplicationLabel(it).toString().lowercase().contains(appName)
            }
            if (match != null) {
                launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            }
        }

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            _commandResult.postValue("$appNameRaw khol diya hai.")
        } else {
            _commandResult.postValue("$appNameRaw nahi mila.")
        }
    }

    private fun adjustVolume(direction: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun loadPrimeContactsJson(): JSONArray? {
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("prime_contacts_json", null) ?: return null
        return try { JSONArray(json) } catch (e: Exception) { null }
    }

    private fun callPrimeContact(index: Int) {
        val array = loadPrimeContactsJson()
        if (array == null || index >= array.length()) {
            _commandResult.postValue("Prime contact set nahi hai.")
            return
        }
        try {
            val num = array.getJSONObject(index).getString("number")
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            _commandResult.postValue("Prime contact ko call lagaya ja raha hai.")
        } catch (e: Exception) {
            _commandResult.postValue("Contact read karne me samasya aayi.")
        }
    }

    private fun messagePrimeContact(index: Int) {
        val array = loadPrimeContactsJson()
        if (array == null || index >= array.length()) {
            _commandResult.postValue("Prime contact set nahi hai.")
            return
        }
        try {
            val num = array.getJSONObject(index).getString("number")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$num")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            _commandResult.postValue("Prime contact ko message bhej rahe hain.")
        } catch (e: Exception) {
            _commandResult.postValue("Contact read karne me samasya aayi.")
        }
    }

    private fun resolveContactNumber(name: String): String? {
        if (name.isBlank()) return null
        val resolver = context.contentResolver
        val cursor: Cursor? = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val numIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                return it.getString(numIndex)
            }
        }
        return null
    }

    private fun callByName(name: String) {
        val number = resolveContactNumber(name)
        if (number == null) {
            _commandResult.postValue("$name naam ka contact nahi mila.")
            return
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        _commandResult.postValue("$name ko call lagaya ja raha hai.")
    }

    private fun sendSms(name: String, message: String) {
        val number = resolveContactNumber(name)
        val intent = if (number != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$number"))
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("smsto:"))
        }
        intent.putExtra("sms_body", message)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        _commandResult.postValue("SMS taiyaar kar diya hai.")
    }

    private fun sendWhatsApp(name: String, message: String) {
        val number = resolveContactNumber(name)
        if (number == null) {
            _commandResult.postValue("$name naam ka contact nahi mila.")
            return
        }
        val encoded = Uri.encode(message)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        _commandResult.postValue("WhatsApp message bhej rahe hain $name ko.")
    }

    private fun openWhatsAppChat(name: String) {
        val number = resolveContactNumber(name)
        if (number == null) {
            _commandResult.postValue("$name naam ka contact nahi mila.")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        _commandResult.postValue("WhatsApp khol rahe hain $name ke liye.")
    }

    private fun setFlashlight(on: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, on)
                _commandResult.postValue(if (on) "Torch on kar diya hai." else "Torch off kar diya hai.")
            } else {
                _commandResult.postValue("Flashlight is device me available nahi hai.")
            }
        } catch (e: Exception) {
            _commandResult.postValue("Flashlight control nahi ho paaya.")
        }
    }

    @Suppress("DEPRECATION")
    private fun setWifiHint(on: Boolean) {
        // Android 10+ restricts programmatic WiFi toggling; open panel for user action.
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled = on
            _commandResult.postValue(if (on) "WiFi on kar diya hai." else "WiFi off kar diya hai.")
        } catch (e: Exception) {
            val intent = Intent(android.provider.Settings.Panel.ACTION_WIFI).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            _commandResult.postValue("WiFi settings khol diya hai, aap khud toggle kar lo.")
        }
    }

    private fun setBluetooth(on: Boolean) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null) {
                if (on) adapter.enable() else adapter.disable()
                _commandResult.postValue(if (on) "Bluetooth on kar diya hai." else "Bluetooth off kar diya hai.")
            } else {
                _commandResult.postValue("Bluetooth is device me available nahi hai.")
            }
        } catch (e: Exception) {
            _commandResult.postValue("Bluetooth control ke liye permission chahiye.")
        }
    }

    fun acceptCall() {
        val tm = context.getSystemService(TelecomManager::class.java)
        try {
            tm.acceptRingingCall()
        } catch (_: SecurityException) {}
    }

    fun rejectCall() {
        val tm = context.getSystemService(TelecomManager::class.java)
        try {
            tm.endCall()
        } catch (_: SecurityException) {}
    }
}
