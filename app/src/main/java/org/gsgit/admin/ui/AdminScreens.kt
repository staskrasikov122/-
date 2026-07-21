package org.gsgit.admin.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.RoundedRectangle
import org.gsgit.admin.data.*
import org.gsgit.admin.ui.kyant.components.AnimatedListItem
import org.gsgit.admin.ui.kyant.components.LiquidSlider
import org.gsgit.admin.ui.kyant.utils.LiquidMotion
import org.gsgit.admin.ui.kyant.utils.liquidClickable
import org.gsgit.admin.ui.liquid.LocalLiquidBackdrop
import org.gsgit.admin.ui.theme.AdminTheme
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

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
                AdminSectionLabel("автообновление")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "выкл.", 15 to "15 сек", 30 to "30 сек", 60 to "60 сек").forEach { (seconds, label) ->
                        AdminChip(label, state.autoRefreshSeconds == seconds) { viewModel.setAutoRefresh(seconds) }
                    }
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
        item {
            AdminCard {
                AdminSectionLabel("быстрые действия")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdminPillButton("тестовый пуш", { viewModel.selectSection(Section.Devices) }, accent = false)
                    AdminPillButton("рассылка", { viewModel.selectSection(Section.Announce) }, accent = false)
                    AdminPillButton("новый релиз", { viewModel.openOperations("releases") }, accent = false)
                    if (maintenanceOn) AdminPillButton("остановить техработы", viewModel::stopMaintenance, destructive = true)
                }
            }
        }
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
    var config by remember(serverConfig) { mutableStateOf(state.configDraft ?: serverConfig) }
    var reason by remember(serverConfig) { mutableStateOf(if (state.configDraft != null) state.configReasonDraft else "") }
    var preview by rememberSaveable { mutableStateOf(false) }
    var rollbackTarget by rememberSaveable { mutableStateOf<Int?>(null) }
    var compareTarget by rememberSaveable { mutableStateOf<Int?>(null) }
    var historyQuery by rememberSaveable { mutableStateOf("") }
    val changes = remember(serverConfig, config) { configChanges(serverConfig, config) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(adminScreenPadding()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdminPageTitle("конфигурация", "проверка, предпросмотр и безопасный откат")
        AdminCard {
            AdminSectionLabel("параметры клиентов")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminField(config.maintenanceSoon, { config = config.copy(maintenanceSoon = it); viewModel.saveConfigDraft(config, reason) }, "Скоро техработы", "Пусто — предупреждение отключено", 2)
                AdminField(config.maintenance, { config = config.copy(maintenance = it); viewModel.saveConfigDraft(config, reason) }, "Техработы сейчас", "Полная блокировка клиентов", 3)
                AdminField(config.latestVersion, { config = config.copy(latestVersion = it); viewModel.saveConfigDraft(config, reason) }, "Последняя версия", "Формат x.y.z")
                AdminField(config.minVersion, { config = config.copy(minVersion = it); viewModel.saveConfigDraft(config, reason) }, "Минимальная версия", "Старые клиенты будут заблокированы")
                AdminField(config.changelog, { config = config.copy(changelog = it); viewModel.saveConfigDraft(config, reason) }, "Что нового", "Описание изменений", 5)
                AdminField(config.downloadUrl, { config = config.copy(downloadUrl = it); viewModel.saveConfigDraft(config, reason) }, "Ссылка на APK", "HTTPS-адрес загрузки")
            }
        }
        AdminCard {
            AdminSectionLabel("применение")
            Spacer(Modifier.height(10.dp))
            AdminField(reason, { reason = it; viewModel.saveConfigDraft(config, reason) }, "Причина изменения", "Попадёт в ревизию и аудит", 2)
            Spacer(Modifier.height(12.dp))
            AdminPillButton("показать и сохранить ${changes.size} изм.", { preview = true }, Modifier.fillMaxWidth(), enabled = !state.savingConfig && changes.isNotEmpty())
            if (state.configDraft != null) AdminTextAction("очистить локальный черновик", { config = serverConfig; reason = ""; viewModel.clearConfigDraft() })
        }
        AdminCard {
            AdminSectionLabel("история конфигурации")
            Spacer(Modifier.height(7.dp))
            AdminTextField(historyQuery, { historyQuery = it }, label = "Фильтр истории", placeholder = "ревизия, поле или причина")
            Spacer(Modifier.height(7.dp))
            when (val history = state.configHistory) {
                is LoadState.Ready -> {
                    val filteredHistory = history.value.items.filter { revision ->
                        historyQuery.isBlank() || revision.revision.toString().contains(historyQuery, true) || revision.reason.contains(historyQuery, true) || revision.changedFields.any { it.contains(historyQuery, true) }
                    }
                    if (filteredHistory.isEmpty()) AdminText(if (history.value.items.isEmpty()) "история пуста" else "ревизии не найдены", color = AdminTheme.colors.textMuted) else filteredHistory.forEach { revision ->
                    AdminText("#${revision.revision} · ${displayDate(revision.changedAt)}", color = AdminTheme.colors.accent, fontWeight = FontWeight.Medium)
                    AdminText(revision.changedFields.joinToString().ifBlank { "поля не указаны" }, color = AdminTheme.colors.textSecondary, fontSize = 10.sp)
                    if (revision.reason.isNotBlank()) AdminText("причина: ${revision.reason}", color = AdminTheme.colors.textMuted, fontSize = 10.sp)
                    AdminTextAction("сравнить с текущей", { compareTarget = revision.revision; viewModel.loadConfigRevision(revision.revision) }, enabled = state.busyAction == null)
                    AdminTextAction("откатить к этой ревизии", { rollbackTarget = revision.revision }, enabled = state.busyAction == null)
                    AdminHairline(Modifier.padding(vertical = 6.dp))
                }
                }
                is LoadState.Error -> AdminText(history.message, color = AdminTheme.colors.error)
                else -> AdminSpinner("загрузка истории")
            }
            val historyPage = (state.configHistory as? LoadState.Ready)?.value
            if (historyPage?.nextCursor != null) AdminPillButton("загрузить ещё", viewModel::loadMoreConfigHistory, Modifier.fillMaxWidth(), enabled = state.busyAction == null)
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
    compareTarget?.let { revision ->
        AdminDialog(
            onDismissRequest = { compareTarget = null },
            title = "ревизия #$revision",
            confirmLabel = "закрыть",
            onConfirm = { compareTarget = null },
            dismissLabel = "",
        ) {
            when (val details = state.configRevisionDetails) {
                is LoadState.Ready -> {
                    val snapshot = details.value.snapshot
                    if (snapshot == null) AdminText("Сервер не вернул снимок этой ревизии", color = AdminTheme.colors.warning)
                    else {
                        val revisionChanges = configChanges(snapshot, serverConfig)
                        if (revisionChanges.isEmpty()) AdminText("Ревизия совпадает с текущей конфигурацией", color = AdminTheme.colors.accent)
                        else revisionChanges.forEach { change ->
                            AdminText(change.label, color = AdminTheme.colors.accent, fontWeight = FontWeight.Medium)
                            AdminText("#${revision}: ${change.before.ifBlank { "<пусто>" }}", color = AdminTheme.colors.textSecondary, fontSize = 10.sp, maxLines = 4)
                            AdminText("сейчас: ${change.after.ifBlank { "<пусто>" }}", color = AdminTheme.colors.textPrimary, fontSize = 10.sp, maxLines = 4)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
                is LoadState.Error -> AdminText(details.message, color = AdminTheme.colors.error)
                else -> AdminSpinner("загрузка ревизии")
            }
        }
    }
}

@Composable
fun AnnounceV3Screen(state: AdminUiState, viewModel: AdminViewModel) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(state.announcementDraft.title) }
    var body by rememberSaveable { mutableStateOf(state.announcementDraft.body) }
    var url by rememberSaveable { mutableStateOf(state.announcementDraft.url) }
    var sendConfirm by rememberSaveable { mutableStateOf(false) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var historyQuery by rememberSaveable { mutableStateOf("") }
    val recipients = (state.stats as? LoadState.Ready)?.value?.devices

    LazyColumn(Modifier.fillMaxSize(), contentPadding = adminScreenPadding(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { AdminPageTitle("пуши", "рассылка, история и повтор ошибок") }
        item { AdminCard {
            AdminSectionLabel("новая рассылка")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminField(title, { title = it; viewModel.saveAnnouncementDraft(Announcement(title, body, url)) }, "Заголовок", "Обязательно")
                AdminField(body, { body = it; viewModel.saveAnnouncementDraft(Announcement(title, body, url)) }, "Текст", "Обязательно", 4)
                AdminField(url, { url = it; viewModel.saveAnnouncementDraft(Announcement(title, body, url)) }, "Ссылка", "Необязательно")
            }
            Spacer(Modifier.height(12.dp))
            AdminPillButton("отправить на ${recipients ?: 0} устройств", { sendConfirm = true }, Modifier.fillMaxWidth(), enabled = recipients != null && title.isNotBlank() && body.isNotBlank() && !state.sendingAnnouncement)
            if (title.isNotBlank() || body.isNotBlank() || url.isNotBlank()) AdminTextAction("очистить черновик", { title = ""; body = ""; url = ""; viewModel.clearAnnouncementDraft() })
        } }
        item { AdminSectionLabel("история", Modifier.padding(start = 4.dp, top = 6.dp)) }
        item { AdminTextField(historyQuery, { historyQuery = it }, label = "Фильтр истории", placeholder = "заголовок, статус или ID") }
        when (val history = state.announcements) {
            is LoadState.Ready -> {
                val filteredHistory = history.value.items.filter { record -> historyQuery.isBlank() || record.id.contains(historyQuery, true) || record.title.contains(historyQuery, true) || record.status.contains(historyQuery, true) }
                if (filteredHistory.isEmpty()) item { AdminCard { AdminText(if (history.value.items.isEmpty()) "рассылок пока нет" else "рассылки не найдены", color = AdminTheme.colors.textMuted) } } else itemsIndexed(filteredHistory, key = { _, it -> it.id }) { itemIndex, record -> AnimatedListItem(itemIndex) {
                AdminCard(onClick = { detailsOpen = true; viewModel.loadAnnouncementDetails(record.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AdminText(record.title.ifBlank { "без заголовка" }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        AdminChip(record.status, selected = record.failed == 0, destructive = record.failed > 0)
                    }
                    AdminText(displayDate(record.createdAt), color = AdminTheme.colors.textMuted, fontSize = 10.sp)
                    AdminKeyValue("получателей", record.targeted.toString())
                    AdminKeyValue("доставлено / ошибок", "${record.delivered} / ${record.failed}")
                }
            } }
            }
            is LoadState.Error -> item { AdminCard { AdminText(history.message, color = AdminTheme.colors.error) } }
            else -> item { AdminSpinner("загрузка истории") }
        }
        val historyPage = (state.announcements as? LoadState.Ready)?.value
        if (historyPage?.nextCursor != null) item { AdminPillButton("загрузить ещё", viewModel::loadMoreAnnouncements, Modifier.fillMaxWidth(), enabled = state.busyAction == null) }
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
                AdminTextAction("копировать ID", { copyText(context, "ID рассылки", details.value.id); viewModel.showMessage("ID рассылки скопирован") })
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
                itemsIndexed(response.value.devices, key = { _, it -> it.login }) { itemIndex, group -> AnimatedListItem(itemIndex) {
                    AdminCard(onClick = { expanded = if (group.login in expanded) expanded - group.login else expanded + group.login }) {
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
                } }
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
    val context = LocalContext.current
    AdminText(device.name, color = AdminTheme.colors.accent, fontWeight = FontWeight.Bold)
    AdminKeyValue("ID", device.deviceId); AdminKeyValue("версия", device.appVersion.ifBlank { "неизвестно" })
    AdminKeyValue("пуши", if (device.pushEnabled) "включены" else "выключены", if (device.pushEnabled) AdminTheme.colors.accent else AdminTheme.colors.error)
    AdminKeyValue("регистрация", displayDate(device.registeredAt)); AdminKeyValue("активность", displayDate(device.lastSeenAt))
    AdminKeyValue("последний пуш", "${displayDate(device.lastPushAt)} ${device.lastPushStatus}".trim().ifBlank { "нет" })
    AdminKeyValue("отложено", device.heldCount.toString()); AdminKeyValue("токен", "••••••${device.tokenTail}")
    // AdminPillFlowRow вместо горизонтального скролла: скролл-контейнер обрезал
    // растянутую капсулу по своим границам. Перенос на вторую строку показывает
    // все кнопки целиком, а соседи по строке расталкиваются как в iOS 26.
    AdminPillFlowRow {
        AdminPillButton("копировать ID", { copyText(context, "ID устройства", device.deviceId) }, accent = false)
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

private enum class OperationsTab(val label: String) { Maintenance("техработы"), Releases("релизы"), Audit("аудит"), Errors("ошибки"), Security("защита"), Glass("стекло") }

@Composable
fun OperationsV3Screen(state: AdminUiState, viewModel: AdminViewModel) {
    var tab by rememberSaveable(state.operationsTab) {
        mutableStateOf(OperationsTab.entries.firstOrNull { it.name.equals(state.operationsTab, true) } ?: OperationsTab.Maintenance)
    }
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
            OperationsTab.Glass -> GlassPanelV3()
        }
    }
}

@Composable
private fun MaintenancePanelV3(state: AdminUiState, viewModel: AdminViewModel) {
    val context = LocalContext.current
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
                AdminField(starts, { starts = it }, "Начало", "ISO 8601")
                AdminTextAction("выбрать дату и время начала", { showDateTimePicker(context, starts) { starts = it } })
                AdminField(ends, { ends = it }, "Окончание", "ISO 8601")
                AdminTextAction("выбрать дату и время окончания", { showDateTimePicker(context, ends) { ends = it } })
                AdminField(message, { message = it }, "Сообщение", "Увидят пользователи", 3)
            }
            Spacer(Modifier.height(12.dp))
            AdminPillButton("сохранить расписание", { viewModel.scheduleMaintenance(starts, ends, message) }, Modifier.fillMaxWidth(), enabled = state.busyAction == null && starts.isNotBlank() && ends.isNotBlank() && message.isNotBlank())
        }
    }
    confirm?.let { action -> TypedConfirmationDialog(if (action == "stop") "остановить техработы" else "удалить расписание", "Блокировка и расписание будут сняты.", if (action == "stop") "ОСТАНОВИТЬ" else "УДАЛИТЬ", { confirm = null; if (action == "stop") viewModel.stopMaintenance() else viewModel.deleteMaintenanceSchedule() }, { confirm = null }) }
}

@Composable
private fun ReleasesPanelV3(state: AdminUiState, viewModel: AdminViewModel) {
    val context = LocalContext.current
    val draft = state.releaseDraft
    var version by rememberSaveable { mutableStateOf(draft.version) }
    var changelog by rememberSaveable { mutableStateOf(draft.changelog) }
    var url by rememberSaveable { mutableStateOf(draft.url) }
    var sha by rememberSaveable { mutableStateOf(draft.sha256) }
    var mandatory by rememberSaveable { mutableStateOf(draft.mandatory) }
    var rollout by rememberSaveable { mutableStateOf(draft.rollout.toString()) }
    var releaseQuery by rememberSaveable { mutableStateOf("") }
    var readinessTarget by remember { mutableStateOf<ReleaseRecord?>(null) }
    var publish by rememberSaveable { mutableStateOf<String?>(null) }
    fun saveDraft() {
        viewModel.saveReleaseDraft(
            ReleaseRecord(version.trim(), changelog, url.trim(), sha.trim(), mandatory, rollout.toIntOrNull()?.coerceIn(0, 100) ?: 100),
        )
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = adminPanelPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { AdminCard {
            AdminSectionLabel("добавить или обновить релиз")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminField(version, { version = it; saveDraft() }, "Версия", "x.y.z")
                AdminField(changelog, { changelog = it; saveDraft() }, "Что нового", "Необязательно", 4)
                AdminField(url, { url = it; saveDraft() }, "URL", "Ссылка на APK")
                AdminField(sha, { sha = it; saveDraft() }, "SHA-256", "64 шестнадцатеричных символа")
            }
            AdminCheckRow("Обязательное обновление", mandatory, { mandatory = !mandatory; saveDraft() })
            AdminField(rollout, { rollout = it.filter(Char::isDigit).take(3); saveDraft() }, "Процент раздачи", "0–100", keyboardType = KeyboardType.Number)
            Spacer(Modifier.height(12.dp))
            AdminPillButton("сохранить релиз", { viewModel.saveRelease(ReleaseRecord(version.trim(), changelog.trim(), url.trim(), sha.trim(), mandatory, rollout.toIntOrNull()?.coerceIn(0,100) ?: 100)) }, Modifier.fillMaxWidth(), enabled = state.busyAction == null && version.isNotBlank())
            if (version.isNotBlank() || changelog.isNotBlank() || url.isNotBlank() || sha.isNotBlank()) AdminTextAction("очистить черновик", {
                version = ""; changelog = ""; url = ""; sha = ""; mandatory = false; rollout = "100"; viewModel.clearReleaseDraft()
            })
        } }
        item { AdminTextField(releaseQuery, { releaseQuery = it }, label = "Фильтр релизов", placeholder = "версия или описание") }
        when (val releases = state.releases) {
            is LoadState.Ready -> {
                val filteredReleases = releases.value.items.filter { release -> releaseQuery.isBlank() || release.version.contains(releaseQuery, true) || release.changelog.contains(releaseQuery, true) }
                if (filteredReleases.isEmpty()) item { AdminCard { AdminText(if (releases.value.items.isEmpty()) "релизов пока нет" else "релизы не найдены", color = AdminTheme.colors.textMuted) } } else itemsIndexed(filteredReleases, key = { _, it -> it.version }) { itemIndex, release -> AnimatedListItem(itemIndex) { AdminCard {
                Row { AdminText(release.version, color = AdminTheme.colors.accent, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); if (release.mandatory) AdminChip("обязательный", destructive = true) }
                if (release.changelog.isNotBlank()) AdminText(release.changelog, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                AdminKeyValue("раздача", "${release.rollout}%"); AdminKeyValue("опубликован", displayDate(release.publishedAt).ifBlank { "нет" })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdminPillButton("копировать версию", { copyText(context, "Версия", release.version); viewModel.showMessage("Версия скопирована") }, accent = false)
                    if (release.sha256.isNotBlank()) AdminPillButton("копировать SHA-256", { copyText(context, "SHA-256", release.sha256); viewModel.showMessage("SHA-256 скопирован") }, accent = false)
                }
                AdminPillButton("проверить готовность", { readinessTarget = release; viewModel.checkReleaseReadiness(release) }, enabled = state.busyAction == null)
            } } } }
            is LoadState.Error -> item { AdminCard { AdminText(releases.message, color = AdminTheme.colors.error) } }
            else -> item { AdminSpinner("загрузка релизов") }
        }
        val releasesPage = (state.releases as? LoadState.Ready)?.value
        if (releasesPage?.nextCursor != null) item { AdminPillButton("загрузить ещё", viewModel::loadMoreReleases, Modifier.fillMaxWidth(), enabled = state.busyAction == null) }
    }
    readinessTarget?.let { release ->
        val readiness = state.releaseReadiness
        AdminDialog(
            onDismissRequest = { readinessTarget = null; viewModel.clearReleaseReadiness() },
            title = "готовность релиза ${release.version}",
            confirmLabel = "продолжить",
            onConfirm = { readinessTarget = null; publish = release.version },
            confirmEnabled = (readiness as? LoadState.Ready)?.value?.ready == true,
        ) {
            when (readiness) {
                is LoadState.Ready -> {
                    val value = readiness.value
                    AdminKeyValue("сервер", if (value.serverAvailable) "доступен" else "ошибка", if (value.serverAvailable) AdminTheme.colors.accent else AdminTheme.colors.error)
                    AdminKeyValue("Firebase", if (value.firebaseAvailable) "работает" else "ошибка", if (value.firebaseAvailable) AdminTheme.colors.accent else AdminTheme.colors.error)
                    AdminKeyValue("версия", release.version, if (value.versionValid) AdminTheme.colors.accent else AdminTheme.colors.error)
                    AdminKeyValue("обязательная", if (release.mandatory) "да" else "нет")
                    AdminKeyValue("минимальная версия станет", if (release.mandatory) release.version else "без изменения")
                    AdminKeyValue("раздача", "${release.rollout}%")
                    AdminKeyValue("APK", if (value.apkSpecified) "указан" else "не указан", if (value.apkSpecified) AdminTheme.colors.accent else AdminTheme.colors.error)
                    AdminKeyValue("SHA-256", if (value.apkVerification?.matches == true) "совпадает" else "не совпадает", if (value.apkVerification?.matches == true) AdminTheme.colors.accent else AdminTheme.colors.error)
                    AdminKeyValue("заблокированных клиентов", value.blockedClients.toString())
                    value.apkVerification?.let {
                        AdminText("ожидался: ${it.expectedSha256}", color = AdminTheme.colors.textMuted, fontSize = 9.sp, maxLines = 2)
                        AdminText("получен: ${it.actualSha256}", color = AdminTheme.colors.textMuted, fontSize = 9.sp, maxLines = 2)
                    }
                }
                is LoadState.Error -> AdminText(readiness.message, color = AdminTheme.colors.error)
                else -> AdminSpinner("загрузка APK и проверка SHA-256")
            }
        }
    }
    publish?.let { target -> TypedConfirmationDialog("опубликовать $target", "Проверка готовности пройдена. Будут атомарно обновлены версия, changelog, URL и при необходимости minVersion.", "ОПУБЛИКОВАТЬ", { publish = null; viewModel.publishRelease(target) }, { publish = null; viewModel.clearReleaseReadiness() }) }
}

@Composable
private fun AuditPanelV3(state: AdminUiState, viewModel: AdminViewModel) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var errorsOnly by rememberSaveable { mutableStateOf(false) }
    when (val audit = state.audit) {
        is LoadState.Ready -> LazyColumn(Modifier.fillMaxSize(), contentPadding = adminPanelPadding(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item {
                AdminCard {
                    AdminTextField(query, { query = it }, label = "Фильтр аудита", placeholder = "действие, IP или результат")
                    AdminCheckRow("Только ошибки", errorsOnly, { errorsOnly = !errorsOnly })
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AdminPillButton("обновить", viewModel::loadAudit, accent = false)
                        AdminPillButton("экспорт диагностики", { shareDiagnostics(context, buildDiagnosticReport(state)) }, accent = false)
                    }
                }
            }
            val filtered = audit.value.items.filter { record ->
                (!errorsOnly || !record.result.equals("ok", true)) &&
                    (query.isBlank() || listOf(record.id, record.action, record.ip, record.result, record.meta).any { it.contains(query, true) })
            }
            if (filtered.isEmpty()) item { AdminCard { AdminText(if (audit.value.items.isEmpty()) "журнал пуст" else "записи не найдены", color = AdminTheme.colors.textMuted) } }
            itemsIndexed(filtered, key = { _, it -> it.id }) { itemIndex, record -> AnimatedListItem(itemIndex) { AdminCard {
                Row { AdminText(record.action, color = AdminTheme.colors.accent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); AdminChip(record.result, selected = record.result.equals("ok", true), destructive = !record.result.equals("ok", true)) }
                AdminKeyValue("время", displayDate(record.at)); AdminKeyValue("IP", record.ip); if (record.meta.isNotBlank() && record.meta != "{}") AdminText(record.meta, color = AdminTheme.colors.textMuted, fontSize = 10.sp)
                AdminTextAction("копировать запись", { copyText(context, "Запись аудита", "${record.id}\n${record.at}\n${record.action}\n${record.result}\n${record.meta}"); viewModel.showMessage("Запись аудита скопирована") })
            } } }
            if (audit.value.nextCursor != null) item { AdminPillButton("загрузить ещё", viewModel::loadMoreAudit, Modifier.fillMaxWidth(), enabled = state.busyAction == null) }
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
                itemsIndexed(errors.value, key = { _, it -> it.id }) { itemIndex, error -> AnimatedListItem(itemIndex) { AdminCard { Row { AdminText(error.code, color = AdminTheme.colors.error, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); AdminText("×${error.count}") }; AdminText(error.message); AdminKeyValue("сервис", error.service); AdminKeyValue("последняя", displayDate(error.lastAt)) } } }
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

// ═══ Настройка стекла: все параметры backdrop + выбор обоев ═══

@Composable
private fun GlassPanelV3() {
    // Аккордеон: одна открытая группа за раз — короче экран и меньше
    // одновременно живого стекла (свёрнутые слайдеры не в композиции).
    var open by rememberSaveable { mutableStateOf("Обои") }
    fun toggle(name: String) { open = if (open == name) "" else name }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(adminPanelPadding()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdminExpandableSection("Обои", open == "Обои", { toggle("Обои") }) {
            Spacer(Modifier.height(4.dp))
            WallpaperPickerRow()
        }
        AdminExpandableSection("Панели", open == "Панели", { toggle("Панели") }) {
            GlassSlider("радиус углов", "%.0f dp", 8f..48f, { GlassSettingsStore.state.value.cardCornerRadius }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(cardCornerRadius = it)) }
            GlassSlider("размытие", "%.0f dp", 0f..32f, { GlassSettingsStore.state.value.cardBlur }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(cardBlur = it)) }
            GlassSlider("плотность заливки", "%.2f", 0f..0.8f, { GlassSettingsStore.state.value.cardSurfaceAlpha }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(cardSurfaceAlpha = it)) }
            GlassSlider("высота линзы", "%.0f dp", 0f..64f, { GlassSettingsStore.state.value.refractionHeight }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(refractionHeight = it)) }
            GlassSlider("сила линзы", "%.0f dp", 0f..96f, { GlassSettingsStore.state.value.refractionAmount }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(refractionAmount = it)) }
            GlassSlider("яркость", "%.2f", -0.5f..0.5f, { GlassSettingsStore.state.value.brightness }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(brightness = it)) }
            GlassSlider("контраст", "%.2f", 0.5f..1.5f, { GlassSettingsStore.state.value.contrast }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(contrast = it)) }
            GlassSlider("насыщенность", "%.2f", 0f..2f, { GlassSettingsStore.state.value.saturation }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(saturation = it)) }
            GlassSlider("блик: ширина", "%.1f dp", 0f..6f, { GlassSettingsStore.state.value.highlightWidth }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(highlightWidth = it)) }
            GlassSlider("блик: размытие", "%.1f dp", 0f..12f, { GlassSettingsStore.state.value.highlightBlur }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(highlightBlur = it)) }
            GlassSlider("блик: яркость", "%.2f", 0f..1f, { GlassSettingsStore.state.value.highlightAlpha }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(highlightAlpha = it)) }
            GlassSlider("оттенок стекла", "%.0f°", 0f..360f, { GlassSettingsStore.state.value.tintHue }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(tintHue = it)) }
            GlassSlider("цветность стекла", "%.2f", 0f..0.6f, { GlassSettingsStore.state.value.tintChroma }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(tintChroma = it)) }
            GlassToggleRow("Глубина линзы", { it.depthEffect }) { g, v -> g.copy(depthEffect = v) }
            GlassToggleRow("Хроматическая аберрация", { it.chromaticAberration }) { g, v -> g.copy(chromaticAberration = v) }
            GlassToggleRow("Вибранс", { it.vibrancy }) { g, v -> g.copy(vibrancy = v) }
        }
        AdminExpandableSection("Контролы", open == "Контролы", { toggle("Контролы") }) {
            GlassSlider("плотность цвета", "%.2f", 0.2f..1f, { GlassSettingsStore.state.value.tintAlpha }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(tintAlpha = it)) }
            GlassSlider("линза: высота", "%.0f dp", 0f..48f, { GlassSettingsStore.state.value.controlLensHeight }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(controlLensHeight = it)) }
            GlassSlider("линза: сила", "%.0f dp", 0f..96f, { GlassSettingsStore.state.value.controlLensAmount }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(controlLensAmount = it)) }
            GlassSlider("размытие", "%.0f dp", 0f..16f, { GlassSettingsStore.state.value.controlBlur }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(controlBlur = it)) }
            GlassSlider("внешняя тень", "%.2f", 0f..1f, { GlassSettingsStore.state.value.controlShadow }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(controlShadow = it)) }
            GlassSlider("тень: радиус", "%.0f dp", 0f..24f, { GlassSettingsStore.state.value.controlShadowRadius }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(controlShadowRadius = it)) }
            GlassSlider("внутренняя тень", "%.2f", 0f..1f, { GlassSettingsStore.state.value.controlInnerShadow }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(controlInnerShadow = it)) }
            GlassSlider("вн. тень: радиус", "%.0f dp", 0f..16f, { GlassSettingsStore.state.value.controlInnerRadius }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(controlInnerRadius = it)) }
            GlassSlider("окантовка", "%.2f", 0f..0.5f, { GlassSettingsStore.state.value.controlStroke }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(controlStroke = it)) }
        }
        AdminExpandableSection("Цвет акцента", open == "Цвет акцента", { toggle("Цвет акцента") }) {
            Spacer(Modifier.height(6.dp))
            AccentColorWheel(
                color = Color(GlassSettingsStore.state.value.accentColor),
                onColorChange = { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(accentColor = it.toArgb())) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AdminExpandableSection("Нижний бар", open == "Нижний бар", { toggle("Нижний бар") }) {
            GlassSlider("высота линзы", "%.0f dp", 0f..64f, { GlassSettingsStore.state.value.barLensHeight }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(barLensHeight = it)) }
            GlassSlider("сила линзы", "%.0f dp", 0f..96f, { GlassSettingsStore.state.value.barLensAmount }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(barLensAmount = it)) }
            GlassSlider("тонировка", "%.2f", 0f..0.5f, { GlassSettingsStore.state.value.barSurfaceAlpha }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(barSurfaceAlpha = it)) }
        }
        AdminExpandableSection("Кромки и фон", open == "Кромки и фон", { toggle("Кромки и фон") }) {
            GlassSlider("затемнение обоев", "%.2f", 0f..0.6f, { GlassSettingsStore.state.value.wallpaperScrim }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(wallpaperScrim = it)) }
            GlassSlider("блюр кромки сверху", "%.0f dp", 0f..24f, { GlassSettingsStore.state.value.edgeBlurTop }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(edgeBlurTop = it)) }
            GlassSlider("блюр кромки снизу", "%.0f dp", 0f..24f, { GlassSettingsStore.state.value.edgeBlurBottom }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(edgeBlurBottom = it)) }
            GlassSlider("высота фейда", "%.0f dp", 16f..64f, { GlassSettingsStore.state.value.edgeFadeHeight }) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(edgeFadeHeight = it)) }
        }
        AdminPillButton("сбросить настройки стекла", { GlassSettingsStore.reset() }, Modifier.fillMaxWidth(), accent = false)
    }
}

