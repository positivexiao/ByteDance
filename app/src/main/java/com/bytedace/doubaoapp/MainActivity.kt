package com.bytedace.doubaoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bytedace.doubaoapp.ui.theme.DoubaoAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DoubaoApp

        setContent {
            DoubaoAppTheme {
                DoubaoAppContent(app)
            }
        }
    }
}
