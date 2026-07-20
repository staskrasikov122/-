package org.gsgit.admin.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.gsgit.admin.ui.liquid.LiquidScene
import org.gsgit.admin.ui.theme.AdminTheme

@Composable
fun AdminApp(viewModel: AdminViewModel) {
    val state by viewModel.state.collectAsState()
    var toast by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onBackground()
                Lifecycle.Event.ON_START -> viewModel.onForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest {
            toast = it
            delay(3_800)
            toast = null
        }
    }

    LiquidScene {
        when (val auth = state.auth) {
            AuthState.Restoring -> CenterStatus("восстановление защищённой сессии")
            is AuthState.Locked -> AdminKeyScreen(auth.error, false, viewModel::unlock)
            AuthState.Checking -> AdminKeyScreen(null, true, viewModel::unlock)
            is AuthState.BiometricRequired -> BiometricScreen(
                error = auth.error,
                onSuccess = viewModel::completeBiometricAuthentication,
                onFailure = viewModel::biometricFailed,
                onUseKey = viewModel::useAdminKeyInstead,
            )
            AuthState.Unlocked -> AdminShell(state, viewModel)
        }
        toast?.let { message ->
            AdminToast(
                message,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 104.dp),
            )
        }
    }
}

@Composable
private fun AdminKeyScreen(error: String?, checking: Boolean, onUnlock: (String) -> Unit) {
    var key by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(22.dp), contentAlignment = Alignment.Center) {
        AdminCard(Modifier.widthIn(max = 460.dp), elevated = true) {
            AdminText("Админ GsGit", color = AdminTheme.colors.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            AdminText("панель управления заблокирована", color = AdminTheme.colors.textMuted, fontSize = 11.sp)
            Spacer(Modifier.height(16.dp))
            AdminTextField(
                value = key,
                onValueChange = { if (!checking) key = it },
                label = "X-Admin-Key",
                placeholder = "введите серверный ключ",
                enabled = !checking,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!checking && key.isNotBlank()) onUnlock(key) }),
            )
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(7.dp))
                AdminText("! $error", color = AdminTheme.colors.error, fontSize = 11.sp)
            }
            Spacer(Modifier.height(13.dp))
            if (checking) AdminSpinner("проверка ключа на сервере")
            else AdminPillButton("разблокировать", { onUnlock(key) }, Modifier.fillMaxWidth(), enabled = key.isNotBlank())
        }
    }
}

@Composable
private fun BiometricScreen(
    error: String?,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
    onUseKey: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var promptStarted by remember { mutableStateOf(false) }
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    val prompt = remember(activity) {
        activity?.let {
            BiometricPrompt(
                it,
                ContextCompat.getMainExecutor(it),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                    override fun onAuthenticationFailed() = onFailure("Отпечаток или лицо не распознаны")
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.ERROR_CANCELED && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                            onFailure(errString.toString())
                        }
                    }
                },
            )
        }
    }

    fun requestAuthentication() {
        val available = BiometricManager.from(context).canAuthenticate(authenticators)
        if (activity == null || prompt == null || available != BiometricManager.BIOMETRIC_SUCCESS) {
            onFailure(biometricError(context, available))
            return
        }
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Админ GsGit")
                .setSubtitle("Подтвердите доступ к серверной панели")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    LaunchedEffect(prompt) {
        if (!promptStarted) {
            promptStarted = true
            requestAuthentication()
        }
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(22.dp), contentAlignment = Alignment.Center) {
        AdminCard(Modifier.widthIn(max = 460.dp), elevated = true) {
            AdminText("Защищённый вход", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            AdminText("Сохранённый ключ остаётся зашифрованным. Подтвердите личность системным способом.", color = AdminTheme.colors.textSecondary, fontSize = 11.sp)
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(9.dp))
                AdminText("! $error", color = AdminTheme.colors.error, fontSize = 11.sp)
            }
            Spacer(Modifier.height(14.dp))
            AdminPillButton("отпечаток / лицо", ::requestAuthentication, Modifier.fillMaxWidth())
            Spacer(Modifier.height(7.dp))
            AdminTextAction("ввести X-Admin-Key", onUseKey, Modifier.fillMaxWidth())
        }
    }
}

private fun biometricError(context: Context, code: Int): String = when (code) {
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "На устройстве не настроен отпечаток, лицо или PIN"
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Биометрия на устройстве недоступна"
    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Модуль биометрии временно недоступен"
    else -> "Системная аутентификация недоступна ($code)"
}

private val adminNavigation = listOf(
    AdminNavItem(Section.Dashboard, "обзор", AdminIcons.Dashboard),
    AdminNavItem(Section.AppConfig, "конфиг", AdminIcons.Tune),
    AdminNavItem(Section.Announce, "пуши", AdminIcons.Notifications),
    AdminNavItem(Section.Devices, "устройства", AdminIcons.Devices),
    AdminNavItem(Section.Operations, "операции", AdminIcons.Settings),
)

@Composable
private fun AdminShell(state: AdminUiState, viewModel: AdminViewModel) {
    Column(Modifier.fillMaxSize()) {
        AdminTopBar(viewModel::refreshAll, viewModel::lock, state.backend, viewModel::selectBackend)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.backend == Backend.GlassFiles) {
                GlassFilesPlaceholderV3()
            } else {
                when (state.section) {
                    Section.Dashboard -> DashboardV3Screen(state, viewModel)
                    Section.AppConfig -> AppConfigV3Screen(state, viewModel)
                    Section.Announce -> AnnounceV3Screen(state, viewModel)
                    Section.Devices -> DevicesV3Screen(state, viewModel)
                    Section.Operations -> OperationsV3Screen(state, viewModel)
                }
            }
        }
        if (state.backend == Backend.GsGit) AdminBottomBar(adminNavigation, state.section, viewModel::selectSection)
    }
}

@Composable
private fun GlassFilesPlaceholderV3() {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        AdminCard(Modifier.widthIn(max = 520.dp)) {
            AdminText("GlassFiles", color = AdminTheme.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            AdminText("Контракт API пока не подключён. Вымышленные запросы не выполняются.", color = AdminTheme.colors.textMuted)
        }
    }
}

@Composable
private fun CenterStatus(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AdminSpinner(message) }
}
