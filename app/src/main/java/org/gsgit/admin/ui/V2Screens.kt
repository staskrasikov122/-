package org.gsgit.admin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.gsgit.admin.data.*
import org.gsgit.admin.ui.theme.*

@Composable
fun DashboardV2Screen(state: AdminUiState, viewModel: AdminViewModel) {
    val stats = (state.stats as? LoadState.Ready)?.value
    if (stats == null && state.stats is LoadState.Error) {
        V2State((state.stats as LoadState.Error).message, "ПОВТОРИТЬ", viewModel::loadDashboard)
        return
    }
    if (stats == null) {
        V2State("загрузка сводки")
        return
    }

    var maintenanceDialog by rememberSaveable { mutableStateOf(false) }
    var maintenanceMessage by rememberSaveable { mutableStateOf("") }
    val maintenanceOn = stats.maintenance.isNotBlank() && !stats.maintenance.equals("off", true)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { V2Title("обзор", "здоровье сервера и реальные метрики") }
        item {
            when (val health = state.health) {
                is LoadState.Ready -> HealthCard(health.value)
                is LoadState.Error -> V2Card { Text("Здоровье сервера: ${health.message}", color = TerminalRed) }
                else -> V2Card { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Проверка компонентов…", color = TerminalMuted) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V2Metric("УСТРОЙСТВА", stats.devices.toString(), Modifier.weight(1f))
                V2Metric("АККАУНТЫ", stats.logins.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V2Metric("ТИХИЙ РЕЖИМ", stats.quietEnabled.toString(), Modifier.weight(1f))
                V2Metric("ОТЛОЖЕНО", stats.heldPushes.toString(), Modifier.weight(1f))
            }
        }
        item {
            V2Card {
                Text("ПЕРИОД МЕТРИК", style = MaterialTheme.typography.labelLarge, color = TerminalMuted)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("1h" to "1 ч", "24h" to "24 ч", "7d" to "7 д", "30d" to "30 д").forEach { (value, label) ->
                        FilterChip(
                            selected = state.metricsPeriod == value,
                            onClick = { viewModel.loadMetrics(value) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                when (val metrics = state.metrics) {
                    is LoadState.Ready -> MetricsBlock(metrics.value)
                    is LoadState.Error -> Text(metrics.message, color = TerminalRed)
                    else -> LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        item {
            V2Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("АВАРИЙНАЯ БЛОКИРОВКА", style = MaterialTheme.typography.labelLarge, color = TerminalMuted)
                        Text(if (maintenanceOn) "ВКЛЮЧЕНА" else "ВЫКЛЮЧЕНА", color = if (maintenanceOn) TerminalRed else TerminalGreen, style = MaterialTheme.typography.titleLarge)
                        Text(if (maintenanceOn) stats.maintenance else "Клиенты работают в обычном режиме", color = TerminalMuted)
                    }
                    Button(onClick = { maintenanceDialog = true }, enabled = !state.togglingKillSwitch) {
                        Icon(Icons.Outlined.PowerSettingsNew, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (maintenanceOn) "СНЯТЬ" else "ВКЛЮЧИТЬ")
                    }
                }
            }
        }
        item {
            V2Card {
                Text("ВЕРСИИ", style = MaterialTheme.typography.labelLarge, color = TerminalMuted)
                Spacer(Modifier.height(8.dp))
                V2Line("последняя", stats.latestVersion)
                V2Line("минимальная", stats.minVersion)
            }
        }
    }

    if (maintenanceDialog) {
        AlertDialog(
            onDismissRequest = { maintenanceDialog = false },
            title = { Text(if (maintenanceOn) "Снять блокировку?" else "Включить блокировку?") },
            text = {
                if (maintenanceOn) Text("Все клиенты разблокируются после следующей проверки сервера.")
                else OutlinedTextField(
                    value = maintenanceMessage,
                    onValueChange = { maintenanceMessage = it },
                    label = { Text("Сообщение пользователям") },
                    minLines = 3,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        maintenanceDialog = false
                        viewModel.setMaintenance(if (maintenanceOn) "" else maintenanceMessage)
                    },
                    enabled = maintenanceOn || maintenanceMessage.isNotBlank(),
                ) { Text(if (maintenanceOn) "РАЗБЛОКИРОВАТЬ" else "ЗАБЛОКИРОВАТЬ") }
            },
            dismissButton = { TextButton(onClick = { maintenanceDialog = false }) { Text("ОТМЕНА") } },
        )
    }
}

@Composable
private fun HealthCard(health: HealthStatus) {
    V2Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (health.status.equals("ok", true)) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                null,
                tint = if (health.status.equals("ok", true)) TerminalGreen else TerminalRed,
            )
            Spacer(Modifier.width(9.dp))
            Text("СЕРВЕР ${health.status.uppercase()}", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(10.dp))
        V2Line("версия сервера", health.serverVersion)
        V2Line("время работы", formatUptime(health.uptimeSec))
        V2Line("база данных", health.database)
        V2Line("Firebase", health.firebase)
        V2Line("GitHub webhooks", health.githubWebhooks)
        V2Line("очередь пушей", health.pushQueue.toString())
        V2Line("время сервера", displayDate(health.serverTime))
    }
}

@Composable
private fun MetricsBlock(metrics: AdminMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        V2Line("регистрации", metrics.registrations.toString())
        V2Line("активные устройства", metrics.activeDevices.toString())
        V2Line("пуши: успешно / ошибки", "${metrics.pushesSent} / ${metrics.pushesFailed}")
        V2Line("события GitHub", metrics.githubEvents.toString())
        V2Line("запросы", metrics.requests.toString())
        V2Line("ответы 4xx / 5xx", "${metrics.responses4xx} / ${metrics.responses5xx}")
    }
}

