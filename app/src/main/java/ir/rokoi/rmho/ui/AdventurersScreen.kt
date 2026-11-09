package ir.rokoi.rmho.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ir.rokoi.rmho.data.Adventurer
import ir.rokoi.rmho.data.DataBundle

private val ClassFilters = listOf(
    "" to "All",
    "ASSAULT" to "Assault",
    "DISRUPTOR" to "Disruptor",
    "SUPPORT" to "Support"
)

@Composable
fun AdventurersScreen(data: DataBundle, onOpen: (String) -> Unit) {
    var filter by remember { mutableStateOf("") }
    val list = data.adventurers.filter { filter.isEmpty() || it.classType == filter }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Adventurers",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            items(ClassFilters) { (value, label) ->
                FilterChip(
                    selected = filter == value,
                    onClick = { filter = value },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            items(list, key = { it.id }) { adv ->
                AdventurerCard(adv) { onOpen(adv.id) }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun AdventurerCard(adv: Adventurer, onClick: () -> Unit) {
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
            if (adv.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = adv.imageUrl,
                    contentDescription = adv.nameEn,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        adv.nameEn.take(1),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(adv.nameEn, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${classLabel(adv.classType)} • ${weaponLabel(adv.weapon)} • ${elementLabel(adv.element)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AdventurerDetailScreen(data: DataBundle, id: String, onBack: () -> Unit) {
    val adv = data.adventurers.firstOrNull { it.id == id }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                adv?.nameEn ?: "Adventurer",
                style = MaterialTheme.typography.titleLarge
            )
        }
        if (adv == null) {
            Text(
                "Not found.",
                modifier = Modifier.padding(20.dp)
            )
            return
        }
        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (adv.imageUrl.isNotEmpty()) {
                item {
                    AsyncImage(
                        model = adv.imageUrl,
                        contentDescription = adv.nameEn,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(onClick = {}, label = { Text(classLabel(adv.classType)) })
                    SuggestionChip(onClick = {}, label = { Text(weaponLabel(adv.weapon)) })
                    SuggestionChip(onClick = {}, label = { Text(elementLabel(adv.element)) })
                }
            }
            if (adv.description.isNotEmpty()) {
                item { Text(adv.description, style = MaterialTheme.typography.bodyLarge) }
            }
            if (adv.skills.isNotEmpty()) {
                item {
                    Text("Skills", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    adv.skills.forEach { s ->
                        Text("• ${s.nameEn} — ${s.description}")
                    }
                }
            }
            adv.ultimate?.let { u ->
                item {
                    Text("Ultimate: ${u.nameEn}", style = MaterialTheme.typography.titleMedium)
                    if (u.description.isNotEmpty()) Text(u.description)
                }
            }
            if (adv.strengths.isNotEmpty()) {
                item { Text("Strengths: ${adv.strengths.joinToString(", ")}") }
            }
            if (adv.weaknesses.isNotEmpty()) {
                item { Text("Weaknesses: ${adv.weaknesses.joinToString(", ")}") }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
