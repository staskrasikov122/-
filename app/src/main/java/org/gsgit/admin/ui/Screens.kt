package org.gsgit.admin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.gsgit.admin.data.AdminDevice
import org.gsgit.admin.data.AdminStats
import org.gsgit.admin.data.AppConfig
import org.gsgit.admin.data.DeviceGroup
import org.gsgit.admin.data.DevicesResponse
import org.gsgit.admin.ui.theme.TerminalAmber
import org.gsgit.admin.ui.theme.TerminalBorder
import org.gsgit.admin.ui.theme.TerminalGreen
import org.gsgit.admin.ui.theme.TerminalMuted
import org.gsgit.admin.ui.theme.TerminalRed
import org.gsgit.admin.ui.theme.TerminalSurface

@Composable
fun DashboardScreen(state: AdminUiState, viewModel: AdminViewModel) {
    when (val statsState = state.stats) {
        LoadState.Idle, LoadState.Loading -> StatePanel("загрузка сводки")
        is LoadState.Error -> StatePanel(statsState.message, "ПОВТОРИТЬ", viewModel::loadStats)
        is LoadState.Ready -> DashboardContent(statsState.value, state.togglingKillSwitch, viewModel)
    }
}

@Composable
private fun DashboardContent(
    stats: AdminStats,
    toggling: Boolean,
    viewModel: AdminViewModel,
) {
    val maintenanceOn = stats.maintenance.isMaintenanceOn()
    var showEnableDialog by rememberSaveable { mutableStateOf(false) }
    var showDisableDialog by rememberSaveable { mutableStateOf(false) }
    var maintenanceMessage by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageTitle("обзор", "актуальное состояние сервера") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("УСТРОЙСТВА", stats.devices.toString(), Modifier.weight(1f))
                MetricCard("АККАУНТЫ", stats.logins.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("ТИХИЙ РЕЖИМ", stats.quietEnabled.toString(), Modifier.weight(1f))
                MetricCard("ОТЛОЖЕНО", stats.heldPushes.toString(), Modifier.weight(1f))
            }
        }
        item {
            TerminalCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("АВАРИЙНАЯ БЛОКИРОВКА", style = MaterialTheme.typography.labelLarge, color = TerminalMuted)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(
                                if (maintenanceOn) Icons.Outlined.Warning else Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = if (maintenanceOn) TerminalRed else TerminalGreen,
                            )
                            Text(
                                if (maintenanceOn) "ВКЛЮЧЕНА" else "ВЫКЛЮЧЕНА",
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (maintenanceOn) TerminalRed else TerminalGreen,
                            )
                        }
                        if (maintenanceOn) {
                            Text(stats.maintenance, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("клиенты работают в обычном режиме", style = MaterialTheme.typography.bodyMedium, color = TerminalMuted)
                        }
                    }
                    Button(
                        onClick = {
                            if (maintenanceOn) showDisableDialog = true else showEnableDialog = true
                        },
                        enabled = !toggling,
                    ) {
                        if (toggling) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text(if (maintenanceOn) "ОТКЛЮЧИТЬ" else "ВКЛЮЧИТЬ")
                    }
                }
            }
        }
        item {
            TerminalCard {
                Text("ОГРАНИЧЕНИЯ ВЕРСИЙ", style = MaterialTheme.typography.labelLarge, color = TerminalMuted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    VersionValue("последняя", stats.latestVersion, Modifier.weight(1f))
                    VersionValue("минимальная", stats.minVersion, Modifier.weight(1f))
                }
            }
        }
    }

    if (showEnableDialog) {
        AlertDialog(
            onDismissRequest = { if (!toggling) showEnableDialog = false },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = TerminalRed) },
            title = { Text("Включить блокировку?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Действие заблокирует все клиенты GsGit. Введите сообщение, которое увидят пользователи.")
                    OutlinedTextField(
                        value = maintenanceMessage,
                        onValueChange = { maintenanceMessage = it },
                        label = { Text("Сообщение о техработах") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEnableDialog = false
                        viewModel.setMaintenance(maintenanceMessage)
                    },
                    enabled = maintenanceMessage.isNotBlank() && !toggling,
                ) { Text("ЗАБЛОКИРОВАТЬ ВСЕХ") }
            },
            dismissButton = { TextButton(onClick = { showEnableDialog = false }) { Text("ОТМЕНА") } },
        )
    }

    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { if (!toggling) showDisableDialog = false },
            title = { Text("Отключить блокировку?") },
            text = { Text("Все клиенты разблокируются после следующей проверки сервера — примерно в течение минуты.") },
            confirmButton = {
                Button(onClick = {
                    showDisableDialog = false
                    viewModel.setMaintenance("")
                }) { Text("РАЗБЛОКИРОВАТЬ") }
            },
            dismissButton = { TextButton(onClick = { showDisableDialog = false }) { Text("ОТМЕНА") } },
        )
    }
}

