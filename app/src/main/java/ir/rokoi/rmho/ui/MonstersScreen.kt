package ir.rokoi.rmho.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ir.rokoi.rmho.data.DataBundle
import ir.rokoi.rmho.data.Monster

private val ElementOrder = listOf("FIRE", "WATER", "THUNDER", "ICE", "DRAGON")

@Composable
fun MonstersScreen(data: DataBundle, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Monsters",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            items(data.monsters, key = { it.id }) { mon ->
                MonsterCard(mon) { onOpen(mon.id) }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun MonsterCard(mon: Monster, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RedPortrait(mon.imageUrl, mon.nameEn, 64.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(mon.nameEn, style = MaterialTheme.typography.titleMedium)
                val top = mon.elementWeakness.entries
                    .filter { it.value > 0 }
                    .sortedByDescending { it.value }
                    .take(2)
                    .joinToString(" · ") { "${elementLabel(it.key)} ${"★".repeat(it.value)}" }
                if (top.isNotEmpty()) {
                    Text(
                        "Weak: $top",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MonsterDetailScreen(data: DataBundle, id: String, onBack: () -> Unit) {
    val mon = data.monsters.firstOrNull { it.id == id }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(mon?.nameEn ?: "Monster", style = MaterialTheme.typography.titleLarge)
        }
        if (mon == null) {
            Text("Not found.", modifier = Modifier.padding(20.dp))
            return
        }
        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (mon.imageUrl.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        RedPortrait(mon.imageUrl, mon.nameEn, 184.dp, glow = true)
                    }
                }
            }
            if (mon.description.isNotEmpty()) {
                item { Text(mon.description, style = MaterialTheme.typography.bodyLarge) }
            }
            if (mon.descriptionFa.isNotEmpty()) {
                item { FaSection(mon.descriptionFa) }
            }
            if (mon.elementWeakness.isNotEmpty()) {
                item {
                    Text("Elemental weakness", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    ElementOrder.forEach { el ->
                        val stars = mon.elementWeakness[el] ?: 0
                        Row {
                            Text(
                                elementLabel(el),
                                modifier = Modifier.width(90.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (stars > 0) "★".repeat(stars) + "☆".repeat(3 - stars) else "—",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            if (mon.breakableParts.isNotEmpty()) {
                item {
                    Text("Breakable parts", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        mon.breakableParts.forEach { part ->
                            SuggestionChip(onClick = {}, label = { Text(part) })
                        }
                    }
                }
            }
            if (mon.drops.isNotEmpty()) {
                item {
                    Text("Loot", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    mon.drops.forEach { d -> Text("• $d") }
                }
            }
            if (mon.notes.isNotEmpty()) {
                item {
                    Text(
                        mon.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val comps = data.teamComps.filter { it.targetMonsterId == mon.id }
            comps.forEach { comp ->
                item {
                    Text(
                        "Best team for ${mon.nameEn}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(6.dp))
                    if (comp.titleEn.isNotEmpty()) {
                        Text(comp.titleEn, style = MaterialTheme.typography.titleSmall)
                    }
                    comp.adventurerIds.forEach { aid ->
                        val name = data.adventurers.firstOrNull { it.id == aid }?.nameEn ?: aid
                        Text("• $name")
                    }
                    if (comp.buddy.isNotEmpty()) {
                        Text(
                            "Buddy: ${comp.buddy}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (comp.strategy.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(comp.strategy, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (comp.strategyFa.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FaSection(comp.strategyFa, title = "استراتژی")
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
