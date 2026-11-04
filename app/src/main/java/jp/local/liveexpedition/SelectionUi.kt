@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package jp.local.liveexpedition

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp

@Composable
fun SelectionField(
    label: String,
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
    allowCustom: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Caption(label)
        if (options.size <= 5 && !allowCustom)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (options + listOf(value).filter { it.isNotEmpty() && it !in options }).forEach {
                    option ->
                    AccentChip(value == option, { onChange(option) }, label = { Text(option) })
                }
            }
        else
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(value.ifEmpty { "選択してください" }, Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore, null)
            }
    }
    if (open)
        ChoiceSheet(
            label,
            options.map { it to it },
            setOf(value),
            false,
            { keys ->
                keys.firstOrNull()?.let(onChange)
                open = false
            },
            { open = false },
            allowCustom,
        )
}

@Composable
fun ChoiceSheet(
    title: String,
    choices: List<Pair<String, String>>,
    selected: Set<String>,
    multi: Boolean,
    onChoose: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    allowCustom: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(selected) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionTitle(title)
                if (multi) TextButton(onClick = { onChoose(checked) }) { Text("選択を確定") }
            }
            OutlinedTextField(
                query,
                { query = it.take(100) },
                label = { Text("検索${if(allowCustom)"・新しい値"else ""}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (allowCustom && query.isNotBlank() && choices.none { it.second == query })
                TextButton(onClick = { onChoose(setOf(query)) }) { Text("「$query」を使用") }
            LazyColumn(
                Modifier.heightIn(max = 420.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(choices.filter { it.second.contains(query, true) }, key = { it.first }) {
                    (key, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                if (multi)
                                    checked = if (key in checked) checked - key else checked + key
                                else onChoose(setOf(key))
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, Modifier.weight(1f))
                        if (multi) Checkbox(key in checked, null)
                        else RadioButton(key in checked, null)
                    }
                }
            }
        }
    }
}
