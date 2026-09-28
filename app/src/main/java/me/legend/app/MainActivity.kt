package me.legend.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import me.legend.app.core.designsystem.LegendMeTheme
import me.legend.app.core.navigation.LegendMeApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LegendMeTheme {
                LegendMeApp()
            }
        }
    }
}