@Composable
fun AppConfigV2Screen(state: AdminUiState, viewModel: AdminViewModel) {
    val serverConfig = (state.config as? LoadState.Ready)?.value
    if (serverConfig == null) {
        val error = (state.config as? LoadState.Error)?.message
        V2State(error ?: "загрузка настроек", if (error != null) "ПОВТОРИТЬ" else null, if (error != null) viewModel::loadConfig else null)
        return
    }
    var config by remember(serverConfig) { mutableStateOf(serverConfig) }
    var reason by rememberSaveable { mutableStateOf("") }
    var rollbackRevision by rememberSaveable { mutableStateOf<Int?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        V2Title("настройки", "серверная проверка, история и безопасный откат")
        V2Field(config.maintenanceSoon, { config = config.copy(maintenanceSoon = it) }, "Скоро техработы", "Пусто — предупреждение выключено", 2)
        V2Field(config.maintenance, { config = config.copy(maintenance = it) }, "Техработы сейчас", "Полная блокировка клиентов", 3)
        V2Field(config.latestVersion, { config = config.copy(latestVersion = it) }, "Последняя версия", "Формат x.y.z")
        V2Field(config.minVersion, { config = config.copy(minVersion = it) }, "Минимальная версия", "Старые клиенты будут заблокированы")
        V2Field(config.changelog, { config = config.copy(changelog = it) }, "Что нового", "Описание изменений", 5)
        V2Field(config.downloadUrl, { config = config.copy(downloadUrl = it) }, "Ссылка на APK", "HTTPS-адрес загрузки")
        V2Field(reason, { reason = it }, "Причина изменения", "Попадёт в журнал ревизий", 2)
        Button(
            onClick = { viewModel.saveConfig(config, reason) },
            enabled = !state.savingConfig,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.savingConfig) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(if (state.savingConfig) "  ПРОВЕРКА И СОХРАНЕНИЕ" else "ПРОВЕРИТЬ И СОХРАНИТЬ")
        }
        V2Card {
            Text("ИСТОРИЯ КОНФИГУРАЦИИ", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            when (val history = state.configHistory) {
                is LoadState.Ready -> if (history.value.items.isEmpty()) Text("История пока пуста", color = TerminalMuted) else history.value.items.forEach { revision ->
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ревизия #${revision.revision}", color = TerminalGreen, fontWeight = FontWeight.Bold)
                        Text(displayDate(revision.changedAt), color = TerminalMuted)
                        Text(revision.changedFields.joinToString().ifBlank { "поля не указаны" })
                        if (revision.reason.isNotBlank()) Text("причина: ${revision.reason}", color = TerminalMuted)
                        OutlinedButton(onClick = { rollbackRevision = revision.revision }, enabled = state.busyAction == null) { Text("ОТКАТИТЬ К ЭТОЙ") }
                    }
                    HorizontalDivider(color = TerminalBorder)
                }
                is LoadState.Error -> Text(history.message, color = TerminalRed)
                else -> LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    rollbackRevision?.let { revision ->
        AlertDialog(
            onDismissRequest = { rollbackRevision = null },
            title = { Text("Откатить конфигурацию?") },
            text = { Text("Будет применён снимок ревизии #$revision. Текущее состояние сохранится новой записью аудита.") },
            confirmButton = { Button(onClick = { rollbackRevision = null; viewModel.rollbackConfig(revision) }) { Text("ОТКАТИТЬ") } },
            dismissButton = { TextButton(onClick = { rollbackRevision = null }) { Text("ОТМЕНА") } },
        )
    }
}

@Composable
fun AnnounceV2Screen(state: AdminUiState, viewModel: AdminViewModel) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var sendConfirm by rememberSaveable { mutableStateOf(false) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val recipients = (state.stats as? LoadState.Ready)?.value?.devices

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { V2Title("рассылки", "отправка, история и повтор ошибок") }
        item {
            V2Card {
                Text("НОВАЯ РАССЫЛКА", style = MaterialTheme.typography.titleMedium, color = TerminalAmber)
                Spacer(Modifier.height(8.dp))
                V2Field(title, { title = it }, "Заголовок", "Обязательно")
                V2Field(body, { body = it }, "Текст", "Обязательно", 4)
                V2Field(url, { url = it }, "Ссылка", "Необязательно")
                Button(
                    onClick = { sendConfirm = true },
                    enabled = recipients != null && title.isNotBlank() && body.isNotBlank() && !state.sendingAnnouncement,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.sendingAnnouncement) "ОТПРАВКА…" else "ОТПРАВИТЬ НА ${recipients ?: 0} УСТРОЙСТВ") }
            }
        }
        item { Text("ИСТОРИЯ", style = MaterialTheme.typography.titleMedium) }
        when (val history = state.announcements) {
            is LoadState.Ready -> if (history.value.items.isEmpty()) item { V2Card { Text("Рассылок пока нет", color = TerminalMuted) } } else items(history.value.items, key = { it.id }) { record ->
                V2Card(Modifier.clickable { detailsOpen = true; viewModel.loadAnnouncementDetails(record.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(record.title.ifBlank { "Без заголовка" }, fontWeight = FontWeight.Bold)
                            Text(displayDate(record.createdAt), color = TerminalMuted)
                        }
                        Text(record.status, color = if (record.failed == 0) TerminalGreen else TerminalAmber)
                    }
                    Spacer(Modifier.height(6.dp))
                    V2Line("получателей", record.targeted.toString())
                    V2Line("доставлено / ошибок", "${record.delivered} / ${record.failed}")
                }
            }
            is LoadState.Error -> item { V2Card { Text(history.message, color = TerminalRed) } }
            else -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
    }

    if (sendConfirm && recipients != null) {
        AlertDialog(
            onDismissRequest = { sendConfirm = false },
            title = { Text("Отправить всем?") },
            text = { Text("«$title» будет отправлено на $recipients устройств.") },
            confirmButton = { Button(onClick = {
                sendConfirm = false
                viewModel.sendAnnouncement(title, body, url) { title = ""; body = ""; url = "" }
            }) { Text("ОТПРАВИТЬ") } },
            dismissButton = { TextButton(onClick = { sendConfirm = false }) { Text("ОТМЕНА") } },
        )
    }

    if (detailsOpen) {
        AlertDialog(
            onDismissRequest = { detailsOpen = false },
            title = { Text("Детали рассылки") },
            text = {
                when (val details = state.announcementDetails) {
                    is LoadState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(details.value.title, fontWeight = FontWeight.Bold)
                        Text(details.value.body)
                        if (details.value.url.isNotBlank()) Text(details.value.url, color = TerminalGreen)
                        V2Line("доставлено", details.value.delivered.toString())
                        V2Line("ошибок", details.value.failed.toString())
                        if (details.value.failed > 0) OutlinedButton(
                            onClick = { detailsOpen = false; viewModel.retryAnnouncement(details.value.id) },
                            enabled = state.busyAction == null,
                        ) { Text("ПОВТОРИТЬ ОШИБКИ") }
                        TextButton(onClick = { detailsOpen = false; viewModel.cancelAnnouncement(details.value.id) }) { Text("ПРОВЕРИТЬ ОТМЕНУ") }
                    }
                    is LoadState.Error -> Text(details.message, color = TerminalRed)
                    else -> CircularProgressIndicator()
                }
            },
            confirmButton = { TextButton(onClick = { detailsOpen = false }) { Text("ЗАКРЫТЬ") } },
        )
    }
}

