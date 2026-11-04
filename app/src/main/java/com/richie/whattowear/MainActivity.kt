package com.richie.whattowear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.richie.whattowear.data.LocationHelper
import com.richie.whattowear.data.Settings
import com.richie.whattowear.reminder.Reminders
import com.richie.whattowear.ui.HistoryScreen
import com.richie.whattowear.ui.LogScreen
import com.richie.whattowear.ui.TodayScreen
import com.richie.whattowear.ui.WhatToWearTheme
import com.richie.whattowear.widget.WidgetUpdates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Background jobs: keep the home-screen widget fresh, and the 5pm reminder.
        WidgetUpdates.schedule(this)
        // Off the main thread: it reads the job list (and re-times the reminder if the clocks changed).
        lifecycleScope.launch(Dispatchers.IO) { Reminders.schedule(applicationContext) }
        setContent {
            WhatToWearTheme {
                App(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshIfStale()
        // The note may have changed on the Mac while the app was in the background.
        vm.reloadEntries()
    }
}

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Default.Home),
    LOG("Log", Icons.Default.Add),
    HISTORY("History", Icons.AutoMirrored.Filled.List),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    val snackbar = remember { SnackbarHostState() }

    // Ask for location only when it's needed, then carry on with whatever needed it.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) afterPermission?.invoke() else vm.locationDenied()
        afterPermission = null
    }
    val withLocation: (() -> Unit) -> Unit = { action ->
        if (LocationHelper.hasPermission(context)) {
            action()
        } else {
            afterPermission = action
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    // Android's own file picker. Picking the note gives the app lasting permission to read and write just that file.
    val notePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.connectNote(uri)
    }
    val connectNote = { notePicker.launch(arrayOf("*/*")) }

    // The reminder needs permission to show notifications (Android 13 and later). Ask once, on first open.
    val notificationAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val settings = Settings(context)
        if (vm.reminderOn && !Reminders.canNotify(context) && !settings.askedForNotifications && Build.VERSION.SDK_INT >= 33) {
            settings.askedForNotifications = true
            notificationAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        if (vm.forecast == null) {
            if (vm.usingGps) withLocation { vm.refresh() } else vm.refresh()
        }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    "WHAT TO WEAR",
                    style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp),
                )
            })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(
                    vm = vm,
                    onUseMyLocation = { withLocation { vm.useMyLocation() } },
                    onLogOutfit = { tab = Tab.LOG },
                    onConnectNote = connectNote,
                )
                Tab.LOG -> LogScreen(vm = vm, onUseMyLocation = { withLocation { vm.useMyLocationForDraft() } })
                Tab.HISTORY -> HistoryScreen(vm = vm, onConnectNote = connectNote)
            }
        }
    }
}
