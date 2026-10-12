package org.ivanivorontsov.mudrost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import org.ivanivorontsov.mudrost.ui.MudrostApp
import org.ivanivorontsov.mudrost.ui.MudrostTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MudrostTheme { MudrostApp(vm) { finish() } } }
    }
}
