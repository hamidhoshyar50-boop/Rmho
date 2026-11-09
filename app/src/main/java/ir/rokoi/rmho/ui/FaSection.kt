package ir.rokoi.rmho.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The Persian section of a bilingual description. Menus and names stay
 * English; descriptive content carries this FA block underneath, rendered
 * RTL on its own so the rest of the UI stays LTR.
 */
@Composable
fun FaSection(text: String, title: String = "توضیح فارسی") {
    if (text.isEmpty()) return
    if (!UiPrefs.showFa) return
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