@Composable
fun DevicesV2Screen(state: AdminUiState, viewModel: AdminViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var activeOnly by rememberSaveable { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var deleteTarget by remember { mutableStateOf<AdminDevice?>(null) }
    var testTarget by remember { mutableStateOf<AdminDevice?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { V2Title("устройства", "диагностика и адресные действия") }
        item {
            V2Card {
                OutlinedTextField(query, { query = it }, label = { Text("Логин содержит") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(activeOnly, { activeOnly = it })
                    Text("Только с включёнными пушами", Modifier.weight(1f))
                    Button(onClick = { viewModel.loadDevices(query, activeOnly) }) { Text("НАЙТИ") }
                }
            }
        }
        when (val devices = state.devices) {
            is LoadState.Ready -> {
                item { Text("АККАУНТОВ: ${devices.value.logins} · УСТРОЙСТВ: ${devices.value.totalDevices}", color = TerminalMuted) }
                if (devices.value.devices.isEmpty()) item { V2Card { Text("Устройства не найдены", color = TerminalMuted) } }
                items(devices.value.devices, key = { it.login }) { group ->
                    V2Card(Modifier.clickable { expanded = if (group.login in expanded) expanded - group.login else expanded + group.login }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("@${group.login}", fontWeight = FontWeight.Bold)
                                Text("устройств: ${group.count}", color = TerminalMuted)
                            }
                            Icon(if (group.login in expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                        }
                        AnimatedVisibility(group.login in expanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Spacer(Modifier.height(6.dp))
                                group.devices.forEach { device ->
                                    HorizontalDivider(color = TerminalBorder)
                                    DeviceV2Details(
                                        device = device,
                                        busy = state.busyAction != null,
                                        onTest = { testTarget = device },
                                        onClear = { viewModel.clearHeld(device.deviceId) },
                                        onTogglePush = { viewModel.setDevicePush(device.deviceId, !device.pushEnabled) },
                                        onDelete = { deleteTarget = device },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is LoadState.Error -> item { V2Card { Text(devices.message, color = TerminalRed) } }
            else -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
    }

    deleteTarget?.let { device ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить регистрацию?") },
            text = { Text("${device.name}\nID: ${device.deviceId}\nУстройство перестанет получать уведомления до новой регистрации.") },
            confirmButton = { Button(onClick = { deleteTarget = null; viewModel.deleteDevice(device.deviceId) }) { Text("УДАЛИТЬ") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("ОТМЕНА") } },
        )
    }
    testTarget?.let { device -> TestPushDialog(device, { testTarget = null }) { title, body, url ->
        testTarget = null
        viewModel.testDevicePush(device.deviceId, title, body, url)
    } }
}

@Composable
private fun DeviceV2Details(
    device: AdminDevice,
    busy: Boolean,
    onTest: () -> Unit,
    onClear: () -> Unit,
    onTogglePush: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(device.name, color = TerminalGreen, style = MaterialTheme.typography.titleMedium)
        V2Line("ID", device.deviceId)
        V2Line("версия", device.appVersion.ifBlank { "неизвестно" })
        V2Line("пуши", if (device.pushEnabled) "включены" else "выключены")
        V2Line("зарегистрировано", displayDate(device.registeredAt))
        V2Line("последняя активность", displayDate(device.lastSeenAt))
        V2Line("последний пуш", listOf(displayDate(device.lastPushAt), device.lastPushStatus).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "нет" })
        V2Line("тихие часы", device.quietHours?.let { "%02d:00–%02d:00".format(it.start, it.end) } ?: "выключены")
        V2Line("отложено", device.heldCount.toString())
        V2Line("хвост токена", "••••••${device.tokenTail}")
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedButton(onClick = onTest, enabled = !busy) { Text("ТЕСТ") }
            OutlinedButton(onClick = onClear, enabled = !busy && device.heldCount > 0) { Text("ОЧИСТИТЬ") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedButton(onClick = onTogglePush, enabled = !busy) { Text(if (device.pushEnabled) "ВЫКЛ. ПУШИ" else "ВКЛ. ПУШИ") }
            TextButton(onClick = onDelete, enabled = !busy) { Text("УДАЛИТЬ", color = TerminalRed) }
        }
    }
}

@Composable
private fun TestPushDialog(device: AdminDevice, onDismiss: () -> Unit, onSend: (String, String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("Тест GsGit") }
    var body by rememberSaveable { mutableStateOf("Проверка доставки на ${device.name}") }
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Тестовый пуш") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Заголовок") })
            OutlinedTextField(body, { body = it }, label = { Text("Текст") }, minLines = 3)
            OutlinedTextField(url, { url = it }, label = { Text("Ссылка, необязательно") })
        } },
        confirmButton = { Button(onClick = { onSend(title, body, url) }, enabled = title.isNotBlank() && body.isNotBlank()) { Text("ОТПРАВИТЬ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ОТМЕНА") } },
    )
}

