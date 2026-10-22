package com.atlas.intelligence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.atlas.intelligence.ui.AtlasApp

class MainActivity : ComponentActivity() {
    private val viewModel: AtlasViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AtlasApp(viewModel) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshIfStale()
    }
}
