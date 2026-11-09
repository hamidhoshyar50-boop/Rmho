package ir.rokoi.rmho.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ir.rokoi.rmho.data.DataBundle
import ir.rokoi.rmho.data.DataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val Destinations = listOf(
    Destination("home", "Home", Icons.Filled.Home),
    Destination("adventurers", "Adventurers", Icons.Filled.Person),
    Destination("monsters", "Monsters", Icons.Filled.Pets),
    Destination("teams", "Team Comps", Icons.Filled.Groups),
    Destination("coach", "Coach", Icons.Filled.School)
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val context = LocalContext.current
    var bundle by remember { mutableStateOf<DataBundle?>(null) }

    LaunchedEffect(Unit) {
        bundle = withContext(Dispatchers.IO) {
            runCatching { DataRepository(context).loadBundled() }.getOrNull()
        }
    }

    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val current = backStack?.destination?.route
            NavigationBar {
                Destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = current == dest.route ||
                            (dest.route == "adventurers" && current?.startsWith("adventurer") == true),
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
                        label = { Text(dest.label) }
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
                    composable("home") { HomeScreen(data) }
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
                        SectionPlaceholder(
                            "Monsters",
                            "Elemental weaknesses, breakable parts and loot for every monster — filled in during the data stage."
                        )
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
}

@Composable
private fun HomeScreen(data: DataBundle) {
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
        Text(
            "This is the foundation build. Data and images grow stage by stage and update in place — no reinstall needed.",
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
