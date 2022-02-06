package kenneth.app.starlightlauncher

import android.view.View
import kenneth.app.starlightlauncher.R
import kenneth.app.starlightlauncher.utils.ContextMenuCallback
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the current state of the launcher.
 */
@Singleton
class AppState @Inject constructor() {
    enum class Theme {
        LIGHT, DARK
    }

    var screenWidth: Int = 0
    var screenHeight: Int = 0
        set(height) {
            field = height
            halfScreenHeight = height / 2
        }

    var halfScreenHeight: Int = 0
        private set

    /**
     * Determines if MainActivity is starting for the first time.
     */
    var isInitialStart = true

    var theme: Theme = Theme.DARK

    val themeStyleId: Int
        get() = when (theme) {
            Theme.LIGHT -> R.style.LightLauncherTheme
            Theme.DARK -> R.style.DarkLauncherTheme
        }

    /**
     * Current adaptive theme based on the current wallpaper.
     */
//    var adaptiveTheme = AdaptiveTheme()

    var statusBarHeight = 0

    /**
     * The [ContextMenuCallback] that [MainActivity] should call after
     * an item in the current floating menu is selected.
     *
     * If a [View] want to be notified of what item is selected after a floating context menu
     * is shown for it, implement [ContextMenuCallback], then set this field when
     * [View.onCreateContextMenu] is called.
     */
    var contextMenuCallbackForView: ContextMenuCallback? = null
}