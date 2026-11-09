package ir.rokoi.rmho.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.imageLoader
import ir.rokoi.rmho.BuildConfig
import ir.rokoi.rmho.data.DataBundle
import ir.rokoi.rmho.data.DataRepository
import ir.rokoi.rmho.data.RemoteVersion
import ir.rokoi.rmho.data.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var bundle by remember { mutableStateOf<DataBundle?>(null) }
    var updateStatus by remember { mutableStateOf("Updates: not checked yet") }
    var remote by remember { mutableStateOf<RemoteVersion?>(null) }
    var updateAvailable by remember { mutableStateOf(false) }
    var showApkDialog by remember { mutableStateOf(false) }
    var dlProgress by remember { mutableStateOf<Int?>(null) }
    var settingsMsg by remember { mutableStateOf("") }

    fun persistFa(value: Boolean) {
        context.getSharedPreferences("rmho_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("show_fa", value).apply()
    }

    fun checkUpdates(manual: Boolean, openDialog: Boolean) {
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
            updateAvailable = version.apkVersionCode > BuildConfig.VERSION_CODE
            if (updateAvailable) {
                remote = version
                status = "App update available: ${version.apkVersionName}"
                if (openDialog) showApkDialog = true
            }
            updateStatus = status
        }
    }

    fun forceDataRefresh() {
        val repo = DataRepository(context)
        scope.launch {
            settingsMsg = "Refreshing data…"
            val fresh = withContext(Dispatchers.IO) { UpdateManager.fetchBundle() }
            if (fresh != null) {
                withContext(Dispatchers.IO) { repo.cacheBundle(fresh) }
                bundle = fresh
                settingsMsg = "Data refreshed · v${fresh.dataVersion} " +
                    "(${fresh.adventurers.size} adventurers, ${fresh.monsters.size} monsters)"
            } else {
                settingsMsg = "Data refresh failed — check your connection."
            }
        }
    }

    fun clearImageCache() {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.imageLoader.memoryCache?.clear()
                    context.imageLoader.diskCache?.clear()
                }
            }
            settingsMsg = "Image cache cleared. Portraits will download again."
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
        UiPrefs.showFa = context
            .getSharedPreferences("rmho_settings", Context.MODE_PRIVATE)
            .getBoolean("show_fa", true)
        bundle = withContext(Dispatchers.IO) {
            runCatching { DataRepository(context).loadLocal() }.getOrNull()
        }
        checkUpdates(manual = false, openDialog = false)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Rmho",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("settings") { launchSingleTop = true }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    label = { Text("Update") },
                    badge = { if (updateAvailable) Badge() },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        checkUpdates(manual = true, openDialog = true)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    label = { Text("About") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("about") { launchSingleTop = true }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    updateStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Rmho",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                val backStack by navController.currentBackStackEntryAsState()
                val current = backStack?.destination?.route
                NavigationBar {
                    Destinations.forEach { dest ->
                        NavigationBarItem(
                            selected = current == dest.route ||
                                (dest.route == "adventurers" && current?.startsWith("adventurer") == true) ||
                                (dest.route == "monsters" && current?.startsWith("monster") == true) ||
                                (dest.route == "coach" && current?.startsWith("coach") == true),
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
                            HomeScreen(
                                data,
                                onOpenAdventurer = { id -> navController.navigate("adventurer/$id") },
                                onOpenMonster = { id -> navController.navigate("monster/$id") },
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
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
                            TeamsScreen(data) { id ->
                                navController.navigate("adventurer/$id")
                            }
                        }
                        composable("coach") {
                            CoachScreen(
                                data,
                                onOpenRotation = { id ->
                                    navController.navigate("coach/$id")
                                },
                                onOpenAnalysis = {
                                    navController.navigate("coach/analyse")
                                }
                            )
                        }
                        composable("coach/analyse") {
                            AnalysisPickerScreen(
                                data,
                                onPick = { id ->
                                    navController.navigate("coach/analyse/$id")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("coach/analyse/{id}") { entry ->
                            val id = entry.arguments?.getString("id") ?: ""
                            HuntAnalysisScreen(data, id) { navController.popBackStack() }
                        }
                        composable("coach/{id}") { entry ->
                            val id = entry.arguments?.getString("id") ?: ""
                            CoachPracticeScreen(data, id) { navController.popBackStack() }
                        }
                        composable("settings") {
                            SettingsScreen(
                                dataVersion = data.dataVersion,
                                settingsMsg = settingsMsg,
                                onToggleFa = { v ->
                                    UiPrefs.showFa = v
                                    persistFa(v)
                                },
                                onRefreshData = { forceDataRefresh() },
                                onClearCache = { clearImageCache() },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("about") {
                            AboutScreen { navController.popBackStack() }
                        }
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
private fun HomeScreen(
    data: DataBundle,
    onOpenAdventurer: (String) -> Unit,
    onOpenMonster: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val featuredAdv = data.adventurers
        .filter { it.id != "fated" }
        .let { pool -> if (pool.isEmpty()) null else pool[day % pool.size] }
    val featuredMon = if (data.monsters.isEmpty()) null
        else data.monsters[(day * 7) % data.monsters.size]

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                                )
                            )
                        )
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "Rmho",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "Your Monster Hunter Outlanders companion — adventurers, monsters, counter teams and rotations.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(10.dp))
                        FaSection(
                            "Rmho راهنمای همراه تو برای مانستر هانتر اوتلندرز است: همهٔ ادونچررها و هیولاها با عکس و توضیح فارسی، برای هر هیولا یک تیم مخصوص شکستش، و به‌زودی مربی روتیشن. دیتا از داخل خود برنامه تازه می‌شود.",
                            title = "خوش آمدی، هانتر!"
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), Icons.Filled.Person, "${data.adventurers.size}", "Adventurers")
                StatCard(Modifier.weight(1f), Icons.Filled.Pets, "${data.monsters.size}", "Monsters")
                StatCard(Modifier.weight(1f), Icons.Filled.Groups, "${data.teamComps.size}", "Teams")
            }
        }
        if (featuredAdv != null) {
            item {
                Text("Adventurer of the Day", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAdventurer(featuredAdv.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GoldPortrait(featuredAdv.imageUrl, featuredAdv.nameEn, 92.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                featuredAdv.nameEn,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${classLabel(featuredAdv.classType)} • ${weaponLabel(featuredAdv.weapon)} • ${elementLabel(featuredAdv.element)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                featuredAdv.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        if (featuredMon != null) {
            item {
                Text("Monster of the Day", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMonster(featuredMon.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RedPortrait(featuredMon.imageUrl, featuredMon.nameEn, 92.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                featuredMon.nameEn,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            val weak = featuredMon.elementWeakness.entries
                                .maxByOrNull { it.value }
                            Text(
                                if (weak != null) "Top weakness: ${elementLabel(weak.key)} ★${weak.value}"
                                else "Weakness not published yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                featuredMon.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        item {
            Text("Quick start", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onNavigate("adventurers") },
                    modifier = Modifier.weight(1f)
                ) { Text("Adventurers", maxLines = 1, softWrap = false) }
                OutlinedButton(
                    onClick = { onNavigate("monsters") },
                    modifier = Modifier.weight(1f)
                ) { Text("Monsters", maxLines = 1, softWrap = false) }
                OutlinedButton(
                    onClick = { onNavigate("teams") },
                    modifier = Modifier.weight(1f)
                ) { Text("Teams", maxLines = 1, softWrap = false) }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun StatCard(modifier: Modifier, icon: ImageVector, value: String, label: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    dataVersion: Int,
    settingsMsg: String,
    onToggleFa: (Boolean) -> Unit,
    onRefreshData: () -> Unit,
    onClearCache: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Persian descriptions", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Show the Persian section under descriptions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = UiPrefs.showFa, onCheckedChange = onToggleFa)
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))
        Text("Data", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("Data version: $dataVersion · App version: ${BuildConfig.VERSION_NAME}")
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onRefreshData) { Text("Refresh data now") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onClearCache) { Text("Clear image cache") }
        if (settingsMsg.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                settingsMsg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "About Rmho",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        item {
            Text(
                "Rmho (Rokoi Monster Hunter Outlanders) is an unofficial fan-made guide for Monster Hunter Outlanders: every Adventurer and monster with portraits, kits, weaknesses, and a counter team for every hunt — with Persian descriptions for the community.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        item {
            FaSection(
                "Rmho یک راهنمای هواداری و غیررسمی برای بازی مانستر هانتر اوتلندرز است که برای کامیونیتی فارسی‌زبان ساخته شده: همهٔ ادونچررها و هیولاها با عکس، مهارت‌ها، ضعف‌ها و تیم مناسب هر شکار، همراه با توضیح روان فارسی.",
                title = "دربارهٔ Rmho"
            )
        }
        item {
            Text("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }
        item {
            Text("Data sources", style = MaterialTheme.typography.titleMedium)
            Text("Game data is compiled from community databases and guides: mhodb, GameWith and Game8. Images belong to their respective owners.")
        }
        item {
            Text("Disclaimer", style = MaterialTheme.typography.titleMedium)
            Text(
                "Unofficial fan project — not affiliated with CAPCOM or TiMi Studio Group. Monster Hunter is a trademark of CAPCOM CO., LTD.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Text(
                "Made by Rokoi for the hunter community.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { TextButton(onClick = onBack) { Text("Back") } }
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
