package dev.takeru.perapplocale.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.takeru.perapplocale.R
import dev.takeru.perapplocale.data.AppInfo
import dev.takeru.perapplocale.data.LocaleCatalog
import dev.takeru.perapplocale.data.LocaleEntry
import dev.takeru.perapplocale.data.LocaleGroup
import dev.takeru.perapplocale.data.LocaleOption
import dev.takeru.perapplocale.data.SupportedLocales
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Locale picker for a single app. "Apply" only writes the locale; "Apply & Restart" also stops
 * and relaunches the target, which is what most apps need before the change is visible.
 *
 * The list offers every locale the device knows, ordered by how likely it is to be wanted (see
 * [LocaleCatalog]), and searchable by endonym, English name or tag — 日本語, Japanese and ja-JP
 * all find the same row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocaleSheet(
    app: AppInfo,
    enabled: Boolean,
    busy: Boolean,
    loadSupportedLocales: suspend (String) -> SupportedLocales,
    onDismiss: () -> Unit,
    onApply: (LocaleOption, Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTag by remember(app.packageName) { mutableStateOf(app.localeTag) }
    var query by remember(app.packageName) { mutableStateOf("") }
    var supportedLanguagesExpanded by remember(app.packageName) { mutableStateOf(false) }

    val supportedLocales by produceState<SupportedLocales>(
        initialValue = SupportedLocales.Loading,
        key1 = app.packageName,
    ) {
        value = loadSupportedLocales(app.packageName)
    }

    // ~800 locales with several display names each: too slow for the frame that opens the sheet.
    val catalog by produceState<List<LocaleEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.Default) { LocaleCatalog.entries() }
    }

    val rows = remember(catalog, query, app.localeTag, supportedLocales) {
        catalog?.let { buildRows(it, query, app.localeTag, supportedLocales) }.orEmpty()
    }

    val listState = rememberLazyListState()
    // Editing the query rebuilds the list under the old scroll offset, which would otherwise
    // leave the user staring at the middle of the results.
    LaunchedEffect(query) { listState.scrollToItem(0) }
    LaunchedEffect(catalog, supportedLocales) {
        if (catalog == null || selectedTag.isEmpty()) return@LaunchedEffect
        val index = rows.indexOfFirst { it is SheetRow.Item && it.entry.tag == selectedTag }
        if (index > 0) listState.scrollToItem(index)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(app.label, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SupportedLocalesSummary(
                supportedLocales = supportedLocales,
                expanded = supportedLanguagesExpanded,
                onExpand = { supportedLanguagesExpanded = true },
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.language_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_search))
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
                            style = MaterialTheme.typography.bodyMedium,
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

            val selection = remember(catalog, selectedTag) {
                catalog?.firstOrNull { it.tag == selectedTag }?.toOption()
                    ?: LocaleOption(selectedTag, LocaleOption.labelFor(selectedTag))
            }
            Text(
                if (selection.isSystemDefault) {
                    stringResource(R.string.selected_system_default)
                } else {
                    stringResource(R.string.selected_language, selection.label, selection.tag)
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val unsupportedSelection = selection
                .takeUnless { it.isSystemDefault }
                ?.let { selected ->
                    (supportedLocales as? SupportedLocales.Declared)
                        ?.takeUnless { it.supports(selected.tag) }
                } != null
            if (unsupportedSelection) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(
                        stringResource(R.string.unsupported_language_warning, selection.label),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val canApply = enabled && !busy
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onApply(selection, false) },
                    enabled = canApply,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.apply)) }
                Button(
                    onClick = { onApply(selection, true) },
                    enabled = canApply,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.apply_and_restart)) }
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