private enum class OperationsTab(val title: String) {
    Maintenance("Техработы"), Releases("Релизы"), Audit("Аудит"), Errors("Ошибки")
}

@Composable
fun OperationsScreen(state: AdminUiState, viewModel: AdminViewModel) {
    var tab by rememberSaveable { mutableStateOf(OperationsTab.Maintenance) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            V2Title("операции", "техработы, релизы, аудит и ошибки")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OperationsTab.entries.forEach { item ->
                    FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item.title) })
                }
            }
        }
        when (tab) {
            OperationsTab.Maintenance -> MaintenancePanel(state, viewModel)
            OperationsTab.Releases -> ReleasesPanel(state, viewModel)
            OperationsTab.Audit -> AuditPanel(state, viewModel)
            OperationsTab.Errors -> ErrorsPanel(state, viewModel)
        }
    }
}

@Composable
private fun MaintenancePanel(state: AdminUiState, viewModel: AdminViewModel) {
    var startsAt by rememberSaveable { mutableStateOf("") }
    var endsAt by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val maintenance = state.maintenance) {
            is LoadState.Ready -> V2Card {
                Text("ТЕКУЩЕЕ СОСТОЯНИЕ", style = MaterialTheme.typography.titleMedium)
                V2Line("сейчас", maintenance.value.maintenanceNow.ifBlank { "выключено" })
                maintenance.value.schedule?.let {
                    Spacer(Modifier.height(8.dp)); Text("РАСПИСАНИЕ", color = TerminalAmber)
                    V2Line("начало", displayDate(it.startsAt)); V2Line("окончание", displayDate(it.endsAt)); V2Line("сообщение", it.message)
                    OutlinedButton(onClick = { confirm = "delete" }, enabled = state.busyAction == null) { Text("УДАЛИТЬ РАСПИСАНИЕ") }
                }
                if (maintenance.value.maintenanceNow.isNotBlank() || maintenance.value.schedule != null) {
                    TextButton(onClick = { confirm = "stop" }, enabled = state.busyAction == null) { Text("ОСТАНОВИТЬ НЕМЕДЛЕННО", color = TerminalRed) }
                }
            }
            is LoadState.Error -> V2Card { Text(maintenance.message, color = TerminalRed) }
            else -> LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        V2Card {
            Text("ЗАПЛАНИРОВАТЬ", style = MaterialTheme.typography.titleMedium)
            Text("Время в ISO 8601, например 2026-07-20T01:00:00Z", color = TerminalMuted)
            V2Field(startsAt, { startsAt = it }, "Начало", "ISO 8601")
            V2Field(endsAt, { endsAt = it }, "Окончание", "ISO 8601")
            V2Field(message, { message = it }, "Сообщение", "Увидят пользователи", 3)
            Button(
                onClick = { viewModel.scheduleMaintenance(startsAt, endsAt, message) },
                enabled = state.busyAction == null && startsAt.isNotBlank() && endsAt.isNotBlank() && message.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("СОХРАНИТЬ РАСПИСАНИЕ") }
        }
        Spacer(Modifier.height(20.dp))
    }
    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (action == "stop") "Остановить техработы?" else "Удалить расписание?") },
            text = { Text(if (action == "stop") "Блокировка и расписание будут сняты немедленно." else "Если окно уже включило блокировку, она также будет снята.") },
            confirmButton = { Button(onClick = { confirm = null; if (action == "stop") viewModel.stopMaintenance() else viewModel.deleteMaintenanceSchedule() }) { Text("ПОДТВЕРДИТЬ") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("ОТМЕНА") } },
        )
    }
}