@Composable
private fun WallpaperPickerRow() {
    // Отдельный composable: подписка на настройки не рекомпозит всю панель.
    val selectedIndex = GlassSettingsStore.state.value.wallpaper
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AdminWallpapers.items.forEachIndexed { index, res ->
            val selected = selectedIndex == index
            Image(
                painterResource(res),
                contentDescription = "обои ${index + 1}",
                modifier = Modifier
                    .size(72.dp, 126.dp)
                    .clip(RoundedRectangle(16.dp))
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) AdminTheme.colors.accent else Color.White.copy(alpha = 0.2f),
                        RoundedRectangle(16.dp),
                    )
                    .liquidClickable(pressedScale = LiquidMotion.PressCard) { GlassSettingsStore.update(GlassSettingsStore.state.value.copy(wallpaper = index)) },
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun GlassToggleRow(label: String, get: (GlassSettings) -> Boolean, set: (GlassSettings, Boolean) -> GlassSettings) {
    val checked = get(GlassSettingsStore.state.value)
    AdminCheckRow(label, checked, { GlassSettingsStore.update(set(GlassSettingsStore.state.value, !checked)) })
}

@Composable
private fun GlassSlider(label: String, format: String, range: ClosedFloatingPointRange<Float>, value: () -> Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AdminText(label, color = AdminTheme.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            AdminText(format.format(value()), fontSize = 12.sp)
        }
        LiquidSlider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            visibilityThreshold = 0.001f,
            backdrop = LocalLiquidBackdrop.current,
        )
    }
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