@Composable
private fun SupportedLocalesSummary(
    supportedLocales: SupportedLocales,
    expanded: Boolean,
    onExpand: () -> Unit,
) {
    Column(Modifier.padding(top = 10.dp)) {
        when (supportedLocales) {
            SupportedLocales.Loading -> Text(
                stringResource(R.string.supported_languages_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is SupportedLocales.Declared -> {
                Text(
                    stringResource(R.string.supported_languages_count, supportedLocales.tags.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                val languageNames = remember(supportedLocales.tags) {
                    supportedLocales.tags.map { tag ->
                        val entry = LocaleCatalog.entryFor(tag, LocaleGroup.SUPPORTED)
                        entry.displayName.ifBlank { entry.label }
                    }
                }
                val preview = languageNames.take(SUPPORTED_LOCALE_PREVIEW_SIZE).joinToString(", ")
                if (expanded || languageNames.size <= SUPPORTED_LOCALE_PREVIEW_SIZE) {
                    Text(
                        languageNames.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val collapsedText = stringResource(
                        R.string.supported_languages_preview_more,
                        preview,
                        languageNames.size - SUPPORTED_LOCALE_PREVIEW_SIZE,
                    )
                    val moreStart = collapsedText.indexOf(preview)
                        .takeIf { it >= 0 }
                        ?.plus(preview.length)
                        ?: 0
                    val moreColor = MaterialTheme.colorScheme.primary
                    val moreText = remember(
                        collapsedText,
                        moreStart,
                        moreColor,
                    ) {
                        buildAnnotatedString {
                            append(collapsedText)
                            addStyle(
                                style = SpanStyle(
                                    color = moreColor,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                start = moreStart,
                                end = collapsedText.length,
                            )
                            addStringAnnotation(
                                tag = SUPPORTED_LOCALE_MORE_ANNOTATION,
                                annotation = "expand",
                                start = moreStart,
                                end = collapsedText.length,
                            )
                        }
                    }
                    @Suppress("DEPRECATION")
                    ClickableText(
                        text = moreText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        onClick = { offset ->
                            if (
                                moreText.getStringAnnotations(
                                    tag = SUPPORTED_LOCALE_MORE_ANNOTATION,
                                    start = offset,
                                    end = offset,
                                ).isNotEmpty()
                            ) {
                                onExpand()
                            }
                        },
                    )
                }
            }
            SupportedLocales.NotDeclared -> Text(
                stringResource(R.string.supported_languages_not_declared),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SupportedLocales.Invalid -> Text(
                stringResource(R.string.supported_languages_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            SupportedLocales.Unavailable -> Text(
                stringResource(R.string.supported_languages_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private const val SUPPORTED_LOCALE_PREVIEW_SIZE = 4
private const val SUPPORTED_LOCALE_MORE_ANNOTATION = "supported-locale-more"

internal sealed interface SheetRow {
    val key: String

    data class Header(val group: LocaleGroup) : SheetRow {
        override val key: String get() = "header:$group"
    }

    data class Item(val entry: LocaleEntry) : SheetRow {
        override val key: String get() = "tag:${entry.group}:${entry.tag}"
    }
}

/**
 * Flattens the catalog into list rows.
 *
 * With no query the groups are kept and labelled, which is what carries the intended order
 * (device language → the user's other languages → this app's declared languages → other
 * languages). Search keeps those labels so official matches remain distinguishable.
 */
internal fun buildRows(
    catalog: List<LocaleEntry>,
    rawQuery: String,
    currentTag: String,
    supportedLocales: SupportedLocales,
): List<SheetRow> {
    // A locale already applied to this app that the catalog does not list — set by another tool,
    // or shipped by a system image this one does not know. It has to stay reachable.
    val extras = currentTag
        .takeIf { tag -> tag.isNotEmpty() && catalog.none { it.tag == tag } }
        ?.let { listOf(LocaleCatalog.entryFor(it, LocaleGroup.CURRENT)) }
        .orEmpty()

    val deviceTags = catalog
        .filter { it.group == LocaleGroup.SYSTEM || it.group == LocaleGroup.ADDED }
        .mapTo(mutableSetOf()) { it.tag }
    val currentTags = extras.mapTo(mutableSetOf()) { it.tag }
    val declared = supportedLocales as? SupportedLocales.Declared
    val supported = declared?.tags
        ?.distinct()
        ?.filter { it.isNotEmpty() }
        ?.map { LocaleCatalog.entryFor(it, LocaleGroup.SUPPORTED) }
        ?.filter { it.tag !in deviceTags && it.tag !in currentTags }
        .orEmpty()
    val supportedTags = supported.mapTo(mutableSetOf()) { it.tag }
    val other = catalog.filter { it.group == LocaleGroup.OTHER && it.tag !in supportedTags }
    val sections = buildList {
        if (extras.isNotEmpty()) add(LocaleGroup.CURRENT to extras)
        add(LocaleGroup.SYSTEM to catalog.filter { it.group == LocaleGroup.SYSTEM })
        add(LocaleGroup.ADDED to catalog.filter { it.group == LocaleGroup.ADDED })
        if (supported.isNotEmpty()) add(LocaleGroup.SUPPORTED to supported)
        add(LocaleGroup.OTHER to other)
    }.filter { it.second.isNotEmpty() }

    val needle = LocaleCatalog.normalizeQuery(rawQuery)

    if (needle.isEmpty()) {
        return sections.flatMapTo(ArrayList()) { (group, entries) ->
            buildList<SheetRow> {
                add(SheetRow.Header(group))
                entries.forEach { add(SheetRow.Item(it)) }
            }
        }
    }

    // Keep section labels while searching so an official match cannot look like an arbitrary
    // locale from the broad fallback list.
    return sections.flatMapTo(ArrayList()) { (group, entries) ->
        val matches = entries
            .mapNotNull { entry -> entry.score(needle).takeIf { it >= 0 }?.let { it to entry } }
            .sortedBy { it.first } // stable: ties keep each section's own ordering
        if (matches.isEmpty()) {
            emptyList()
        } else {
            buildList<SheetRow> {
                add(SheetRow.Header(group))
                matches.forEach { (_, entry) -> add(SheetRow.Item(entry)) }
            }
        }
    }
}

@Composable
internal fun GroupHeader(group: LocaleGroup) {
    Text(
        stringResource(
            when (group) {
                LocaleGroup.CURRENT -> R.string.locale_group_current
                LocaleGroup.SYSTEM -> R.string.locale_group_system
                LocaleGroup.ADDED -> R.string.locale_group_added
                LocaleGroup.SUPPORTED -> R.string.locale_group_supported
                LocaleGroup.OTHER -> R.string.locale_group_other
            },
        ),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
internal fun LocaleRow(entry: LocaleEntry, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(
                if (entry.isSystemDefault) stringResource(R.string.system_default) else entry.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (entry.isSystemDefault) stringResource(R.string.follows_device_language) else entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = null)
    }
}
