package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NEED_ACCESS = "Pehle Settings mein Jarvis ka Accessibility ON kijiye."

class CommandProcessor(private val ctx: Context) {

    // Sikhi hui baatein yahan save hongi (phone band karne ke baad bhi)
    private val memory = ctx.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)

    // null return = ye phone ka command nahi hai, AI se poochna hai
    fun handle(raw: String): String? {
        var t = raw.lowercase(Locale.getDefault()).replace("jarvis", "").trim()
        if (t.isEmpty()) return "Haan, boliye."

        // 1) SIKHANA: "yaad rakho insta = instagram"
        Regex("yaad rakho (.+?) (=|matlab) (.+)").find(t)?.let { m ->
            val key = m.groupValues[1].trim()
            val value = m.groupValues[3].trim()
            memory.edit().putString(key, value).apply()
            return "Theek hai, ye yaad rakh liya. $key bolne par $value hoga."
        }

        // 2) Yaad ki hui baat ko asli command se badlo
        for ((key, value) in memory.all) {
            if (key.isNotBlank() && value is String && t.contains(key)) {
                t = t.replace(key, value)
            }
        }

        // 3) Phone ke commands
        return when {
            t.contains("youtube") && (t.contains("chalao") || t.contains("dhundo") || t.contains("search")) ->
                youtube(t)

            t.contains("skip") -> skipAd()

            t.contains("samay") || t.contains("time kya") || t.contains("baje") ->
                "Abhi " + SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date()) + " baje hain."

            t.contains("date kya") || t.contains("tareekh") ->
                "Aaj ki date hai " + SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date())

            t.contains("back") || t.contains("peeche") ->
                global(AccessibilityService.GLOBAL_ACTION_BACK, "Peeche chala gaya.")

            t.contains("home screen") || t.contains("home jao") ->
                global(AccessibilityService.GLOBAL_ACTION_HOME, "Home screen par aa gaya.")

            t.contains("notification") ->
                global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Notifications khol di.")

            t.contains("volume badhao") || t.contains("awaaz badhao") -> volume(true)
            t.contains("volume kam") || t.contains("awaaz kam") -> volume(false)

            t.contains("wifi") || t.contains("wai fai") ->
                settings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi settings khol di.")

            t.contains("bluetooth") ->
                settings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth settings khol di.")

            t.contains("display") || t.contains("brightness") ->
                settings(Settings.ACTION_DISPLAY_SETTINGS, "Display settings khol di.")

            t.contains("settings") ->
                settings(Settings.ACTION_SETTINGS, "Settings khol di.")

            t.startsWith("open ") || t.endsWith("kholo") || t.endsWith("khol do") ->
                openApp(t.replace("open", "").replace("kholo", "").replace("khol do", "").trim())

            t.contains("tum kaun") || t.contains("aap kaun") ->
                "Main Jarvis hoon, aapka phone assistant."

            // Baaki sab (general gyaan) Gemini AI ko jayega
            else -> null
        }
    }

    private fun openApp(name: String): String {
        if (name.isBlank()) return "Kaunsa app kholna hai?"
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val app = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase(Locale.getDefault()).contains(name)
        } ?: return "$name naam ka app nahi mila."

        val intent = pm.getLaunchIntentForPackage(app.activityInfo.packageName)
            ?: return "Ye app nahi khul sakta."
        start(intent)
        return "${app.loadLabel(pm)} khol raha hoon."
    }

    private fun youtube(t: String): String {
        val q = Regex("youtube (?:par|pe|mein|me) (.+)").find(t)?.groupValues?.get(1)
            ?.replace("chalao", "")
            ?.replace("dhundo", "")
            ?.replace("search karo", "")
            ?.trim() ?: ""
        start(Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
        return if (q.isEmpty()) "YouTube khol raha hoon." else "YouTube par $q dhoondh raha hoon."
    }

    private fun skipAd(): String {
        val svc = JarvisAccessibilityService.instance ?: return NEED_ACCESS
        val clicked = svc.clickByText(listOf("Skip ad", "Skip Ad", "Skip", "Skip Ads"))
        return if (clicked) "Ad skip kar diya." else "Skip button abhi screen par nahi dikh raha."
    }

    private fun global(action: Int, msg: String): String {
        val svc = JarvisAccessibilityService.instance ?: return NEED_ACCESS
        svc.performGlobalAction(action)
        return msg
    }

    private fun settings(action: String, msg: String): String {
        start(Intent(action))
        return msg
    }

    private fun volume(up: Boolean): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
        return if (up) "Volume badha diya." else "Volume kam kar diya."
    }

    private fun start(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}