private fun copyText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun shareDiagnostics(context: Context, report: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Диагностика GsGit Admin")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "Экспорт диагностики"))
}

private fun buildDiagnosticReport(state: AdminUiState): String = buildString {
    appendLine("GsGit Admin — диагностический отчёт")
    appendLine("Создан: ${Instant.now()}")
    appendLine("Секреты, ключи, токены и идентификаторы устройств не включены.")
    appendLine()
    (state.health as? LoadState.Ready)?.value?.let { health ->
        appendLine("Сервер: ${health.status}")
        appendLine("Версия сервера: ${health.serverVersion}")
        appendLine("Uptime: ${health.uptimeSec} сек")
        appendLine("База: ${health.database}")
        appendLine("Firebase: ${health.firebase}")
        appendLine("GitHub hooks: ${health.githubWebhooks}")
        appendLine("Очередь пушей: ${health.pushQueue}")
        appendLine("Время сервера: ${health.serverTime}")
    } ?: appendLine("Сервер: данные не загружены")
    appendLine()
    (state.stats as? LoadState.Ready)?.value?.let { stats ->
        appendLine("Аккаунты: ${stats.logins}")
        appendLine("Устройства: ${stats.devices}")
        appendLine("Тихий режим: ${stats.quietEnabled}")
        appendLine("Отложенные пуши: ${stats.heldPushes}")
        appendLine("Последняя версия: ${stats.latestVersion}")
        appendLine("Минимальная версия: ${stats.minVersion}")
        appendLine("Техработы: ${if (stats.maintenance.isBlank() || stats.maintenance.equals("off", true)) "выключены" else "включены"}")
    }
    appendLine()
    (state.metrics as? LoadState.Ready)?.value?.let { metrics ->
        appendLine("Метрики: ${metrics.period}")
        appendLine("Регистрации: ${metrics.registrations}")
        appendLine("Активные устройства: ${metrics.activeDevices}")
        appendLine("Пуши/ошибки: ${metrics.pushesSent}/${metrics.pushesFailed}")
        appendLine("GitHub события: ${metrics.githubEvents}")
        appendLine("Запросы: ${metrics.requests}")
        appendLine("4xx/5xx: ${metrics.responses4xx}/${metrics.responses5xx}")
    }
    val errors = (state.errors as? LoadState.Ready)?.value.orEmpty()
    appendLine()
    appendLine("Агрегированные ошибки: ${errors.size}")
    errors.forEach { error -> appendLine("- ${error.service}/${error.code}: ${error.count}, последнее ${error.lastAt}") }
    val audit = (state.audit as? LoadState.Ready)?.value?.items.orEmpty()
    appendLine()
    appendLine("Последние действия: ${audit.size}")
    audit.take(20).forEach { record -> appendLine("- ${record.at}: ${record.action} — ${record.result}") }
}

private fun showDateTimePicker(context: Context, initial: String, onSelected: (String) -> Unit) {
    val zone = ZoneId.systemDefault()
    val base = runCatching { Instant.parse(initial).atZone(zone) }.getOrElse { ZonedDateTime.now(zone).plusHours(1) }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    onSelected(ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, zone).toInstant().toString())
                },
                base.hour,
                base.minute,
                true,
            ).show()
        },
        base.year,
        base.monthValue - 1,
        base.dayOfMonth,
    ).show()
}
