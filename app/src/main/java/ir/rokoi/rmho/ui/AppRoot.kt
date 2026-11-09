package ir.rokoi.rmho.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ir.rokoi.rmho.BuildConfig
import ir.rokoi.rmho.data.DataBundle
import ir.rokoi.rmho.data.DataRepository
import ir.rokoi.rmho.data.RemoteVersion
import ir.rokoi.rmho.data.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val Destinations = listOf(
    Destination("home", "Home", Icons.Filled.Home),
    Destination("adventurers", "Adventurers", Icons.Filled.Person),
    Destination("monsters", "Monsters", Icons.Filled.Pets),
    Destination("teams", "Teams", Icons.Filled.Groups),
    Destination("coach", "Coach", Icons.Filled.School)
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bundle by remember { mutableStateOf<DataBundle?>(null) }
    var updateStatus by remember { mutableStateOf("Updates: not checked yet") }
    var remote by remember { mutableStateOf<RemoteVersion?>(null) }
    var showApkDialog by remember { mutableStateOf(false) }
    var dlProgress by remember { mutableStateOf<Int?>(null) }

    fun checkUpdates(manual: Boolean) {
        val repo = DataRepository(context)
        scope.launch {
            if (manual) updateStatus = "Checking for updates…"
            val version = withContext(Dispatchers.IO) { UpdateManager.fetchVersion() }
            if (version == null) {
                if (manual) updateStatus = "Update check failed — check your connection."
                return@launch
            }
            var status = "You're up to date · app ${BuildConfig.VERSION_NAME}"
            val current = bundle
            if (current != null && version.dataVersion > current.dataVersion) {
                val fresh = withContext(Dispatchers.IO) { UpdateManager.fetchBundle() }
                if (fresh != null) {
                    withContext(Dispatchers.IO) { repo.cacheBundle(fresh) }
                    bundle = fresh
                    status = "Data updated to v${fresh.dataVersion} · app ${BuildConfig.VERSION_NAME}"
                }
            } else if (current != null) {
                status = "You're up to date · data v${current.dataVersion} · app ${BuildConfig.VERSION_NAME}"
            }
            updateStatus = status
            if (version.apkVersionCode > BuildConfig.VERSION_CODE) {
                remote = version
                showApkDialog = true
            }
        }
    }

    fun startApkUpdate() {
        val v = remote ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            updateStatus = "Allow “install unknown apps” for Rmho, then tap Update again."
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }
        scope.launch {
            dlProgress = 0
            val dest = File(context.cacheDir, "updates/rmho-update.apk")
            val ok = withContext(Dispatchers.IO) {
                UpdateManager.downloadFile(v.apkUrl, dest) { p -> dlProgress = p }
            }
            dlProgress = null
            if (ok) {
                showApkDialog = false
                updateStatus = "Opening the installer…"
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", dest
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                context.startActivity(intent)
            } else {
                updateStatus = "Update download failed — try again."
            }
        }
    }

    LaunchedEffect(Unit) {
        bundle = withContext(Dispatchers.IO) {
            runCatching { DataRepository(context).loadLocal() }.getOrNull()
        }
        checkUpdates(manual = false)
    }

    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val current = backStack?.destination?.route
            NavigationBar {
                Destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = current == dest.route ||
                            (dest.route == "adventurers" && current?.startsWith("adventurer") == true) ||
                            (dest.route == "monsters" && current?.startsWith("monster") == true),
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = {
                            Text(
                                dest.label,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val data = bundle
            if (data == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(data, updateStatus) { checkUpdates(manual = true) }
                    }
                    composable("adventurers") {
                        AdventurersScreen(data) { id ->
                            navController.navigate("adventurer/$id")
                        }
                    }
                    composable("adventurer/{id}") { entry ->
                        val id = entry.arguments?.getString("id") ?: ""
                        AdventurerDetailScreen(data, id) { navController.popBackStack() }
                    }
                    composable("monsters") {
                        MonstersScreen(data) { id ->
                            navController.navigate("monster/$id")
                        }
                    }
                    composable("monster/{id}") { entry ->
                        val id = entry.arguments?.getString("id") ?: ""
                        MonsterDetailScreen(data, id) { navController.popBackStack() }
                    }
                    composable("teams") {
                        SectionPlaceholder(
                            "Team Comps",
                            "The best four-hunter squads for each monster and play style — filled in during the data stage."
                        )
                    }
                    composable("coach") {
                        SectionPlaceholder(
                            "Rotation Coach",
                            "The coach ships in three phases: rotation practice, fight-recording analysis with mistake reports, and finally a live on-button guide during hunts."
                        )
                    }
                }
            }
        }
    }

    if (showApkDialog) {
        val v = remote
        AlertDialog(
            onDismissRequest = { if (dlProgress == null) showApkDialog = false },
            title = { Text("Update available — ${v?.apkVersionName ?: ""}") },
            text = {
                Column {
                    if (!v?.notesEn.isNullOrEmpty()) Text(v?.notesEn ?: "")
                    if (!v?.notesFa.isNullOrEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        FaSection(v?.notesFa ?: "", title = "تازه‌ها")
                    }
                    val p = dlProgress
                    if (p != null) {
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { p / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Downloading… $p%", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { startApkUpdate() },
                    enabled = dlProgress == null
                ) { Text("Update now") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showApkDialog = false },
                    enabled = dlProgress == null
                ) { Text("Later") }
            }
        )
    }
}

@Composable
private fun HomeScreen(data: DataBundle, updateStatus: String, onCheckUpdates: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("Rmho", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Monster Hunter Outlanders guide — built for the hunter community",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        FaSection(
            "رم‌هو راهنمای هواداری مانستر هانتر اوتلندرز است: ادونچررها، هیولاها، ترکیب تیم و روتیشن‌ها، با توضیح فارسی برای هر بخش. دیتا و نسخه‌های تازه از داخل خود برنامه آپدیت می‌شوند.",
            title = "دربارهٔ رم‌هو"
        )
        Spacer(Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Data status", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Data version: ${data.dataVersion}")
                Text("Adventurers: ${data.adventurers.size}")
                Text("Monsters: ${data.monsters.size}")
                Text("Team comps: ${data.teamComps.size}")
                Text("Rotation guides: ${data.rotations.size}")
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Updates", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    updateStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCheckUpdates) { Text("Check for updates") }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Data and images grow stage by stage and update in place — no reinstall needed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionPlaceholder(title: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(desc, style = MaterialTheme.typography.bodyLarge)
    }
}
