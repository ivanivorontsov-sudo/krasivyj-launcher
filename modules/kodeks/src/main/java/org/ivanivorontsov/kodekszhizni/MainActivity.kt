package org.ivanivorontsov.kodekszhizni

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.ivanivorontsov.kodekszhizni.ui.screens.ArticleEditScreen
import org.ivanivorontsov.kodekszhizni.ui.screens.ArticleListScreen
import org.ivanivorontsov.kodekszhizni.ui.screens.ArticleViewModel
import org.ivanivorontsov.kodekszhizni.ui.screens.MeasureListScreen
import org.ivanivorontsov.kodekszhizni.ui.screens.MeasureViewModel
import org.ivanivorontsov.kodekszhizni.ui.screens.SettingsScreen
import org.ivanivorontsov.kodekszhizni.ui.screens.SettingsViewModel
import org.ivanivorontsov.kodekszhizni.ui.theme.KodeksTheme

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotifications()
        val app = KodeksApp.init(this)
        setContent {
            KodeksTheme {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route.orEmpty()
                val showBar = route in listOf("codex", "measures", "settings")
                val articleVm: ArticleViewModel = viewModel(
                    factory = ArticleViewModel.factory(app.repository)
                )
                val measureVm: MeasureViewModel = viewModel(
                    factory = MeasureViewModel.factory(app.repository)
                )
                val settingsVm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(app.settingsStore, applicationContext)
                )
                Scaffold(
                    bottomBar = {
                        if (showBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = route == "codex",
                                    onClick = {
                                        nav.navigate("codex") {
                                            popUpTo("codex") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.AutoStories, null) },
                                    label = { Text("Кодекс") }
                                )
                                NavigationBarItem(
                                    selected = route == "measures",
                                    onClick = {
                                        nav.navigate("measures") {
                                            popUpTo("codex")
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Gavel, null) },
                                    label = { Text("Меры") }
                                )
                                NavigationBarItem(
                                    selected = route == "settings",
                                    onClick = {
                                        nav.navigate("settings") {
                                            popUpTo("codex")
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Settings, null) },
                                    label = { Text("Настройки") }
                                )
                            }
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = nav,
                        startDestination = "codex",
                        modifier = Modifier.padding(padding)
                    ) {
                        composable("codex") {
                            ArticleListScreen(
                                vm = articleVm,
                                onOpen = { id -> nav.navigate("article/$id") },
                                onCreate = { nav.navigate("article/0") }
                            )
                        }
                        composable(
                            "article/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType })
                        ) { entry ->
                            val id = entry.arguments?.getLong("id") ?: 0L
                            ArticleEditScreen(
                                articleId = id,
                                vm = articleVm,
                                onBack = { nav.popBackStack() }
                            )
                        }
                        composable("measures") {
                            MeasureListScreen(vm = measureVm)
                        }
                        composable("settings") {
                            SettingsScreen(vm = settingsVm)
                        }
                    }
                }
            }
        }
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