@Composable
fun AppConfigScreen(state: AdminUiState, viewModel: AdminViewModel) {
    when (val configState = state.config) {
        LoadState.Idle, LoadState.Loading -> StatePanel("загрузка настроек приложения")
        is LoadState.Error -> StatePanel(configState.message, "ПОВТОРИТЬ", viewModel::loadConfig)
        is LoadState.Ready -> AppConfigForm(configState.value, state.savingConfig, viewModel::saveConfig)
    }
}

@Composable
private fun AppConfigForm(
    serverConfig: AppConfig,
    saving: Boolean,
    onSave: (AppConfig) -> Unit,
) {
    var config by remember { mutableStateOf(serverConfig) }
    LaunchedEffect(serverConfig) { config = serverConfig }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageTitle("настройки приложения", "обновления и технические работы")
        TerminalField(
            value = config.maintenanceSoon,
            onValueChange = { config = config.copy(maintenanceSoon = it) },
            label = "Скоро техработы",
            help = "Текст жёлтой плашки. Пустое поле — плашка выключена.",
            minLines = 2,
        )
        TerminalField(
            value = config.maintenance,
            onValueChange = { config = config.copy(maintenance = it) },
            label = "Техработы СЕЙЧАС",
            help = "Полная блокировка клиентов. Пустое поле — блокировка выключена.",
            minLines = 3,
        )
        TerminalField(
            value = config.latestVersion,
            onValueChange = { config = config.copy(latestVersion = it) },
            label = "Последняя версия",
            help = "Мягкое предложение обновиться. Формат x.y.z.",
            keyboardType = KeyboardType.Decimal,
        )
        TerminalField(
            value = config.minVersion,
            onValueChange = { config = config.copy(minVersion = it) },
            label = "Минимальная версия",
            help = "Клиенты со старой версией будут заблокированы. Формат x.y.z.",
            keyboardType = KeyboardType.Decimal,
        )
        TerminalField(
            value = config.changelog,
            onValueChange = { config = config.copy(changelog = it) },
            label = "Что нового",
            help = "Многострочное описание изменений для клиентов.",
            minLines = 5,
        )
        TerminalField(
            value = config.downloadUrl,
            onValueChange = { config = config.copy(downloadUrl = it) },
            label = "Ссылка на APK",
            help = "Адрес страницы или файла для загрузки обновления.",
            keyboardType = KeyboardType.Uri,
        )
        Button(
            onClick = { onSave(config) },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(9.dp))
                Text("СОХРАНЕНИЕ")
            } else {
                Text("СОХРАНИТЬ НАСТРОЙКИ")
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
fun AnnounceScreen(state: AdminUiState, viewModel: AdminViewModel) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val recipientCount = (state.stats as? LoadState.Ready)?.value?.devices

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageTitle("рассылка", "сообщение всем зарегистрированным устройствам")
        TerminalCard {
            Text("ОБЩАЯ ПУШ-РАССЫЛКА", style = MaterialTheme.typography.labelLarge, color = TerminalAmber)
            Spacer(Modifier.height(8.dp))
            Text(
                recipientCount?.let { "Зарегистрировано устройств: $it" }
                    ?: "Не удалось получить число получателей — обновите данные",
                style = MaterialTheme.typography.bodyMedium,
                color = TerminalMuted,
            )
        }
        TerminalField(title, { title = it }, "Заголовок", "Обязательное поле")
        TerminalField(body, { body = it }, "Текст", "Обязательное поле", minLines = 5)
        TerminalField(url, { url = it }, "URL", "Необязательная ссылка из уведомления", keyboardType = KeyboardType.Uri)
        Button(
            onClick = { confirm = true },
            enabled = !state.sendingAnnouncement && title.isNotBlank() && body.isNotBlank() && recipientCount != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.sendingAnnouncement) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(9.dp))
                Text("ОТПРАВКА")
            } else {
                Text("ОТПРАВИТЬ ВСЕМ")
            }
        }
    }

    if (confirm && recipientCount != null) {
        AlertDialog(
            onDismissRequest = { if (!state.sendingAnnouncement) confirm = false },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = TerminalAmber) },
            title = { Text("Отправить на $recipientCount устройств?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Пуш-анонс будет отправлен на каждое зарегистрированное устройство.")
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(body, color = TerminalMuted)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirm = false
                        viewModel.sendAnnouncement(title, body, url) {
                            title = ""
                            body = ""
                            url = ""
                        }
                    },
                    enabled = !state.sendingAnnouncement,
                ) { Text("ПОДТВЕРДИТЬ") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("ОТМЕНА") } },
        )
    }
}

