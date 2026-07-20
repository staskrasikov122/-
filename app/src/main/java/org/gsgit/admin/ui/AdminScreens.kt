package org.gsgit.admin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gsgit.admin.data.*
import org.gsgit.admin.ui.theme.AdminTheme

@Composable
fun DashboardV3Screen(state: AdminUiState, viewModel: AdminViewModel) {
    val stats = (state.stats as? LoadState.Ready)?.value
    if (stats == null) {
        AdminStatePanel((state.stats as? LoadState.Error)?.message ?: "загрузка сводки", state.stats is LoadState.Error, viewModel::loadDashboard)
        return
    }
    var maintenanceDialog by rememberSaveable { mutableStateOf(false) }
    var maintenanceMessage by rememberSaveable { mutableStateOf("") }
    val maintenanceOn = stats.maintenance.isNotBlank() && !stats.maintenance.equals("off", true)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = adminScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { AdminPageTitle("обзор", "здоровье сервера и реальные метрики") }
        item {
            when (val health = state.health) {
                is LoadState.Ready -> AdminCard {
                    val ok = health.value.status.equals("ok", true)
                    AdminText(if (ok) "● ВСЕ СИСТЕМЫ РАБОТАЮТ" else "! СЕРВЕР ${health.value.status.uppercase()}", color = if (ok) AdminTheme.colors.accent else AdminTheme.colors.error, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    AdminKeyValue("версия", health.value.serverVersion)
                    AdminKeyValue("время работы", formatUptime(health.value.uptimeSec))
                    AdminKeyValue("база данных", health.value.database)
                    AdminKeyValue("Firebase", health.value.firebase)
                    AdminKeyValue("GitHub hooks", health.value.githubWebhooks)
                    AdminKeyValue("очередь пушей", health.value.pushQueue.toString())
                    AdminKeyValue("время сервера", displayDate(health.value.serverTime))
                }
                is LoadState.Error -> AdminCard { AdminText("! ${health.message}", color = AdminTheme.colors.error) }
                else -> AdminCard { AdminSpinner("проверка компонентов") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AdminMetricCard("УСТРОЙСТВА", stats.devices.toString(), Modifier.weight(1f))
                AdminMetricCard("АККАУНТЫ", stats.logins.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AdminMetricCard("ТИХИЙ РЕЖИМ", stats.quietEnabled.toString(), Modifier.weight(1f))
                AdminMetricCard("ОТЛОЖЕНО", stats.heldPushes.toString(), Modifier.weight(1f))
            }
        }
        item {
            AdminCard {
                AdminSectionLabel("период метрик")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1h" to "1 час", "24h" to "24 часа", "7d" to "7 дней", "30d" to "30 дней").forEach { (period, label) ->
                        AdminChip(label, state.metricsPeriod == period) { viewModel.loadMetrics(period) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                when (val metrics = state.metrics) {
                    is LoadState.Ready -> {
                        AdminKeyValue("регистрации", metrics.value.registrations.toString())
                        AdminKeyValue("активные", metrics.value.activeDevices.toString())
                        AdminKeyValue("пуши / ошибки", "${metrics.value.pushesSent} / ${metrics.value.pushesFailed}")
                        AdminKeyValue("GitHub события", metrics.value.githubEvents.toString())
                        AdminKeyValue("запросы", metrics.value.requests.toString())
                        AdminKeyValue("4xx / 5xx", "${metrics.value.responses4xx} / ${metrics.value.responses5xx}")
                    }
                    is LoadState.Error -> AdminText(metrics.message, color = AdminTheme.colors.error)
                    else -> AdminSpinner("загрузка метрик")
                }
            }
        }
        item {
            AdminCard {
                AdminSectionLabel("аварийная блокировка")
                Spacer(Modifier.height(7.dp))
                AdminText(if (maintenanceOn) "ВКЛЮЧЕНА" else "ВЫКЛЮЧЕНА", color = if (maintenanceOn) AdminTheme.colors.error else AdminTheme.colors.accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                AdminText(if (maintenanceOn) stats.maintenance else "клиенты работают в обычном режиме", color = AdminTheme.colors.textSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                AdminPillButton(if (maintenanceOn) "снять блокировку" else "включить блокировку", { maintenanceDialog = true }, enabled = !state.togglingKillSwitch, destructive = !maintenanceOn)
            }
        }
        item { AdminCard { AdminSectionLabel("версии"); Spacer(Modifier.height(7.dp)); AdminKeyValue("последняя", stats.latestVersion); AdminKeyValue("минимальная", stats.minVersion) } }
    }

    if (maintenanceDialog) AdminDialog(
        onDismissRequest = { maintenanceDialog = false },
        title = if (maintenanceOn) "снять блокировку" else "включить блокировку",
        confirmLabel = if (maintenanceOn) "разблокировать" else "заблокировать",
        onConfirm = { maintenanceDialog = false; viewModel.setMaintenance(if (maintenanceOn) "" else maintenanceMessage) },
        confirmEnabled = maintenanceOn || maintenanceMessage.isNotBlank(),
        destructive = !maintenanceOn,
    ) {
        if (maintenanceOn) AdminText("Клиенты разблокируются после следующей проверки сервера.", color = AdminTheme.colors.textSecondary)
        else AdminTextField(maintenanceMessage, { maintenanceMessage = it }, label = "Сообщение пользователям", placeholder = "Причина технических работ", minLines = 3, maxLines = 6)
    }
}

@Composable
fun AppConfigV3Screen(state: AdminUiState, viewModel: AdminViewModel) {
    val serverConfig = (state.config as? LoadState.Ready)?.value
    if (serverConfig == null) {
        AdminStatePanel((state.config as? LoadState.Error)?.message ?: "загрузка настроек", state.config is LoadState.Error, viewModel::loadConfig)
        return
    }
    var config by remember(serverConfig) { mutableStateOf(serverConfig) }
    var reason by rememberSaveable { mutableStateOf("") }
    var preview by rememberSaveable { mutableStateOf(false) }
    var rollbackTarget by rememberSaveable { mutableStateOf<Int?>(null) }
    val changes = remember(serverConfig, config) { configChanges(serverConfig, config) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(adminScreenPadding()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdminPageTitle("конфигурация", "проверка, предпросмотр и безопасный откат")
        AdminCard {
            AdminSectionLabel("параметры клиентов")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminField(config.maintenanceSoon, { config = config.copy(maintenanceSoon = it) }, "Скоро техработы", "Пусто — предупреждение отключено", 2)
                AdminField(config.maintenance, { config = config.copy(maintenance = it) }, "Техработы сейчас", "Полная блокировка клиентов", 3)
                AdminField(config.latestVersion, { config = config.copy(latestVersion = it) }, "Последняя версия", "Формат x.y.z")
                AdminField(config.minVersion, { config = config.copy(minVersion = it) }, "Минимальная версия", "Старые клиенты будут заблокированы")
                AdminField(config.changelog, { config = config.copy(changelog = it) }, "Что нового", "Описание изменений", 5)
                AdminField(config.downloadUrl, { config = config.copy(downloadUrl = it) }, "Ссылка на APK", "HTTPS-адрес загрузки")
            }
        }
        AdminCard {
            AdminSectionLabel("применение")
            Spacer(Modifier.height(10.dp))
            AdminField(reason, { reason = it }, "Причина изменения", "Попадёт в ревизию и аудит", 2)
            Spacer(Modifier.height(12.dp))
            AdminPillButton("показать и сохранить ${changes.size} изм.", { preview = true }, Modifier.fillMaxWidth(), enabled = !state.savingConfig && changes.isNotEmpty())
        }
        AdminCard {
            AdminSectionLabel("история конфигурации")
            Spacer(Modifier.height(7.dp))
            when (val history = state.configHistory) {
                is LoadState.Ready -> if (history.value.items.isEmpty()) AdminText("история пуста", color = AdminTheme.colors.textMuted) else history.value.items.forEach { revision ->
                    AdminText("#${revision.revision} · ${displayDate(revision.changedAt)}", color = AdminTheme.colors.accent, fontWeight = FontWeight.Medium)
                    AdminText(revision.changedFields.joinToString().ifBlank { "поля не указаны" }, color = AdminTheme.colors.textSecondary, fontSize = 10.sp)
                    if (revision.reason.isNotBlank()) AdminText("причина: ${revision.reason}", color = AdminTheme.colors.textMuted, fontSize = 10.sp)
                    AdminTextAction("откатить к этой ревизии", { rollbackTarget = revision.revision }, enabled = state.busyAction == null)
                    AdminHairline(Modifier.padding(vertical = 6.dp))
                }
                is LoadState.Error -> AdminText(history.message, color = AdminTheme.colors.error)
                else -> AdminSpinner("загрузка истории")
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (preview) AdminDialog(
        onDismissRequest = { preview = false }, title = "предпросмотр изменений", confirmLabel = "проверить и сохранить",
        onConfirm = { preview = false; viewModel.saveConfig(config, reason) }, confirmEnabled = changes.isNotEmpty() && !state.savingConfig,
    ) {
        if (changes.isEmpty()) AdminText("Изменений нет", color = AdminTheme.colors.textMuted)
        else changes.forEach { change ->
            AdminText(change.label, color = AdminTheme.colors.accent, fontWeight = FontWeight.Medium)
            AdminText("− ${change.before.ifBlank { "<пусто>" }}", color = AdminTheme.colors.error, fontSize = 10.sp, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            AdminText("+ ${change.after.ifBlank { "<пусто>" }}", color = AdminTheme.colors.accent, fontSize = 10.sp, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
        }
    }
    rollbackTarget?.let { revision ->
        TypedConfirmationDialog(
            title = "откатить конфигурацию",
            description = "Будет применён снимок ревизии #$revision.",
            required = "ОТКАТИТЬ",
            onConfirm = { rollbackTarget = null; viewModel.rollbackConfig(revision) },
            onDismiss = { rollbackTarget = null },
        )
    }
}

@Composable
fun AnnounceV3Screen(state: AdminUiState, viewModel: AdminViewModel) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var sendConfirm by rememberSaveable { mutableStateOf(false) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val recipients = (state.stats as? LoadState.Ready)?.value?.devices

    LazyColumn(Modifier.fillMaxSize(), contentPadding = adminScreenPadding(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { AdminPageTitle("пуши", "рассылка, история и повтор ошибок") }
        item { AdminCard {
            AdminSectionLabel("новая рассылка")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminField(title, { title = it }, "Заголовок", "Обязательно")
                AdminField(body, { body = it }, "Текст", "Обязательно", 4)
                AdminField(url, { url = it }, "Ссылка", "Необязательно")
            }
            Spacer(Modifier.height(12.dp))
            AdminPillButton("отправить на ${recipients ?: 0} устройств", { sendConfirm = true }, Modifier.fillMaxWidth(), enabled = recipients != null && title.isNotBlank() && body.isNotBlank() && !state.sendingAnnouncement)
        } }
        item { AdminSectionLabel("история", Modifier.padding(start = 4.dp, top = 6.dp)) }
        when (val history = state.announcements) {
            is LoadState.Ready -> if (history.value.items.isEmpty()) item { AdminCard { AdminText("рассылок пока нет", color = AdminTheme.colors.textMuted) } } else items(history.value.items, key = { it.id }) { record ->
                AdminCard(Modifier.clickable { detailsOpen = true; viewModel.loadAnnouncementDetails(record.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AdminText(record.title.ifBlank { "без заголовка" }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        AdminChip(record.status, selected = record.failed == 0, destructive = record.failed > 0)
                    }
                    AdminText(displayDate(record.createdAt), color = AdminTheme.colors.textMuted, fontSize = 10.sp)
                    AdminKeyValue("получателей", record.targeted.toString())
                    AdminKeyValue("доставлено / ошибок", "${record.delivered} / ${record.failed}")
                }
            }
            is LoadState.Error -> item { AdminCard { AdminText(history.message, color = AdminTheme.colors.error) } }
            else -> item { AdminSpinner("загрузка истории") }
        }
    }

    if (sendConfirm && recipients != null) AdminDialog(
        onDismissRequest = { sendConfirm = false }, title = "отправить всем", confirmLabel = "отправить",
        onConfirm = { sendConfirm = false; viewModel.sendAnnouncement(title, body, url) { title = ""; body = ""; url = "" } },
        destructive = true,
    ) { AdminText("«$title» будет отправлено на $recipients устройств.", color = AdminTheme.colors.textSecondary) }

    if (detailsOpen) AdminDialog(
        onDismissRequest = { detailsOpen = false }, title = "детали рассылки", confirmLabel = "закрыть", onConfirm = { detailsOpen = false }, dismissLabel = "",
    ) {
        when (val details = state.announcementDetails) {
            is LoadState.Ready -> {
                AdminText(details.value.title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp)); AdminText(details.value.body)
                if (details.value.url.isNotBlank()) AdminText(details.value.url, color = AdminTheme.colors.accent)
                AdminKeyValue("доставлено", details.value.delivered.toString()); AdminKeyValue("ошибок", details.value.failed.toString())
                if (details.value.failed > 0) AdminPillButton("повторить ошибки", { detailsOpen = false; viewModel.retryAnnouncement(details.value.id) }, enabled = state.busyAction == null)
                AdminTextAction("проверить отмену", { detailsOpen = false; viewModel.cancelAnnouncement(details.value.id) })
            }
            is LoadState.Error -> AdminText(details.message, color = AdminTheme.colors.error)
            else -> AdminSpinner("загрузка деталей")
        }
    }
}

@Composable
fun DevicesV3Screen(state: AdminUiState, viewModel: AdminViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var activeOnly by rememberSaveable { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var deleteTarget by remember { mutableStateOf<AdminDevice?>(null) }
    var testTarget by remember { mutableStateOf<AdminDevice?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = adminScreenPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { AdminPageTitle("устройства", "диагностика и адресные действия") }
        item { AdminCard {
            AdminTextField(query, { query = it }, label = "Фильтр по логину", placeholder = "часть логина")
            Spacer(Modifier.height(4.dp))
            AdminCheckRow("Только активные пуши", activeOnly, { activeOnly = !activeOnly })
            Spacer(Modifier.height(4.dp))
            AdminPillButton("найти", { viewModel.loadDevices(query, activeOnly) }, Modifier.fillMaxWidth())
        } }
        when (val response = state.devices) {
            is LoadState.Ready -> {
                item { AdminText("аккаунтов: ${response.value.logins} · устройств: ${response.value.totalDevices}", color = AdminTheme.colors.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp)) }
                if (response.value.devices.isEmpty()) item { AdminCard { AdminText("устройства не найдены", color = AdminTheme.colors.textMuted) } }
                items(response.value.devices, key = { it.login }) { group ->
                    AdminCard(Modifier.clickable { expanded = if (group.login in expanded) expanded - group.login else expanded + group.login }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { AdminText("@${group.login}", fontWeight = FontWeight.Bold); AdminText("устройств: ${group.count}", color = AdminTheme.colors.textMuted, fontSize = 10.sp) }
                            AdminText(if (group.login in expanded) "⌃" else "⌄", color = AdminTheme.colors.accent, fontSize = 16.sp)
                        }
                        AnimatedVisibility(group.login in expanded) {
                            Column {
                                group.devices.forEach { device ->
                                    AdminHairline(Modifier.padding(vertical = 9.dp))
                                    DeviceDetailsV3(device, state.busyAction != null, { testTarget = device }, { viewModel.clearHeld(device.deviceId) }, { viewModel.setDevicePush(device.deviceId, !device.pushEnabled) }, { deleteTarget = device })
                                }
                            }
                        }
                    }
                }
            }
            is LoadState.Error -> item { AdminCard { AdminText(response.message, color = AdminTheme.colors.error) } }
            else -> item { AdminSpinner("загрузка устройств") }
        }
    }
    deleteTarget?.let { device -> TypedConfirmationDialog("удалить регистрацию", "${device.name}\nID: ${device.deviceId}", "УДАЛИТЬ", { deleteTarget = null; viewModel.deleteDevice(device.deviceId) }, { deleteTarget = null }) }
    testTarget?.let { device -> TestPushDialogV3(device, { testTarget = null }) { title, text, link -> testTarget = null; viewModel.testDevicePush(device.deviceId, title, text, link) } }
}

@Composable
private fun DeviceDetailsV3(device: AdminDevice, busy: Boolean, onTest: () -> Unit, onClear: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    AdminText(device.name, color = AdminTheme.colors.accent, fontWeight = FontWeight.Bold)
    AdminKeyValue("ID", device.deviceId); AdminKeyValue("версия", device.appVersion.ifBlank { "неизвестно" })
    AdminKeyValue("пуши", if (device.pushEnabled) "включены" else "выключены", if (device.pushEnabled) AdminTheme.colors.accent else AdminTheme.colors.error)
    AdminKeyValue("регистрация", displayDate(device.registeredAt)); AdminKeyValue("активность", displayDate(device.lastSeenAt))
    AdminKeyValue("последний пуш", "${displayDate(device.lastPushAt)} ${device.lastPushStatus}".trim().ifBlank { "нет" })
    AdminKeyValue("отложено", device.heldCount.toString()); AdminKeyValue("токен", "••••••${device.tokenTail}")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AdminPillButton("тест", onTest, enabled = !busy)
        AdminPillButton("очистить", onClear, enabled = !busy && device.heldCount > 0, accent = false)
        AdminPillButton(if (device.pushEnabled) "выкл. пуши" else "вкл. пуши", onToggle, enabled = !busy, accent = false)
        AdminPillButton("удалить", onDelete, enabled = !busy, destructive = true)
    }
}

@Composable
private fun TestPushDialogV3(device: AdminDevice, onDismiss: () -> Unit, onSend: (String, String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("Тест GsGit") }; var body by rememberSaveable { mutableStateOf("Проверка доставки на ${device.name}") }; var url by rememberSaveable { mutableStateOf("") }
    AdminDialog(onDismiss, "тестовый пуш", "отправить", { onSend(title, body, url) }, title.isNotBlank() && body.isNotBlank()) {
        AdminTextField(title, { title = it }, label = "Заголовок"); Spacer(Modifier.height(7.dp))
        AdminTextField(body, { body = it }, label = "Текст", minLines = 3, maxLines = 5); Spacer(Modifier.height(7.dp))
        AdminTextField(url, { url = it }, label = "Ссылка")
    }
}

private enum class OperationsTab(val label: String) { Maintenance("техработы"), Releases("релизы"), Audit("аудит"), Errors("ошибки"), Security("защита") }

@Composable
fun OperationsV3Screen(state: AdminUiState, viewModel: AdminViewModel) {
    var tab by rememberSaveable { mutableStateOf(OperationsTab.Maintenance) }
    Column(Modifier.fillMaxSize().padding(top = adminTopChromeInset())) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminPageTitle("операции", "серверное управление и защита админки")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OperationsTab.entries.forEach { AdminChip(it.label, tab == it) { tab = it } }
            }
        }
        when (tab) {
            OperationsTab.Maintenance -> MaintenancePanelV3(state, viewModel)
            OperationsTab.Releases -> ReleasesPanelV3(state, viewModel)
            OperationsTab.Audit -> AuditPanelV3(state, viewModel)
            OperationsTab.Errors -> ErrorsPanelV3(state, viewModel)
            OperationsTab.Security -> SecurityPanelV3(state, viewModel)
        }
    }
}

@Composable
private fun MaintenancePanelV3(state: AdminUiState, viewModel: AdminViewModel) {
    var starts by rememberSaveable { mutableStateOf("") }; var ends by rememberSaveable { mutableStateOf("") }; var message by rememberSaveable { mutableStateOf("") }; var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(adminPanelPadding()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        when (val maintenance = state.maintenance) {
            is LoadState.Ready -> AdminCard {
                AdminSectionLabel("текущее состояние"); AdminKeyValue("сейчас", maintenance.value.maintenanceNow.ifBlank { "выключено" })
                maintenance.value.schedule?.let { AdminKeyValue("начало", displayDate(it.startsAt)); AdminKeyValue("окончание", displayDate(it.endsAt)); AdminKeyValue("сообщение", it.message); AdminTextAction("удалить расписание", { confirm = "delete" }, destructive = true) }
                if (maintenance.value.maintenanceNow.isNotBlank() || maintenance.value.schedule != null) AdminPillButton("остановить немедленно", { confirm = "stop" }, destructive = true)
            }
            is LoadState.Error -> AdminCard { AdminText(maintenance.message, color = AdminTheme.colors.error) }
            else -> AdminSpinner("загрузка техработ")
        }
        AdminCard {
            AdminSectionLabel("запланировать")
            Spacer(Modifier.height(4.dp))
            AdminText("Формат времени ISO 8601, например 2026-07-20T01:00:00Z", color = AdminTheme.colors.textMuted, fontSize = 10.sp)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminField(starts, { starts = it }, "Начало", "ISO 8601"); AdminField(ends, { ends = it }, "Окончание", "ISO 8601"); AdminField(message, { message = it }, "Сообщение", "Увидят пользователи", 3)
            }
            Spacer(Modifier.height(12.dp))
            AdminPillButton("сохранить расписание", { viewModel.scheduleMaintenance(starts, ends, message) }, Modifier.fillMaxWidth(), enabled = state.busyAction == null && starts.isNotBlank() && ends.isNotBlank() && message.isNotBlank())
        }
    }
    confirm?.let { action -> TypedConfirmationDialog(if (action == "stop") "остановить техработы" else "удалить расписание", "Блокировка и расписание будут сняты.", if (action == "stop") "ОСТАНОВИТЬ" else "УДАЛИТЬ", { confirm = null; if (action == "stop") viewModel.stopMaintenance() else viewModel.deleteMaintenanceSchedule() }, { confirm = null }) }
}

@Composable
private fun ReleasesPanelV3(state: AdminUiState, viewModel: AdminViewModel) {
    var version by rememberSaveable { mutableStateOf("") }; var changelog by rememberSaveable { mutableStateOf("") }; var url by rememberSaveable { mutableStateOf("") }; var sha by rememberSaveable { mutableStateOf("") }; var mandatory by rememberSaveable { mutableStateOf(false) }; var rollout by rememberSaveable { mutableStateOf("100") }; var publish by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = adminPanelPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { AdminCard {
            AdminSectionLabel("добавить или обновить релиз")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminField(version, { version = it }, "Версия", "x.y.z"); AdminField(changelog, { changelog = it }, "Что нового", "Необязательно", 4); AdminField(url, { url = it }, "URL", "Ссылка на APK"); AdminField(sha, { sha = it }, "SHA-256", "64 шестнадцатеричных символа")
            }
            AdminCheckRow("Обязательное обновление", mandatory, { mandatory = !mandatory })
            AdminField(rollout, { rollout = it.filter(Char::isDigit).take(3) }, "Процент раздачи", "0–100", keyboardType = KeyboardType.Number)
            Spacer(Modifier.height(12.dp))
            AdminPillButton("сохранить релиз", { viewModel.saveRelease(ReleaseRecord(version.trim(), changelog.trim(), url.trim(), sha.trim(), mandatory, rollout.toIntOrNull()?.coerceIn(0,100) ?: 100)) }, Modifier.fillMaxWidth(), enabled = state.busyAction == null && version.isNotBlank())
        } }
        when (val releases = state.releases) {
            is LoadState.Ready -> if (releases.value.items.isEmpty()) item { AdminCard { AdminText("релизов пока нет", color = AdminTheme.colors.textMuted) } } else items(releases.value.items, key = { it.version }) { release -> AdminCard {
                Row { AdminText(release.version, color = AdminTheme.colors.accent, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); if (release.mandatory) AdminChip("обязательный", destructive = true) }
                if (release.changelog.isNotBlank()) AdminText(release.changelog, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                AdminKeyValue("раздача", "${release.rollout}%"); AdminKeyValue("опубликован", displayDate(release.publishedAt).ifBlank { "нет" })
                AdminPillButton("опубликовать атомарно", { publish = release.version }, enabled = state.busyAction == null)
            } }
            is LoadState.Error -> item { AdminCard { AdminText(releases.message, color = AdminTheme.colors.error) } }
            else -> item { AdminSpinner("загрузка релизов") }
        }
    }
    publish?.let { target -> TypedConfirmationDialog("опубликовать $target", "Будут атомарно обновлены версия, changelog, URL и при необходимости minVersion.", "ОПУБЛИКОВАТЬ", { publish = null; viewModel.publishRelease(target) }, { publish = null }) }
}

@Composable
private fun AuditPanelV3(state: AdminUiState, viewModel: AdminViewModel) {
    when (val audit = state.audit) {
        is LoadState.Ready -> LazyColumn(Modifier.fillMaxSize(), contentPadding = adminPanelPadding(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { AdminPillButton("обновить", viewModel::loadAudit) }
            if (audit.value.items.isEmpty()) item { AdminCard { AdminText("журнал пуст", color = AdminTheme.colors.textMuted) } }
            items(audit.value.items, key = { it.id }) { record -> AdminCard {
                Row { AdminText(record.action, color = AdminTheme.colors.accent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); AdminChip(record.result, selected = record.result.equals("ok", true), destructive = !record.result.equals("ok", true)) }
                AdminKeyValue("время", displayDate(record.at)); AdminKeyValue("IP", record.ip); if (record.meta.isNotBlank() && record.meta != "{}") AdminText(record.meta, color = AdminTheme.colors.textMuted, fontSize = 10.sp)
            } }
        }
        is LoadState.Error -> AdminStatePanel(audit.message, true, viewModel::loadAudit)
        else -> AdminStatePanel("загрузка аудита")
    }
}

@Composable
private fun ErrorsPanelV3(state: AdminUiState, viewModel: AdminViewModel) {
    var service by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("" to "все", "push" to "пуши", "github" to "GitHub", "database" to "база").forEach { (value,label) -> AdminChip(label, service == value) { service = value; viewModel.loadErrors(value) } } }
        when (val errors = state.errors) {
            is LoadState.Ready -> LazyColumn(Modifier.fillMaxSize(), contentPadding = adminPanelPadding(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (errors.value.isEmpty()) item { AdminCard { AdminText("серверных ошибок нет", color = AdminTheme.colors.accent) } }
                items(errors.value, key = { it.id }) { error -> AdminCard { Row { AdminText(error.code, color = AdminTheme.colors.error, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); AdminText("×${error.count}") }; AdminText(error.message); AdminKeyValue("сервис", error.service); AdminKeyValue("последняя", displayDate(error.lastAt)) } }
            }
            is LoadState.Error -> AdminStatePanel(errors.message, true) { viewModel.loadErrors(service) }
            else -> AdminStatePanel("загрузка ошибок")
        }
    }
}

@Composable
private fun SecurityPanelV3(state: AdminUiState, viewModel: AdminViewModel) {
    var logoutConfirm by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(adminPanelPadding()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        AdminCard {
            AdminSectionLabel("защита экрана")
            AdminKeyValue("скриншоты", "разрешены")
            AdminKeyValue("запись экрана", "разрешена")
            AdminCheckRow("Биометрическая блокировка", state.biometricEnabled, { viewModel.setBiometricEnabled(!state.biometricEnabled) }, "Используется системный отпечаток, лицо или код устройства")
        }
        AdminCard {
            AdminSectionLabel("блокировать после сворачивания")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(1,5,15).forEach { minutes -> AdminChip("$minutes мин", state.lockTimeoutMinutes == minutes) { viewModel.setLockTimeout(minutes) } } }
            Spacer(Modifier.height(8.dp)); AdminText("Короткое сворачивание не сбрасывает текущий экран. После тайм-аута приложение вернётся на него после аутентификации.", color = AdminTheme.colors.textMuted, fontSize = 10.sp)
        }
        AdminCard {
            AdminSectionLabel("сессия")
            Spacer(Modifier.height(8.dp)); AdminPillButton("заблокировать сейчас", viewModel::lock); Spacer(Modifier.height(6.dp)); AdminPillButton("удалить сохранённый ключ", { logoutConfirm = true }, destructive = true)
        }
    }
    if (logoutConfirm) TypedConfirmationDialog("удалить сохранённый ключ", "Для следующего входа потребуется полный X-Admin-Key.", "ВЫЙТИ", { logoutConfirm = false; viewModel.logout() }, { logoutConfirm = false })
}

@Composable
private fun TypedConfirmationDialog(title: String, description: String, required: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var entered by rememberSaveable { mutableStateOf("") }
    AdminDialog(onDismiss, title, required.lowercase(), onConfirm, entered == required, destructive = true) {
        AdminText(description, color = AdminTheme.colors.textSecondary); Spacer(Modifier.height(8.dp)); AdminText("Для подтверждения введите: $required", color = AdminTheme.colors.warning, fontSize = 10.sp); Spacer(Modifier.height(6.dp)); AdminTextField(entered, { entered = it }, placeholder = required)
    }
}

@Composable
private fun AdminField(value: String, onChange: (String) -> Unit, label: String, help: String, minLines: Int = 1, keyboardType: KeyboardType = KeyboardType.Text) {
    AdminTextField(value, onChange, label = label, placeholder = help, minLines = minLines, maxLines = if (minLines == 1) 1 else 10, keyboardOptions = KeyboardOptions(keyboardType = keyboardType))
}

@Composable
private fun AdminMetricCard(label: String, value: String, modifier: Modifier = Modifier) { AdminCard(modifier) { AdminText(label, color = AdminTheme.colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.height(2.dp)); AdminText(value, color = AdminTheme.colors.accent, fontSize = 24.sp, fontWeight = FontWeight.Bold) } }

@Composable
private fun AdminStatePanel(message: String, error: Boolean = false, retry: (() -> Unit)? = null) { Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) { AdminCard(Modifier.widthIn(max = 440.dp)) { if (error) AdminText("! $message", color = AdminTheme.colors.error) else AdminSpinner(message); if (retry != null) { Spacer(Modifier.height(9.dp)); AdminPillButton("повторить", retry) } } } }

private data class ConfigChange(val label: String, val before: String, val after: String)
private fun configChanges(old: AppConfig, new: AppConfig) = buildList {
    fun add(label: String, before: String, after: String) { if (before != after) add(ConfigChange(label, before, after)) }
    add("Скоро техработы", old.maintenanceSoon, new.maintenanceSoon); add("Техработы сейчас", old.maintenance, new.maintenance); add("Последняя версия", old.latestVersion, new.latestVersion); add("Минимальная версия", old.minVersion, new.minVersion); add("Что нового", old.changelog, new.changelog); add("Ссылка на APK", old.downloadUrl, new.downloadUrl)
}
private fun displayDate(raw: String) = raw.replace("T", " ").replace(".000Z", " UTC").replace("Z", " UTC")
private fun formatUptime(seconds: Long): String { val days = seconds / 86_400; val hours = (seconds % 86_400) / 3_600; val minutes = (seconds % 3_600) / 60; return "${days}д ${hours}ч ${minutes}м" }
