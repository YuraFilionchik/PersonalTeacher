package com.example.personallangmaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.personallangmaster.ui.PersonalLangMasterApp
import com.example.personallangmaster.ui.theme.PersonalLangMasterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PersonalLangMasterTheme {
                PersonalLangMasterApp()
            }
        }
    }
}