@Composable
fun DevicesScreen(state: LoadState<DevicesResponse>, retry: () -> Unit) {
    when (state) {
        LoadState.Idle, LoadState.Loading -> StatePanel("загрузка устройств")
        is LoadState.Error -> StatePanel(state.message, "ПОВТОРИТЬ", retry)
        is LoadState.Ready -> DevicesContent(state.value)
    }
}

@Composable
private fun DevicesContent(response: DevicesResponse) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val filtered = remember(response, query) {
        val needle = query.trim()
        if (needle.isEmpty()) response.devices else response.devices.filter { group ->
            group.login.contains(needle, ignoreCase = true) ||
                group.devices.any { it.name.contains(needle, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("устройства", "аккаунтов: ${response.logins} / устройств: ${response.totalDevices}") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск по аккаунту или устройству") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (filtered.isEmpty()) {
            item { EmptyCard(if (response.devices.isEmpty()) "Зарегистрированных устройств нет" else "Ничего не найдено") }
        } else {
            items(filtered, key = { it.login }) { group ->
                DeviceGroupCard(
                    group = group,
                    expanded = group.login in expanded,
                    onToggle = {
                        expanded = if (group.login in expanded) expanded - group.login else expanded + group.login
                    },
                )
            }
        }
    }
}

@Composable
private fun DeviceGroupCard(group: DeviceGroup, expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        color = TerminalSurface,
        border = BorderStroke(1.dp, TerminalBorder),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("@${group.login}", style = MaterialTheme.typography.titleMedium)
                    Text("зарегистрировано: ${group.count}", style = MaterialTheme.typography.labelMedium, color = TerminalMuted)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = TerminalBorder)
                    group.devices.forEachIndexed { index, device ->
                        DeviceDetails(device)
                        if (index != group.devices.lastIndex) HorizontalDivider(color = TerminalBorder)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceDetails(device: AdminDevice) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(device.name, style = MaterialTheme.typography.titleMedium, color = TerminalGreen)
        DetailLine("часовой пояс", formatTimezone(device.tzOffsetMin))
        DetailLine(
            "тихие часы",
            device.quietHours?.let { "%02d:00–%02d:00".format(it.start, it.end) } ?: "выключены",
        )
        DetailLine("отложено", device.heldCount.toString())
        DetailLine("хвост токена", "••••••${device.tokenTail}")
    }
}

@Composable
fun GlassFilesPlaceholder() {
    // TODO(контракт API GlassFiles): заменить заглушку только после получения реального контракта от владельца.
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text("[ GlassFiles ]", style = MaterialTheme.typography.headlineMedium, color = TerminalGreen)
            Spacer(Modifier.height(12.dp))
            Text("Здесь будет контракт API GlassFiles", color = TerminalMuted)
            Spacer(Modifier.height(7.dp))
            Text("Вымышленные эндпоинты не добавлены и не вызываются.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    TerminalCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TerminalMuted)
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.displaySmall, color = TerminalGreen)
    }
}

@Composable
private fun VersionValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TerminalMuted)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", style = MaterialTheme.typography.labelMedium, color = TerminalMuted)
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TerminalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    help: String,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(help) },
        minLines = minLines,
        maxLines = if (minLines == 1) 1 else 10,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("> $title", style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TerminalMuted)
    }
}

@Composable
private fun TerminalCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TerminalSurface,
        border = BorderStroke(1.dp, TerminalBorder),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun StatePanel(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (action == null) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            Text(message, color = TerminalMuted)
            if (action != null && onAction != null) OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    TerminalCard {
        Text(message, color = TerminalMuted, modifier = Modifier.padding(vertical = 18.dp))
    }
}

private fun String.isMaintenanceOn(): Boolean = isNotBlank() && !equals("off", ignoreCase = true)

private fun formatTimezone(offsetMinutes: Int): String {
    val sign = if (offsetMinutes >= 0) "+" else "-"
    val absolute = kotlin.math.abs(offsetMinutes)
    return "UTC%s%02d:%02d".format(sign, absolute / 60, absolute % 60)
}
