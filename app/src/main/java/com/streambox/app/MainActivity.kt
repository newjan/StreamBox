package com.streambox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.streambox.app.ui.AppNavHost
import com.streambox.app.ui.MainViewModel
import com.streambox.app.ui.isTelevision
import com.streambox.app.ui.theme.StreamBoxTheme
import dagger.hilt.android.AndroidEntryPoint

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTv = isTelevision()
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val darkTheme by viewModel.themeDark.collectAsStateWithLifecycle()
            StreamBoxTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost(isTv = isTv)
                }
            }
        }
    }
}