@Composable
private fun ReleasesPanel(state: AdminUiState, viewModel: AdminViewModel) {
    var version by rememberSaveable { mutableStateOf("") }
    var changelog by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var sha256 by rememberSaveable { mutableStateOf("") }
    var mandatory by rememberSaveable { mutableStateOf(false) }
    var rollout by rememberSaveable { mutableStateOf("100") }
    var publishTarget by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            V2Card {
                Text("ДОБАВИТЬ ИЛИ ОБНОВИТЬ РЕЛИЗ", style = MaterialTheme.typography.titleMedium)
                V2Field(version, { version = it }, "Версия", "x.y.z")
                V2Field(changelog, { changelog = it }, "Что нового", "Необязательно", 4)
                V2Field(url, { url = it }, "URL", "Ссылка на APK")
                V2Field(sha256, { sha256 = it }, "SHA-256", "Контрольная сумма APK")
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(mandatory, { mandatory = it }); Text("Обязательное обновление") }
                V2Field(rollout, { rollout = it.filter(Char::isDigit).take(3) }, "Процент раздачи", "0–100", keyboardType = KeyboardType.Number)
                Button(onClick = {
                    viewModel.saveRelease(ReleaseRecord(version.trim(), changelog.trim(), url.trim(), sha256.trim(), mandatory, rollout.toIntOrNull()?.coerceIn(0, 100) ?: 100))
                }, enabled = state.busyAction == null && version.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("СОХРАНИТЬ РЕЛИЗ") }
            }
        }
        when (val releases = state.releases) {
            is LoadState.Ready -> if (releases.value.items.isEmpty()) item { V2Card { Text("Релизов пока нет", color = TerminalMuted) } } else items(releases.value.items, key = { it.version }) { release ->
                V2Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(release.version, style = MaterialTheme.typography.titleLarge, color = TerminalGreen, modifier = Modifier.weight(1f))
                        if (release.mandatory) Text("ОБЯЗАТЕЛЬНЫЙ", color = TerminalRed)
                    }
                    if (release.changelog.isNotBlank()) Text(release.changelog, maxLines = 4)
                    V2Line("раздача", "${release.rollout}%")
                    V2Line("опубликован", displayDate(release.publishedAt).ifBlank { "нет" })
                    Button(onClick = { publishTarget = release.version }, enabled = state.busyAction == null, modifier = Modifier.fillMaxWidth()) { Text("ОПУБЛИКОВАТЬ АТОМАРНО") }
                }
            }
            is LoadState.Error -> item { V2Card { Text(releases.message, color = TerminalRed) } }
            else -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
    publishTarget?.let { target -> AlertDialog(
        onDismissRequest = { publishTarget = null },
        title = { Text("Опубликовать $target?") },
        text = { Text("Сервер атомарно обновит latestVersion, changelog, URL и при необходимости minVersion.") },
        confirmButton = { Button(onClick = { publishTarget = null; viewModel.publishRelease(target) }) { Text("ОПУБЛИКОВАТЬ") } },
        dismissButton = { TextButton(onClick = { publishTarget = null }) { Text("ОТМЕНА") } },
    ) }
}

