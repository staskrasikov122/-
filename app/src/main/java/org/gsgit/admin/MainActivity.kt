package org.gsgit.admin

import android.app.AlertDialog
import android.graphics.RuntimeShader
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import org.gsgit.admin.data.GlassSettingsStore
import org.gsgit.admin.ui.AdminApp
import org.gsgit.admin.ui.AdminViewModel
import org.gsgit.admin.ui.theme.GsGitAdminTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!supportsAgsl()) {
            AlertDialog.Builder(this)
                .setTitle("Устройство не поддерживается")
                .setMessage("Для этой админки требуется Android 13 или новее и рабочая поддержка AGSL. Без неё жидкое стекло запустить невозможно.")
                .setCancelable(false)
                .setPositiveButton("Закрыть") { _, _ -> finishAffinity() }
                .show()
            return
        }
        GlassSettingsStore.init(this)
        // Тёмные обои: системные иконки светлые.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            GsGitAdminTheme {
                AdminApp(viewModel<AdminViewModel>())
            }
        }
    }

    private fun supportsAgsl(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return runCatching {
            RuntimeShader("half4 main(float2 position) { return half4(1.0); }")
        }.isSuccess
    }
}
