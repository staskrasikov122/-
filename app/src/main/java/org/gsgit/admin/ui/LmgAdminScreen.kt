package org.gsgit.admin.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gsgit.admin.data.LmgConfig
import org.gsgit.admin.data.LmgUser
import org.gsgit.admin.ui.theme.AdminTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

private sealed interface LmgPendingAction {
    val user: LmgUser

    data class Grant(override val user: LmgUser, val until: Long) : LmgPendingAction
    data class Revoke(override val user: LmgUser) : LmgPendingAction
    data class Delete(override val user: LmgUser) : LmgPendingAction
    data class Ban(override val user: LmgUser) : LmgPendingAction
    data class Unban(override val user: LmgUser) : LmgPendingAction
}

@Composable
fun LmgAdminScreen(state: AdminUiState, viewModel: AdminViewModel) {
    var userSearch by rememberSaveable { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<LmgPendingAction?>(null) }
    var clearClientErrorsConfirm by rememberSaveable { mutableStateOf(false) }
    var clearRateLimitsConfirm by rememberSaveable { mutableStateOf(false) }
    var rotateKeyConfirm by rememberSaveable { mutableStateOf(false) }
    var expandedClientError by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) viewModel.cancelLmgBackup() else viewModel.saveLmgBackup(uri)
    }

    LaunchedEffect(state.lmgBackup) {
        val backup = (state.lmgBackup as? LoadState.Ready)?.value ?: return@LaunchedEffect
        backupLauncher.launch(backup.fileName)
    }

    val loadedConfig = (state.lmgConfig as? LoadState.Ready)?.value
    var configDraft by remember { mutableStateOf<LmgConfig?>(null) }
    LaunchedEffect(loadedConfig) {
        if (loadedConfig != null) configDraft = loadedConfig
    }

    val loadedUsers = (state.lmgUsers as? LoadState.Ready)?.value
    val visibleUsers = loadedUsers?.items.orEmpty().filter { user ->
        val query = userSearch.trim()
        query.isEmpty() || user.name.contains(query, ignoreCase = true) ||
            user.partnerUserId.contains(query, ignoreCase = true)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = adminScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { AdminPageTitle("LMG", "управление сервером LiquidMusicGlass") }

        item {
            AdminCard {
                AdminSectionLabel("Сводный статус")
                Spacer(Modifier.height(8.dp))
                when (val status = state.lmgStatus) {
                    is LoadState.Ready -> {
                        AdminText(
                            if (status.value.broker.ok) "● БРОКЕР РАБОТАЕТ" else "● БРОКЕР НЕДОСТУПЕН",
                            color = if (status.value.broker.ok) AdminTheme.colors.accent else AdminTheme.colors.error,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(7.dp))
                        AdminKeyValue("версия", status.value.broker.version.ifBlank { "—" })
                        AdminKeyValue("аптайм", formatLmgUptime(status.value.broker.uptimeMs))
                        AdminKeyValue("пользователи", status.value.broker.users.toString())
                        status.value.checks.forEach { check ->
                            AdminKeyValue(
                                check.name.ifBlank { "check" },
                                "${if (check.ok) "ok" else "down"} · ${check.ms} мс",
                                if (check.ok) AdminTheme.colors.accent else AdminTheme.colors.error,
                            )
                        }
                        AdminKeyValue("время сервера", displayLmgIso(status.value.serverTime))
                    }
                    is LoadState.Error -> {
                        AdminText("! ${status.message}", color = AdminTheme.colors.error)
                        AdminTextAction("повторить", viewModel::loadLmgStatus)
                    }
                    else -> AdminSpinner("проверка компонентов LMG")
                }
            }
        }

        item {
            AdminCard {
                AdminSectionLabel("Активность")
                Spacer(Modifier.height(8.dp))
                when (val activity = state.lmgActivity) {
                    is LoadState.Ready -> {
                        AdminKeyValue("всего", activity.value.total.toString())
                        AdminKeyValue("DAU / WAU / MAU", "${activity.value.dau} / ${activity.value.wau} / ${activity.value.mau}")
                        AdminKeyValue("новые 24ч / 7д", "${activity.value.new24h} / ${activity.value.new7d}")
                        AdminKeyValue("premium", activity.value.premium.toString())
                        AdminKeyValue("заблокированы", activity.value.banned.toString(), if (activity.value.banned > 0) AdminTheme.colors.error else AdminTheme.colors.textPrimary)
                        if (activity.value.versions.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            AdminSectionLabel("версии")
                            activity.value.versions.entries.sortedByDescending { it.value }.forEach { (version, count) ->
                                AdminKeyValue(version, count.toString())
                            }
                        }
                        if (activity.value.countries.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            AdminSectionLabel("страны")
                            activity.value.countries.entries.sortedByDescending { it.value }.forEach { (cc, count) ->
                                AdminKeyValue("${countryFlag(cc)} $cc".trim(), count.toString())
                            }
                        }
                    }
                    is LoadState.Error -> {
                        AdminText("! ${activity.message}", color = AdminTheme.colors.error)
                        AdminTextAction("повторить", viewModel::loadLmgActivity)
                    }
                    else -> AdminSpinner("загрузка живой активности")
                }
            }
        }

        item {
            AdminCard {
                AdminSectionLabel("Health")
                Spacer(Modifier.height(8.dp))
                when (val health = state.lmgHealth) {
                    is LoadState.Ready -> {
                        val ok = health.value.icmUpstream.equals("ok", ignoreCase = true)
                        AdminText(
                            if (ok) "● ICM UPSTREAM: OK" else "● ICM UPSTREAM: ${health.value.icmUpstream.ifBlank { "DOWN" }.uppercase()}",
                            color = if (ok) AdminTheme.colors.accent else AdminTheme.colors.error,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(7.dp))
                        AdminKeyValue("версия сервера", health.value.serverVersion.ifBlank { "—" })
                        AdminKeyValue("время работы", formatLmgUptime(health.value.uptimeMs))
                        AdminKeyValue("пользователи", health.value.users.toString())
                        AdminKeyValue("партнёрский ключ", if (health.value.partnerKeySet) "настроен" else "не настроен")
                        AdminKeyValue("ICM origin", health.value.icmOrigin.ifBlank { "—" })
                        AdminKeyValue("Telegram HMAC", if (health.value.telegramHmac) "включён" else "выключен")
                        AdminKeyValue("время сервера", displayLmgIso(health.value.serverTime))
                    }
                    is LoadState.Error -> {
                        AdminText("! ${health.message}", color = AdminTheme.colors.error)
                        AdminTextAction("повторить", viewModel::loadLmgHealth)
                    }
                    else -> AdminSpinner("проверка LMG-сервера")
                }
            }
        }

        item {
            AdminCard {
                AdminSectionLabel("Metrics")
                Spacer(Modifier.height(8.dp))
                AdminPillFlowRow(Modifier.fillMaxWidth()) {
                    listOf("1h" to "1 час", "24h" to "24 часа", "7d" to "7 дней", "30d" to "30 дней").forEach { (period, label) ->
                        AdminChip(label, state.lmgMetricsPeriod == period) { viewModel.loadLmgMetrics(period) }
                    }
                }
                Spacer(Modifier.height(9.dp))
                when (val metrics = state.lmgMetrics) {
                    is LoadState.Ready -> {
                        AdminKeyValue("все запросы", metrics.value.req.toString())
                        AdminKeyValue("выдано сессий", metrics.value.sessionIssued.toString())
                        AdminKeyValue("ошибки авторизации", metrics.value.authFail.toString())
                        AdminKeyValue("ограничено rate limit", metrics.value.rateLimited.toString())
                        AdminKeyValue("запросы ICM", metrics.value.icm.toString())
                        AdminKeyValue("ICM 4xx", metrics.value.icm4xx.toString())
                        AdminKeyValue("ICM 5xx", metrics.value.icm5xx.toString())
                        AdminKeyValue("сбои ICM", metrics.value.icmFail.toString())
                        AdminKeyValue("логи клиентов", metrics.value.clientLog.toString())
                        AdminKeyValue("запросы забаненных", metrics.value.bannedHit.toString())
                    }
                    is LoadState.Error -> {
                        AdminText("! ${metrics.message}", color = AdminTheme.colors.error)
                        AdminTextAction("повторить", { viewModel.loadLmgMetrics(state.lmgMetricsPeriod) })
                    }
                    else -> AdminSpinner("загрузка метрик")
                }
            }
        }

        item {
            AdminCard {
                AdminSectionLabel("Latency ICM")
                Spacer(Modifier.height(8.dp))
                when (val latency = state.lmgLatency) {
                    is LoadState.Ready -> {
                        if (latency.value.isEmpty()) {
                            AdminText("замеров пока нет", color = AdminTheme.colors.textMuted)
                        } else {
                            latency.value.forEach { (name, stat) ->
                                AdminText(name, fontWeight = FontWeight.Bold)
                                AdminKeyValue("замеры", stat.count.toString())
                                AdminKeyValue("p50 / p95 / max", "${stat.p50} / ${stat.p95} / ${stat.max} мс")
                                Spacer(Modifier.height(5.dp))
                            }
                        }
                    }
                    is LoadState.Error -> {
                        AdminText("! ${latency.message}", color = AdminTheme.colors.error)
                        AdminTextAction("повторить", viewModel::loadLmgLatency)
                    }
                    else -> AdminSpinner("загрузка latency")
                }
            }
        }

        item {
            AdminCard {
                AdminSectionLabel("Users")
                Spacer(Modifier.height(8.dp))
                AdminTextField(
                    value = userSearch,
                    onValueChange = { userSearch = it },
                    label = "Поиск",
                    placeholder = "имя или partner_user_id",
                )
                Spacer(Modifier.height(8.dp))
                when (val users = state.lmgUsers) {
                    is LoadState.Ready -> AdminText(
                        "найдено: ${visibleUsers.size} · всего: ${users.value.count}",
                        color = AdminTheme.colors.textSecondary,
                        fontSize = 11.sp,
                    )
                    is LoadState.Error -> {
                        AdminText("! ${users.message}", color = AdminTheme.colors.error)
                        AdminTextAction("повторить", viewModel::loadLmgUsers)
                    }
                    else -> AdminSpinner("загрузка пользователей")
                }
            }
        }

        itemsIndexed(
            visibleUsers,
            key = { index, user -> user.partnerUserId.ifBlank { "lmg-user-$index" } },
        ) { _, user ->
            AdminCard(onClick = { viewModel.loadLmgUser(user.partnerUserId) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            AdminText(user.name.ifBlank { "Без имени" }, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            AdminText(user.partnerUserId, color = AdminTheme.colors.textMuted, fontSize = 10.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (user.banned) AdminChip("заблокирован", destructive = true)
                            if (user.isPremium) AdminChip("premium", selected = true)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    AdminKeyValue("регионы", user.regions.joinToString().ifBlank { "—" })
                    AdminKeyValue("гео", formatLmgGeo(user.cc, user.country, user.city))
                    AdminKeyValue("устройства", user.devices.toString())
                    AdminKeyValue("ручной грант", if (user.localGrant) "да" else "нет")
                    AdminKeyValue("активность", formatLmgEpoch(user.lastSeenAt))
            }
        }

        item {
            AdminCard {
                AdminSectionLabel("Devices")
                Spacer(Modifier.height(8.dp))
                when (val devices = state.lmgDevices) {
                    is LoadState.Ready -> AdminText(
                        "устройств: ${devices.value.count}",
                        color = AdminTheme.colors.textSecondary,
                        fontSize = 11.sp,
                    )
                    is LoadState.Error -> {
                        AdminText("! ${devices.message}", color = AdminTheme.colors.error)
                        AdminTextAction("повторить", viewModel::loadLmgDevices)
                    }
                    else -> AdminSpinner("загрузка устройств")
                }
            }
        }

        val devices = (state.lmgDevices as? LoadState.Ready)?.value?.items.orEmpty()
        itemsIndexed(devices, key = { index, device -> device.deviceId.ifBlank { "lmg-device-$index" } }) { _, device ->
            AdminCard {
                    AdminText(device.name.ifBlank { "Без имени" }, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    AdminText(device.deviceId, color = AdminTheme.colors.textMuted, fontSize = 10.sp)
                    Spacer(Modifier.height(5.dp))
                    AdminKeyValue("пользователь", device.partnerUserId)
                    AdminKeyValue("платформа", device.platform.ifBlank { "—" })
                    AdminKeyValue("версия", device.appVersion.ifBlank { "—" })
                    AdminKeyValue("IP", device.ip.ifBlank { "—" })
                    AdminKeyValue("гео", formatLmgGeo(device.cc, device.country, device.city))
                    AdminKeyValue("первый вход", formatLmgEpoch(device.firstSeen))
                    AdminKeyValue("активность", formatLmgEpoch(device.lastSeen))
            }
        }

        item {
            AdminCard {
                AdminSectionLabel("Config")
                Spacer(Modifier.height(8.dp))
                when (val config = state.lmgConfig) {
                    is LoadState.Ready -> {
                        val draft = configDraft ?: config.value
                        AdminTextField(draft.maintenance, { configDraft = draft.copy(maintenance = it) }, label = "Техработы", placeholder = "пусто — выключены", minLines = 2, maxLines = 5)
                        Spacer(Modifier.height(8.dp))
                        AdminTextField(draft.notice, { configDraft = draft.copy(notice = it) }, label = "Мягкий баннер", placeholder = "пусто — баннер выключен", minLines = 2, maxLines = 5)
                        Spacer(Modifier.height(8.dp))
                        AdminTextField(draft.minVersion, { configDraft = draft.copy(minVersion = it) }, label = "Минимальная версия", placeholder = "x.y.z")
                        Spacer(Modifier.height(8.dp))
                        AdminTextField(draft.latestVersion, { configDraft = draft.copy(latestVersion = it) }, label = "Последняя версия", placeholder = "x.y.z")
                        Spacer(Modifier.height(8.dp))
                        AdminTextField(draft.changelog, { configDraft = draft.copy(changelog = it) }, label = "Что нового", placeholder = "описание изменений", minLines = 3, maxLines = 8)
                        Spacer(Modifier.height(8.dp))
                        AdminTextField(draft.downloadUrl, { configDraft = draft.copy(downloadUrl = it) }, label = "Ссылка на APK", placeholder = "https://…")
                        Spacer(Modifier.height(6.dp))
                        AdminCheckRow("Wave включён", draft.waveEnabled, { configDraft = draft.copy(waveEnabled = !draft.waveEnabled) })
                        AdminCheckRow("Импорт включён", draft.importEnabled, { configDraft = draft.copy(importEnabled = !draft.importEnabled) })
                        Spacer(Modifier.height(8.dp))
                        AdminPillButton(
                            "сохранить конфигурацию",
                            { viewModel.saveLmgConfig(config.value, draft) },
                            Modifier.fillMaxWidth(),
                            enabled = state.busyAction == null && draft != config.value,
                        )
                    }
                    is LoadState.Error -> {
                        AdminText("! ${config.message}", color = AdminTheme.colors.error)
                        AdminTextAction("повторить", viewModel::loadLmgConfig)
                    }
                    else -> AdminSpinner("загрузка конфигурации")
                }
            }
        }

        item {
            AdminCard {
                AdminSectionLabel("Errors")
                Spacer(Modifier.height(8.dp))
                when (val errors = state.lmgErrors) {
                    is LoadState.Ready -> AdminText(
                        if (errors.value.isEmpty()) "ошибок нет" else "записей: ${errors.value.size}",
                        color = if (errors.value.isEmpty()) AdminTheme.colors.accent else AdminTheme.colors.textSecondary,
                        fontSize = 11.sp,
                    )
                    is LoadState.Error -> {
                        AdminText("! ${errors.message}", color = AdminTheme.colors.error)
                        AdminTextAction("повторить", viewModel::loadLmgErrors)
                    }
                    else -> AdminSpinner("загрузка ошибок")
                }
            }
        }

        val errors = (state.lmgErrors as? LoadState.Ready)?.value.orEmpty()
        itemsIndexed(errors, key = { index, error -> "${error.code}-${error.lastAt}-$index" }) { _, error ->
            AdminCard {
                    Row(Modifier.fillMaxWidth()) {
                        AdminText(error.code.ifBlank { "UNKNOWN" }, color = AdminTheme.colors.error, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        AdminText("×${error.count}", color = AdminTheme.colors.textSecondary)
                    }
                    Spacer(Modifier.height(5.dp))
                    AdminText(error.message.ifBlank { "Без описания" })
                    AdminKeyValue("первая", formatLmgEpoch(error.firstAt))
                    AdminKeyValue("последняя", formatLmgEpoch(error.lastAt))
            }
        }

        item {
            AdminCard {
                AdminSectionLabel("Крэши клиентов")
                Spacer(Modifier.height(8.dp))
                when (val errors = state.lmgClientErrors) {
                    is LoadState.Ready -> {
                        AdminText(
                            if (errors.value.isEmpty()) "крэшей нет" else "агрегированных записей: ${errors.value.size}",
                            color = if (errors.value.isEmpty()) AdminTheme.colors.accent else AdminTheme.colors.textSecondary,
                            fontSize = 11.sp,
                        )
                        if (errors.value.isNotEmpty()) {
                            Spacer(Modifier.height(7.dp))
                            AdminTextAction("очистить список", { clearClientErrorsConfirm = true }, destructive = true)
                        }
                    }
                    is LoadState.Error -> {
                        AdminText("! ${errors.message}", color = AdminTheme.colors.error)
                        AdminTextAction("повторить", viewModel::loadLmgClientErrors)
                    }
                    else -> AdminSpinner("загрузка клиентских крэшей")
                }
            }
        }

        val clientErrors = (state.lmgClientErrors as? LoadState.Ready)?.value.orEmpty()
        itemsIndexed(clientErrors, key = { index, error -> error.key.ifBlank { "client-error-$index" } }) { index, error ->
            val itemKey = error.key.ifBlank { "client-error-$index" }
            AdminExpandableSection(
                title = "${error.level.ifBlank { "error" }} · ${error.tag.ifBlank { error.key }} ×${error.count}",
                expanded = expandedClientError == itemKey,
                onToggle = { expandedClientError = if (expandedClientError == itemKey) null else itemKey },
            ) {
                AdminText(error.message.ifBlank { "Без описания" })
                Spacer(Modifier.height(6.dp))
                AdminKeyValue("версия", error.version.ifBlank { "—" })
                AdminKeyValue("пользователь", error.partnerUserId.ifBlank { "—" })
                AdminKeyValue("устройство", error.deviceId.ifBlank { "—" })
                AdminKeyValue("первая", formatLmgEpoch(error.firstAt))
                AdminKeyValue("последняя", formatLmgEpoch(error.lastAt))
                if (error.stack.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    AdminSectionLabel("stack trace")
                    AdminText(error.stack, color = AdminTheme.colors.textSecondary, fontSize = 10.sp)
                }
            }
        }

        item {
            AdminCard {
                AdminSectionLabel("Rate limits")
                Spacer(Modifier.height(8.dp))
                when (val limits = state.lmgRateLimits) {
                    is LoadState.Ready -> {
                        AdminText(
                            if (limits.value.isEmpty()) "горячих IP нет" else "горячих IP: ${limits.value.size}",
                            color = if (limits.value.isEmpty()) AdminTheme.colors.accent else AdminTheme.colors.textSecondary,
                            fontSize = 11.sp,
                        )
                        if (limits.value.isNotEmpty()) {
                            Spacer(Modifier.height(7.dp))
                            AdminTextAction("очистить все лимиты", { clearRateLimitsConfirm = true }, destructive = true)
                        }
                    }
                    is LoadState.Error -> {
                        AdminText("! ${limits.message}", color = AdminTheme.colors.error)
                        AdminTextAction("повторить", viewModel::loadLmgRateLimits)
                    }
                    else -> AdminSpinner("загрузка оперативных лимитов")
                }
            }
        }

        val rateLimits = (state.lmgRateLimits as? LoadState.Ready)?.value.orEmpty()
        itemsIndexed(rateLimits, key = { index, limit -> limit.ip.ifBlank { "rate-limit-$index" } }) { _, limit ->
            AdminCard {
                    AdminText(limit.ip.ifBlank { "неизвестный IP" }, fontWeight = FontWeight.Bold)
                    AdminKeyValue("запросов за минуту", limit.hitsLastMin.toString())
                    AdminTextAction("снять лимит", { viewModel.clearLmgRateLimits(limit.ip) }, destructive = true)
            }
        }

        item {
            AdminCard {
                AdminSectionLabel("Обслуживание")
                Spacer(Modifier.height(8.dp))
                AdminText("Резервная копия содержит пользователей, конфигурацию и ошибки. Ключ в неё не добавляется.", color = AdminTheme.colors.textSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                AdminPillButton(
                    if (state.lmgBackup is LoadState.Loading) "подготовка backup…" else "скачать backup",
                    viewModel::loadLmgBackup,
                    Modifier.fillMaxWidth(),
                    enabled = state.busyAction == null,
                    accent = false,
                )
                (state.lmgBackup as? LoadState.Error)?.let {
                    Spacer(Modifier.height(6.dp))
                    AdminText("! ${it.message}", color = AdminTheme.colors.error)
                }
                Spacer(Modifier.height(10.dp))
                AdminText("Перевыпуск мгновенно отключает старый admin-key.", color = AdminTheme.colors.error, fontSize = 11.sp)
                Spacer(Modifier.height(7.dp))
                AdminPillButton("перевыпустить admin-key", { rotateKeyConfirm = true }, Modifier.fillMaxWidth(), destructive = true, enabled = state.busyAction == null)
                (state.lmgRotatedKey as? LoadState.Error)?.let {
                    Spacer(Modifier.height(6.dp))
                    AdminText("! ${it.message}", color = AdminTheme.colors.error)
                }
            }
        }

        item {
            AdminCard {
                AdminSectionLabel("Session test")
                Spacer(Modifier.height(8.dp))
                when (val test = state.lmgSessionTest) {
                    is LoadState.Ready -> {
                        val ok = test.value.upstreamStatus in 200..299 && test.value.gotToken
                        AdminText(
                            if (ok) "● СВЯЗЬ С ICM РАБОТАЕТ" else "● ПРОВЕРКА ICM НЕ ПРОЙДЕНА",
                            color = if (ok) AdminTheme.colors.accent else AdminTheme.colors.error,
                            fontWeight = FontWeight.Bold,
                        )
                        AdminKeyValue("статус upstream", test.value.upstreamStatus.toString())
                        AdminKeyValue("токен получен", if (test.value.gotToken) "да" else "нет")
                        Spacer(Modifier.height(8.dp))
                    }
                    is LoadState.Error -> {
                        AdminText("! ${test.message}", color = AdminTheme.colors.error)
                        Spacer(Modifier.height(8.dp))
                    }
                    LoadState.Loading -> {
                        AdminSpinner("проверка связи с ICM")
                        Spacer(Modifier.height(8.dp))
                    }
                    else -> Unit
                }
                AdminPillButton(
                    "проверить связь с ICM",
                    viewModel::testLmgSession,
                    Modifier.fillMaxWidth(),
                    enabled = state.busyAction == null,
                )
            }
        }
    }

    when (val selected = state.lmgSelectedUser) {
        LoadState.Loading -> if (pendingAction == null) {
            AdminDialog(viewModel::closeLmgUser, "Пользователь LMG", "закрыть", viewModel::closeLmgUser, dismissLabel = "") {
                AdminSpinner("загрузка деталей")
            }
        }
        is LoadState.Error -> if (pendingAction == null) {
            AdminDialog(viewModel::closeLmgUser, "Пользователь LMG", "закрыть", viewModel::closeLmgUser, dismissLabel = "") {
                AdminText("! ${selected.message}", color = AdminTheme.colors.error)
            }
        }
        is LoadState.Ready -> if (pendingAction == null) {
            LmgUserDetailsDialog(
                selected.value,
                onClose = viewModel::closeLmgUser,
                onGrantForever = { pendingAction = LmgPendingAction.Grant(selected.value, 0) },
                onGrantUntil = {
                    showLmgDateTimePicker(context) { until -> pendingAction = LmgPendingAction.Grant(selected.value, until) }
                },
                onRevoke = { pendingAction = LmgPendingAction.Revoke(selected.value) },
                onBan = { pendingAction = LmgPendingAction.Ban(selected.value) },
                onUnban = { pendingAction = LmgPendingAction.Unban(selected.value) },
                onDelete = { pendingAction = LmgPendingAction.Delete(selected.value) },
            )
        }
        else -> Unit
    }

    pendingAction?.let { action ->
        LmgActionDialog(
            action = action,
            busy = state.busyAction != null,
            onDismiss = { pendingAction = null },
            onConfirm = { reason ->
                when (action) {
                    is LmgPendingAction.Grant -> viewModel.setLmgPremium(action.user.partnerUserId, true, action.until)
                    is LmgPendingAction.Revoke -> viewModel.setLmgPremium(action.user.partnerUserId, false)
                    is LmgPendingAction.Delete -> viewModel.deleteLmgUser(action.user.partnerUserId)
                    is LmgPendingAction.Ban -> viewModel.setLmgBanned(action.user.partnerUserId, true, reason)
                    is LmgPendingAction.Unban -> viewModel.setLmgBanned(action.user.partnerUserId, false)
                }
                pendingAction = null
            },
        )
    }

    if (clearClientErrorsConfirm) {
        AdminDialog(
            onDismissRequest = { clearClientErrorsConfirm = false },
            title = "Очистить клиентские крэши",
            confirmLabel = "очистить",
            onConfirm = {
                clearClientErrorsConfirm = false
                viewModel.clearLmgClientErrors()
            },
            confirmEnabled = state.busyAction == null,
            destructive = true,
        ) {
            AdminText("Агрегированный список клиентских ошибок будет очищен полностью.", color = AdminTheme.colors.textSecondary)
        }
    }

    if (clearRateLimitsConfirm) {
        AdminDialog(
            onDismissRequest = { clearRateLimitsConfirm = false },
            title = "Очистить все rate limits",
            confirmLabel = "очистить",
            onConfirm = {
                clearRateLimitsConfirm = false
                viewModel.clearLmgRateLimits()
            },
            confirmEnabled = state.busyAction == null,
            destructive = true,
        ) {
            AdminText("Оперативные лимиты всех IP будут сброшены. Новые запросы начнут заполнять их заново.", color = AdminTheme.colors.textSecondary)
        }
    }

    if (rotateKeyConfirm) {
        var confirmation by rememberSaveable { mutableStateOf("") }
        AdminDialog(
            onDismissRequest = { rotateKeyConfirm = false },
            title = "Перевыпустить admin-key",
            confirmLabel = "перевыпустить",
            onConfirm = {
                rotateKeyConfirm = false
                viewModel.rotateLmgKey()
            },
            confirmEnabled = confirmation == "ПЕРЕВЫПУСТИТЬ" && state.busyAction == null,
            destructive = true,
        ) {
            AdminText("Старый ключ умрёт мгновенно. Новый сначала будет сохранён в защищённом хранилище и затем показан один раз.", color = AdminTheme.colors.error)
            Spacer(Modifier.height(8.dp))
            AdminText("Введите ПЕРЕВЫПУСТИТЬ", color = AdminTheme.colors.textSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            AdminTextField(confirmation, { confirmation = it }, placeholder = "ПЕРЕВЫПУСТИТЬ")
        }
    }

    (state.lmgRotatedKey as? LoadState.Ready)?.value?.let { rotated ->
        AdminDialog(
            onDismissRequest = viewModel::clearRotatedLmgKey,
            title = "Новый admin-key",
            confirmLabel = "готово",
            onConfirm = viewModel::clearRotatedLmgKey,
            dismissLabel = "",
        ) {
            AdminText(
                if (rotated.securelySaved) "Ключ сохранён в Android Keystore. Сервер больше его не покажет."
                else "Ключ не удалось сохранить. Обязательно скопируйте его сейчас.",
                color = if (rotated.securelySaved) AdminTheme.colors.accent else AdminTheme.colors.error,
            )
            Spacer(Modifier.height(8.dp))
            AdminText(rotated.value, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            AdminPillButton("скопировать ключ", { copySensitiveText(context, rotated.value) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LmgUserDetailsDialog(
    user: LmgUser,
    onClose: () -> Unit,
    onGrantForever: () -> Unit,
    onGrantUntil: () -> Unit,
    onRevoke: () -> Unit,
    onBan: () -> Unit,
    onUnban: () -> Unit,
    onDelete: () -> Unit,
) {
    AdminDialog(onClose, user.name.ifBlank { "Пользователь LMG" }, "закрыть", onClose, dismissLabel = "") {
        AdminText(user.partnerUserId, color = AdminTheme.colors.textMuted, fontSize = 10.sp)
        Spacer(Modifier.height(7.dp))
        AdminKeyValue("premium", if (user.isPremium) "да" else "нет")
        AdminKeyValue("план", user.plan.ifBlank { "—" })
        AdminKeyValue("истекает", if (user.premiumExpiresAt == 0L) "бессрочно / нет" else formatLmgEpoch(user.premiumExpiresAt))
        AdminKeyValue("ручной грант", if (user.localGrant) "да" else "нет")
        AdminKeyValue("блокировка", if (user.banned) "заблокирован" else "нет", if (user.banned) AdminTheme.colors.error else AdminTheme.colors.textPrimary)
        AdminKeyValue("регионы", user.regions.joinToString().ifBlank { "—" })
        AdminKeyValue("гео", formatLmgGeo(user.cc, user.country, user.city))
        AdminKeyValue("ICM-регионы", user.icmRegions.joinToString().ifBlank { "—" })
        AdminKeyValue("email", user.email ?: "—")
        AdminKeyValue("Telegram ID", user.tgId ?: "—")
        AdminKeyValue("активность", formatLmgEpoch(user.lastSeenAt))
        if (user.deviceMap.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            AdminSectionLabel("устройства: ${user.deviceMap.size}")
            user.deviceMap.values.take(4).forEach { device ->
                AdminText(
                    "${device.deviceId} · ${device.platform.ifBlank { "—" }} · ${device.appVersion.ifBlank { "—" }} · ${formatLmgGeo(device.cc, device.country, device.city)}",
                    color = AdminTheme.colors.textSecondary,
                    fontSize = 10.sp,
                )
            }
            if (user.deviceMap.size > 4) AdminText("ещё: ${user.deviceMap.size - 4}", color = AdminTheme.colors.textMuted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(10.dp))
        AdminPillFlowRow(Modifier.fillMaxWidth()) {
            if (user.localGrant) {
                AdminPillButton("снять ручной premium", onRevoke, accent = false)
            } else {
                AdminPillButton("premium бессрочно", onGrantForever)
                AdminPillButton("premium до даты", onGrantUntil, accent = false)
            }
            if (user.banned) AdminPillButton("разблокировать", onUnban, accent = false)
            else AdminPillButton("заблокировать", onBan, destructive = true)
            AdminPillButton("удалить", onDelete, destructive = true)
        }
    }
}

@Composable
private fun LmgActionDialog(
    action: LmgPendingAction,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var typed by rememberSaveable(action) { mutableStateOf("") }
    var reason by rememberSaveable(action) { mutableStateOf("") }
    val deleting = action is LmgPendingAction.Delete
    val title = when (action) {
        is LmgPendingAction.Grant -> "Выдать ручной premium"
        is LmgPendingAction.Revoke -> "Снять ручной premium"
        is LmgPendingAction.Delete -> "Удалить пользователя"
        is LmgPendingAction.Ban -> "Заблокировать пользователя"
        is LmgPendingAction.Unban -> "Разблокировать пользователя"
    }
    val confirm = when (action) {
        is LmgPendingAction.Grant -> "выдать"
        is LmgPendingAction.Revoke -> "снять"
        is LmgPendingAction.Delete -> "удалить"
        is LmgPendingAction.Ban -> "заблокировать"
        is LmgPendingAction.Unban -> "разблокировать"
    }
    AdminDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmLabel = confirm,
        onConfirm = { onConfirm(reason) },
        confirmEnabled = !busy && (!deleting || typed == "УДАЛИТЬ"),
        destructive = action is LmgPendingAction.Delete || action is LmgPendingAction.Ban || action is LmgPendingAction.Revoke,
    ) {
        AdminText(action.user.name.ifBlank { action.user.partnerUserId }, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        when (action) {
            is LmgPendingAction.Grant -> AdminText(
                if (action.until == 0L) "Грант будет бессрочным." else "Грант действует до ${formatLmgEpoch(action.until)}.",
                color = AdminTheme.colors.textSecondary,
            )
            is LmgPendingAction.Revoke -> AdminText(
                "Снимается только ручной грант брокера. Премиум ICM, если он есть, останется активным.",
                color = AdminTheme.colors.textSecondary,
            )
            is LmgPendingAction.Delete -> {
                AdminText("Будут удалены пользователь и связанные серверные данные. Введите УДАЛИТЬ.", color = AdminTheme.colors.error)
                Spacer(Modifier.height(8.dp))
                AdminTextField(typed, { typed = it }, placeholder = "УДАЛИТЬ")
            }
            is LmgPendingAction.Ban -> {
                AdminText("Новые сессии, refresh и проверка подписки начнут возвращать 403. Живой токен дотлеет максимум час.", color = AdminTheme.colors.error)
                Spacer(Modifier.height(8.dp))
                AdminTextField(reason, { reason = it }, label = "Причина", placeholder = "необязательно", minLines = 2, maxLines = 5)
            }
            is LmgPendingAction.Unban -> AdminText("Доступ к выдаче и обновлению сессий будет восстановлен.", color = AdminTheme.colors.textSecondary)
        }
    }
}

private fun formatLmgUptime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    val days = totalSeconds / 86_400
    val hours = totalSeconds % 86_400 / 3_600
    val minutes = totalSeconds % 3_600 / 60
    return "${days}д ${hours}ч ${minutes}м"
}

private val lmgDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())

private fun formatLmgEpoch(milliseconds: Long): String =
    if (milliseconds <= 0) "нет" else runCatching { lmgDateFormatter.format(Instant.ofEpochMilli(milliseconds)) }.getOrDefault("—")

private fun displayLmgIso(raw: String): String = raw.replace("T", " ").replace(".000Z", " UTC").replace("Z", " UTC").ifBlank { "—" }

private fun countryFlag(raw: String): String {
    val cc = raw.trim().uppercase(Locale.ROOT)
    if (cc.length != 2 || cc.any { it !in 'A'..'Z' }) return ""
    return buildString {
        cc.forEach { letter -> appendCodePoint(0x1F1E6 + (letter - 'A')) }
    }
}

private fun formatLmgGeo(cc: String, country: String, city: String): String =
    listOf(countryFlag(cc), country.ifBlank { cc }, city).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" }

private fun copySensitiveText(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("admin-key", value)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}

private fun showLmgDateTimePicker(context: Context, onSelected: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 30) }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val selected = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onSelected(selected.timeInMillis)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true,
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    ).apply { datePicker.minDate = System.currentTimeMillis() }.show()
}
