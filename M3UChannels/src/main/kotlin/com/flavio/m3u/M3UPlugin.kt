package com.flavio.m3u

import android.content.Context
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class M3UPlugin : Plugin() {
    private var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        val appContext = context.applicationContext
        activity = context as? AppCompatActivity
        registerMainAPI(M3UProvider(appContext))

        openSettings = {
            activity?.let { host ->
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val input = EditText(host).apply {
                    hint = "http://servidor/playlist.m3u"
                    setSingleLine(true)
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    setText(prefs.getString(PLAYLIST_KEY, "") ?: "")
                }

                AlertDialog.Builder(host)
                    .setTitle("Lista M3U")
                    .setMessage("Pega la URL de tu lista. Se guardará en este dispositivo.")
                    .setView(input)
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Guardar") { _, _ ->
                        prefs.edit().putString(PLAYLIST_KEY, input.text.toString().trim()).apply()
                    }
                    .show()
            }
        }
    }

    companion object {
        const val PREFS_NAME = "m3u_channels_settings"
        const val PLAYLIST_KEY = "playlist_url"
    }
}
