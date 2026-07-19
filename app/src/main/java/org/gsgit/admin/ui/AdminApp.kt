package org.gsgit.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import org.gsgit.admin.ui.theme.TerminalBorder
import org.gsgit.admin.ui.theme.TerminalGreen
import org.gsgit.admin.ui.theme.TerminalMuted
import org.gsgit.admin.ui.theme.TerminalSurface

@Composable
fun AdminApp(viewModel: AdminViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { snackbarHostState.showSnackbar(it) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val auth = state.auth) {
            AuthState.Restoring -> FullScreenLoader("restoring secure session")
            is AuthState.Locked -> LockScreen(
                error = auth.error,
                checking = false,
                onUnlock = viewModel::unlock,
            )
            AuthState.Checking -> LockScreen(
                error = null,
                checking = true,
                onUnlock = viewModel::unlock,
            )
            AuthState.Unlocked -> AdminShell(
                state = state,
                snackbarHostState = snackbarHostState,
                onBackend = viewModel::selectBackend,
                onSection = viewModel::selectSection,
                onRefresh = viewModel::refreshAll,
                onLock = viewModel::lock,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun LockScreen(
    error: String?,
    checking: Boolean,
    onUnlock: (String) -> Unit,
) {
    var key by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("[ GsGit Admin ]", style = MaterialTheme.typography.headlineMedium, color = TerminalGreen)
            Text("control plane locked", style = MaterialTheme.typography.bodyMedium, color = TerminalMuted)
            OutlinedTextField(
                value = key,
                onValueChange = { if (!checking) key = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !checking,
                singleLine = true,
                label = { Text("X-Admin-Key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!checking) onUnlock(key) }),
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
            )
            Button(
                onClick = { onUnlock(key) },
                enabled = !checking && key.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("VERIFYING")
                } else {
                    Text("UNLOCK")
                }
            }
        }
    }
}

@Composable
private fun AdminShell(
    state: AdminUiState,
    snackbarHostState: SnackbarHostState,
    onBackend: (Backend) -> Unit,
    onSection: (Section) -> Unit,
    onRefresh: () -> Unit,
    onLock: () -> Unit,
    viewModel: AdminViewModel,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                AdminTopBar(
                    backend = state.backend,
                    onBackend = onBackend,
                    onRefresh = onRefresh,
                    onLock = onLock,
                )
            },
            bottomBar = {
                if (!wide && state.backend == Backend.GsGit) {
                    AdminBottomNavigation(state.section, onSection)
                }
            },
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (wide && state.backend == Backend.GsGit) {
                    AdminSideNavigation(state.section, onSection)
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (state.backend == Backend.GlassFiles) {
                        GlassFilesPlaceholder()
                    } else {
                        when (state.section) {
                            Section.Dashboard -> DashboardScreen(state, viewModel)
                            Section.AppConfig -> AppConfigScreen(state, viewModel)
                            Section.Announce -> AnnounceScreen(state, viewModel)
                            Section.Devices -> DevicesScreen(state.devices, viewModel::loadDevices)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminTopBar(
    backend: Backend,
    onBackend: (Backend) -> Unit,
    onRefresh: () -> Unit,
    onLock: () -> Unit,
) {
    Surface(color = TerminalSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("> server admin", style = MaterialTheme.typography.titleLarge)
                    Text("api.gsgit.org", style = MaterialTheme.typography.labelMedium, color = TerminalMuted)
                }
                IconButton(onClick = onRefresh, enabled = backend == Backend.GsGit) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
                TextButton(onClick = onLock) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("LOCK")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Backend.entries.forEach { item ->
                    FilterChip(
                        selected = backend == item,
                        onClick = { onBackend(item) },
                        label = { Text(item.name) },
                    )
                }
            }
        }
    }
}

private data class NavigationItem(
    val section: Section,
    val label: String,
    val icon: ImageVector,
)

private val navigationItems = listOf(
    NavigationItem(Section.Dashboard, "Dashboard", Icons.Outlined.Dashboard),
    NavigationItem(Section.AppConfig, "Config", Icons.Outlined.Settings),
    NavigationItem(Section.Announce, "Announce", Icons.Outlined.Campaign),
    NavigationItem(Section.Devices, "Devices", Icons.Outlined.Devices),
)

@Composable
private fun AdminBottomNavigation(selected: Section, onSection: (Section) -> Unit) {
    NavigationBar(containerColor = TerminalSurface) {
        navigationItems.forEach { item ->
            NavigationBarItem(
                selected = selected == item.section,
                onClick = { onSection(item.section) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun AdminSideNavigation(selected: Section, onSection: (Section) -> Unit) {
    Surface(
        modifier = Modifier.width(220.dp).fillMaxHeight(),
        color = TerminalSurface,
        border = BorderStroke(1.dp, TerminalBorder),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            navigationItems.forEach { item ->
                FilterChip(
                    selected = selected == item.section,
                    onClick = { onSection(item.section) },
                    label = { Text(item.label) },
                    leadingIcon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FullScreenLoader(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            Text(label, color = TerminalMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
