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
        // Светлые обои + скрим: системные иконки должны быть тёмными.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            GsGitAdminTheme {
                AdminApp(viewModel<AdminViewModel>())
            }
        }
    }
}
