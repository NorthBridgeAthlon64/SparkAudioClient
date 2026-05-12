package com.sleaudioclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sleaudioclient.ui.AudioClientScreen
import com.sleaudioclient.ui.theme.SleAudioClientTheme
import com.sleaudioclient.viewmodel.AudioClientViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SleAudioClientTheme {
                val vm: AudioClientViewModel = viewModel(factory = AudioClientViewModel.Factory(application))
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                AudioClientScreen(uiState = uiState, viewModel = vm)
            }
        }
    }
}