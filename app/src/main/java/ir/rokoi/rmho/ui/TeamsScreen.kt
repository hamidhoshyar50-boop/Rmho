package ir.rokoi.rmho.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.rokoi.rmho.data.DataBundle

/**
 * Team compositions — one card per comp, most of them built to counter a
 * specific monster. Tapping a member opens that Adventurer's page.
 */
@Composable
fun TeamsScreen(data: DataBundle, onOpenAdventurer: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Team Comps",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)
        )
        if (data.teamComps.isEmpty()) {
            Text(
                "No team comps yet — they arrive with data updates.",
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            items(data.teamComps, key = { it.id }) { comp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        val monster = comp.targetMonsterId?.let { mid ->
                            data.monsters.firstOrNull { it.id == mid }?.nameEn
                        }
                        Text(
                            comp.titleEn.ifEmpty {
                                if (monster != null) "Anti-$monster squad" else "Team comp"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (monster != null) {
                            Text(
                                "Target: $monster",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            comp.adventurerIds.forEach { aid ->
                                val adv = data.adventurers.firstOrNull { it.id == aid }
                                SuggestionChip(
                                    onClick = { onOpenAdventurer(aid) },
                                    label = { Text(adv?.nameEn ?: aid) }
                                )
                            }
                        }
                        if (comp.buddy.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Buddy: ${comp.buddy}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (comp.strategy.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(comp.strategy, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (comp.strategyFa.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            FaSection(comp.strategyFa, title = "استراتژی")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
