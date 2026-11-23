package com.logicart.bari

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.logicart.bari.ui.home.BariHomeScreen
import com.logicart.bari.ui.theme.BariTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BariTheme {
                BariHomeScreen()
            }
        }
    }
}
