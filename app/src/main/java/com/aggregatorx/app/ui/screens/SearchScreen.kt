package com.aggregatorx.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.aggregatorx.app.ui.VideoPlayerActivity
import com.aggregatorx.app.ui.viewmodel.*

@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val videoExtractionState by viewModel.videoExtractionState.collectAsState()
    val context = LocalContext.current
    var pendingInAppLaunch by remember { mutableStateOf(false) }

    // Content body logic remains your existing implementation...
    // [Insert your existing ResultsFeed / Search components here]

    // Fixed Video Extraction Handler
    when (val vs = videoExtractionState) {
        is VideoExtractionState.Success -> {
            LaunchedEffect(vs.videoUrl) {
                if (pendingInAppLaunch) {
                    context.startActivity(VideoPlayerActivity.buildIntent(context, vs.videoUrl, vs.title, vs.headers))
                    pendingInAppLaunch = false
                    viewModel.resetVideoState()
                }
            }
        }
        is VideoExtractionState.Error -> {
            LaunchedEffect(vs.message) { /* Handle error visual */ }
        }
        else -> {}
    }
}
