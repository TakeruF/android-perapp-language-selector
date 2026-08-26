package dev.takeru.perapplocale.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.takeru.perapplocale.R
import dev.takeru.perapplocale.data.AppInfo
import dev.takeru.perapplocale.data.LocaleOption
import dev.takeru.perapplocale.shizuku.ShizukuState
import dev.takeru.perapplocale.util.rememberAppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
    onShowSystemAppsChange: (Boolean) -> Unit,
    onConfiguredFirstChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenHelp: () -> Unit,
    onRecheckShizuku: () -> Unit,
    onAppClick: (AppInfo) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.show_system_apps)) },
                                trailingIcon = {
                                    Switch(
                                        checked = state.showSystemApps,
                                        onCheckedChange = onShowSystemAppsChange,
                                    )
                                },
                                onClick = { onShowSystemAppsChange(!state.showSystemApps) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.configured_apps_first)) },
                                trailingIcon = {
                                    Switch(
                                        checked = state.configuredFirst,
                                        onCheckedChange = onConfiguredFirstChange,
                                    )
                                },
                                onClick = { onConfiguredFirstChange(!state.configuredFirst) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shizuku_setup_guide)) },
                                onClick = {
                                    menuOpen = false
                                    onOpenSetup()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.help)) },
                                onClick = {
                                    menuOpen = false
                                    onOpenHelp()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            ShizukuStatusCard(
                state = state.shizuku,
                onRequestPermission = onRequestPermission,
                onOpenSetup = onOpenSetup,
                onRecheck = onRecheckShizuku,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.filter == AppFilter.ALL,
                    onClick = { onFilterChange(AppFilter.ALL) },
                    label = { Text(stringResource(R.string.all_apps)) },
                )
                FilterChip(
                    selected = state.filter == AppFilter.CONFIGURED,
                    onClick = { onFilterChange(AppFilter.CONFIGURED) },
                    label = { Text(stringResource(R.string.configured_apps_count, state.configuredCount)) },
                )
            }

            if (state.readingLocales) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            when {
                state.loadingApps -> CenteredBox { CircularProgressIndicator() }
                state.apps.isEmpty() -> CenteredBox {
                    Text(
                        emptyMessage(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.apps, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            busy = state.busyPackage == app.packageName,
                            onClick = { onAppClick(app) },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun emptyMessage(state: MainUiState): String = when {
    state.query.isNotBlank() -> stringResource(R.string.no_app_matches, state.query)
    state.filter == AppFilter.CONFIGURED && state.shizuku != ShizukuState.READY ->
        stringResource(R.string.nothing_recorded)
    state.filter == AppFilter.CONFIGURED -> stringResource(R.string.no_configured_apps)
    else -> stringResource(R.string.no_apps_to_show)
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun AppRow(app: AppInfo, busy: Boolean, onClick: () -> Unit) {
    val icon: ImageBitmap? by rememberAppIcon(app.packageName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            val bitmap = icon
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(44.dp))
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (app.isConfigured) "${LocaleOption.labelFor(app.localeTag)} · ${app.localeTag}"
                else stringResource(R.string.system_default),
                style = MaterialTheme.typography.bodyMedium,
                color = if (app.isConfigured) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when {
            busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            app.isConfigured -> Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
