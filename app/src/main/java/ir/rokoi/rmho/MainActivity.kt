package ir.rokoi.rmho

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ir.rokoi.rmho.ui.AppRoot
import ir.rokoi.rmho.ui.theme.RmhoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RmhoTheme {
                AppRoot()
            }
        }
    }
}
