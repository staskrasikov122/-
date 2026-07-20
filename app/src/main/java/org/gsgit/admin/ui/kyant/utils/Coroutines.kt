package org.gsgit.admin.ui.kyant.utils

import kotlinx.coroutines.android.awaitFrame as androidAwaitFrame

suspend fun awaitFrame() {
    androidAwaitFrame()
}
