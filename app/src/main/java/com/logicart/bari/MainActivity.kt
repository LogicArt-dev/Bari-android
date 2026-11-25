package com.logicart.bari

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.logicart.bari.features.access.AccessViewModel
import com.logicart.bari.features.browser.BrowserViewModel
import com.logicart.bari.navigation.BariNavHost
import com.logicart.bari.ui.theme.BariTheme

class MainActivity : ComponentActivity() {
    private val bariApplication: BariApplication
        get() = application as BariApplication

    private val accessViewModel by viewModels<AccessViewModel> {
        BariViewModelFactory(bariApplication)
    }

    private val browserViewModel by viewModels<BrowserViewModel> {
        BariViewModelFactory(bariApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            BariTheme {
                BariNavHost(accessViewModel, browserViewModel)
            }
        }
    }
}

private class BariViewModelFactory(
    private val application: BariApplication,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel = when {
            modelClass.isAssignableFrom(AccessViewModel::class.java) ->
                AccessViewModel(application.storageRepository)
            modelClass.isAssignableFrom(BrowserViewModel::class.java) ->
                BrowserViewModel(application.storageRepository)
            else -> throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
        }
        return viewModel as T
    }
}