@Composable
private fun AuditPanel(state: AdminUiState, viewModel: AdminViewModel) {
    when (val audit = state.audit) {
        is LoadState.Ready -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OutlinedButton(onClick = viewModel::loadAudit) { Text("ОБНОВИТЬ") } }
            if (audit.value.items.isEmpty()) item { V2Card { Text("Журнал пуст", color = TerminalMuted) } }
            items(audit.value.items, key = { it.id }) { record -> V2Card {
                Row { Text(record.action, color = TerminalGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(record.result) }
                V2Line("время", displayDate(record.at)); V2Line("IP", record.ip)
                if (record.meta.isNotBlank() && record.meta != "{}") Text(record.meta, color = TerminalMuted, style = MaterialTheme.typography.labelSmall)
            } }
            item { Spacer(Modifier.height(20.dp)) }
        }
        is LoadState.Error -> V2State(audit.message, "ПОВТОРИТЬ", viewModel::loadAudit)
        else -> V2State("загрузка журнала")
    }
}

@Composable
private fun ErrorsPanel(state: AdminUiState, viewModel: AdminViewModel) {
    var service by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 18.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("" to "Все", "push" to "Пуши", "github" to "GitHub", "database" to "База").forEach { (value, label) ->
                FilterChip(selected = service == value, onClick = { service = value; viewModel.loadErrors(value) }, label = { Text(label) })
            }
        }
        when (val errors = state.errors) {
            is LoadState.Ready -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (errors.value.isEmpty()) item { V2Card { Text("Серверных ошибок нет", color = TerminalGreen) } }
                items(errors.value, key = { it.id }) { error -> V2Card {
                    Row { Text(error.code, color = TerminalRed, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("×${error.count}") }
                    Text(error.message)
                    V2Line("сервис", error.service); V2Line("первая", displayDate(error.createdAt)); V2Line("последняя", displayDate(error.lastAt))
                } }
            }
            is LoadState.Error -> V2State(errors.message, "ПОВТОРИТЬ") { viewModel.loadErrors(service) }
            else -> V2State("загрузка ошибок")
        }
    }
}

@Composable
private fun V2Title(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("> $title", style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, color = TerminalMuted)
    }
}

@Composable
private fun V2Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = TerminalSurface, border = BorderStroke(1.dp, TerminalBorder), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(15.dp), content = content)
    }
}

@Composable
private fun V2Metric(label: String, value: String, modifier: Modifier = Modifier) {
    V2Card(modifier) { Text(label, color = TerminalMuted, style = MaterialTheme.typography.labelMedium); Text(value, color = TerminalGreen, style = MaterialTheme.typography.displaySmall) }
}

@Composable
private fun V2Line(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", color = TerminalMuted, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun V2Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    help: String,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value, onValueChange, label = { Text(label) }, supportingText = { Text(help) },
        minLines = minLines, maxLines = if (minLines == 1) 1 else 10,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun V2State(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (action == null) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            Text(message, color = TerminalMuted)
            if (action != null && onAction != null) OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

private fun displayDate(raw: String): String = raw
    .replace("T", " ")
    .replace(".000Z", " UTC")
    .replace("Z", " UTC")

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return "${days}д ${hours}ч ${minutes}м"
}
