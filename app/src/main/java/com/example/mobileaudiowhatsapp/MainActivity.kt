package com.example.mobileaudiowhatsapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.mobileaudiowhatsapp.theme.MobileAudioWhatsappTheme
import java.io.File

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    cleanOrphanedPcmFiles(this)

    enableEdgeToEdge()
    setContent {
      MobileAudioWhatsappTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }

  private fun cleanOrphanedPcmFiles(context: Context) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val watchDir = prefs.getString("watch_dir", null) ?: return
    File(watchDir).listFiles { f ->
        f.name.endsWith("_pcm.wav")
    }?.forEach { it.delete() }

    context.cacheDir.listFiles { f ->
        f.name.endsWith("_pcm.wav")
    }?.forEach { it.delete() }
  }
}
