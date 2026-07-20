package org.gsgit.admin

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import org.gsgit.admin.ui.AdminApp
import org.gsgit.admin.ui.AdminViewModel
import org.gsgit.admin.ui.theme.GsGitAdminTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
