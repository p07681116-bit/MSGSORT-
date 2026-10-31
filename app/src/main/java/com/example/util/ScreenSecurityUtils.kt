package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

object ScreenSecurityUtils {

    /**
     * Finds the host Activity from a given Context.
     */
    tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /**
     * Enables or disables Android native FLAG_SECURE on the host window.
     *
     * Protection features:
     * 1. Blocks normal Android screenshots on protected screens.
     * 2. Prevents screen recording and screen mirroring (Android renders black/blank frames).
     * 3. Hides sensitive content preview from the Android Recent Apps / Overview screen.
     * 4. Automatically restores the original security flags when leaving the protected view.
     *
     * Note: This enforces the highest native OS-level protection available on Android,
     * though it cannot protect against external physical camera capture.
     */
    fun setScreenSecurity(activity: Activity?, enable: Boolean) {
        if (activity == null) return
        try {
            if (enable) {
                activity.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        } catch (e: Exception) {
            android.util.Log.w("ScreenSecurityUtils", "Failed setting window FLAG_SECURE: ${e.message}")
        }
    }
}

/**
 * Disposable Composable that enables Android FLAG_SECURE immediately when sensitive content
 * is viewed and cleanly clears it when leaving the screen or resetting the content.
 */
@Composable
fun ScreenSecurityProtection(
    isProtected: Boolean = true
) {
    val context = LocalContext.current
    DisposableEffect(isProtected) {
        val activity = with(ScreenSecurityUtils) { context.findActivity() }
        if (isProtected && activity != null) {
            ScreenSecurityUtils.setScreenSecurity(activity, enable = true)
        }
        onDispose {
            if (activity != null) {
                ScreenSecurityUtils.setScreenSecurity(activity, enable = false)
            }
        }
    }
}
