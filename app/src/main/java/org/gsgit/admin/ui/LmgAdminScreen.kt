package org.gsgit.admin.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
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
import org.gsgit.admin.ui.kyant.components.AnimatedListItem
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
}

@Composable
fun LmgAdminScreen(state: AdminUiState, viewModel: AdminViewModel) {
    var userSearch by rememberSaveable { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<LmgPendingAction?>(null) }
    val context = LocalContext.current

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
        ) { index, user ->
            AnimatedListItem(index) {
                AdminCard(onClick = { viewModel.loadLmgUser(user.partnerUserId) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            AdminText(user.name.ifBlank { "Без имени" }, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            AdminText(user.partnerUserId, color = AdminTheme.colors.textMuted, fontSize = 10.sp)
                        }
                        if (user.isPremium) AdminChip("premium", selected = true)
                    }
                    Spacer(Modifier.height(6.dp))
                    AdminKeyValue("регионы", user.regions.joinToString().ifBlank { "—" })
                    AdminKeyValue("устройства", user.devices.toString())
                    AdminKeyValue("ручной грант", if (user.localGrant) "да" else "нет")
                    AdminKeyValue("активность", formatLmgEpoch(user.lastSeenAt))
                }
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
        itemsIndexed(devices, key = { index, device -> device.deviceId.ifBlank { "lmg-device-$index" } }) { index, device ->
            AnimatedListItem(index) {
                AdminCard {
                    AdminText(device.name.ifBlank { "Без имени" }, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    AdminText(device.deviceId, color = AdminTheme.colors.textMuted, fontSize = 10.sp)
                    Spacer(Modifier.height(5.dp))
                    AdminKeyValue("пользователь", device.partnerUserId)
                    AdminKeyValue("платформа", device.platform.ifBlank { "—" })
                    AdminKeyValue("версия", device.appVersion.ifBlank { "—" })
                    AdminKeyValue("первый вход", formatLmgEpoch(device.firstSeen))
                    AdminKeyValue("активность", formatLmgEpoch(device.lastSeen))
                }
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
        itemsIndexed(errors, key = { index, error -> "${error.code}-${error.lastAt}-$index" }) { index, error ->
            AnimatedListItem(index) {
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
            onConfirm = {
                when (action) {
                    is LmgPendingAction.Grant -> viewModel.setLmgPremium(action.user.partnerUserId, true, action.until)
                    is LmgPendingAction.Revoke -> viewModel.setLmgPremium(action.user.partnerUserId, false)
                    is LmgPendingAction.Delete -> viewModel.deleteLmgUser(action.user.partnerUserId)
                }
                pendingAction = null
            },
        )
    }
}

@Composable
private fun LmgUserDetailsDialog(
    user: LmgUser,
    onClose: () -> Unit,
    onGrantForever: () -> Unit,
    onGrantUntil: () -> Unit,
    onRevoke: () -> Unit,
    onDelete: () -> Unit,
) {
    AdminDialog(onClose, user.name.ifBlank { "Пользователь LMG" }, "закрыть", onClose, dismissLabel = "") {
        AdminText(user.partnerUserId, color = AdminTheme.colors.textMuted, fontSize = 10.sp)
        Spacer(Modifier.height(7.dp))
        AdminKeyValue("premium", if (user.isPremium) "да" else "нет")
        AdminKeyValue("план", user.plan.ifBlank { "—" })
        AdminKeyValue("истекает", if (user.premiumExpiresAt == 0L) "бессрочно / нет" else formatLmgEpoch(user.premiumExpiresAt))
        AdminKeyValue("ручной грант", if (user.localGrant) "да" else "нет")
        AdminKeyValue("регионы", user.regions.joinToString().ifBlank { "—" })
        AdminKeyValue("ICM-регионы", user.icmRegions.joinToString().ifBlank { "—" })
        AdminKeyValue("email", user.email ?: "—")
        AdminKeyValue("Telegram ID", user.tgId ?: "—")
        AdminKeyValue("активность", formatLmgEpoch(user.lastSeenAt))
        if (user.deviceMap.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            AdminSectionLabel("устройства: ${user.deviceMap.size}")
            user.deviceMap.values.take(4).forEach { device ->
                AdminText(
                    "${device.deviceId} · ${device.platform.ifBlank { "—" }} · ${device.appVersion.ifBlank { "—" }}",
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
            AdminPillButton("удалить", onDelete, destructive = true)
        }
    }
}

@Composable
private fun LmgActionDialog(
    action: LmgPendingAction,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var typed by rememberSaveable(action) { mutableStateOf("") }
    val deleting = action is LmgPendingAction.Delete
    val title = when (action) {
        is LmgPendingAction.Grant -> "Выдать ручной premium"
        is LmgPendingAction.Revoke -> "Снять ручной premium"
        is LmgPendingAction.Delete -> "Удалить пользователя"
    }
    val confirm = when (action) {
        is LmgPendingAction.Grant -> "выдать"
        is LmgPendingAction.Revoke -> "снять"
        is LmgPendingAction.Delete -> "удалить"
    }
    AdminDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmLabel = confirm,
        onConfirm = onConfirm,
        confirmEnabled = !busy && (!deleting || typed == "УДАЛИТЬ"),
        destructive = action !is LmgPendingAction.Grant,
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
