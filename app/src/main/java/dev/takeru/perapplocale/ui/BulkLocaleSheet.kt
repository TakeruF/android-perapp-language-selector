package dev.takeru.perapplocale.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.takeru.perapplocale.R
import dev.takeru.perapplocale.data.LocaleCatalog
import dev.takeru.perapplocale.data.LocaleEntry
import dev.takeru.perapplocale.data.LocaleOption
import dev.takeru.perapplocale.data.SupportedLocales
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Locale picker used after selecting multiple apps. No app-specific support list is implied. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkLocaleSheet(
    appCount: Int,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onContinue: (LocaleOption) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val catalog by produceState<List<LocaleEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.Default) { LocaleCatalog.entries() }
    }
    val rows = remember(catalog, query) {
        catalog?.let {
            buildRows(it, query, currentTag = "", supportedLocales = SupportedLocales.NotDeclared)
        }.orEmpty()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.bulk_sheet_title, appCount),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.bulk_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.language_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.clear_search),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            Spacer(Modifier.height(8.dp))

            Box(Modifier.weight(1f)) {
                when {
                    catalog == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.no_language_matches, query.trim()),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(rows, key = { it.key }) { row ->
                            when (row) {
                                is SheetRow.Header -> GroupHeader(row.group)
                                is SheetRow.Item -> LocaleRow(
                                    entry = row.entry,
                                    selected = selectedTag == row.entry.tag,
                                    onClick = { selectedTag = row.entry.tag },
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            val selection = selectedTag?.let { tag ->
                catalog?.firstOrNull { it.tag == tag }?.toOption()
                    ?: LocaleOption(tag, LocaleOption.labelFor(tag))
            }
            Text(
                when {
                    selection == null -> stringResource(R.string.bulk_choose_language)
                    selection.isSystemDefault -> stringResource(R.string.selected_system_default)
                    else -> stringResource(
                        R.string.selected_language,
                        selection.label,
                        selection.tag,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { selection?.let(onContinue) },
                enabled = enabled && selection != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.continue_to_confirmation))
            }

            if (!enabled) {
                Text(
                    stringResource(R.string.shizuku_not_ready),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
