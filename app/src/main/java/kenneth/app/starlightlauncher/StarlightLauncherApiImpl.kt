package kenneth.app.starlightlauncher

import android.content.Context
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kenneth.app.starlightlauncher.api.*
import kenneth.app.starlightlauncher.api.util.BlurHandler
import kenneth.app.starlightlauncher.api.view.OptionMenuBuilder
import kenneth.app.starlightlauncher.prefs.appearance.AppearancePreferenceManager
import kenneth.app.starlightlauncher.utils.BindingRegister
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class StarlightLauncherApiModule {
    @Binds
    abstract fun bindStarlightLauncherApi(
        impl: StarlightLauncherApiImpl
    ): StarlightLauncherApi
}

/**
 * An implementation of [StarlightLauncherApi] which extensions can use to provide
 * additional features for the launcher.
 */
@Singleton
internal class StarlightLauncherApiImpl @Inject constructor(
    private val appearancePreferenceManager: AppearancePreferenceManager,
    private val launcherEventChannel: LauncherEventChannel,
    override val blurHandler: BlurHandler
) : StarlightLauncherApi {
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    private lateinit var requestMultiplePermissionsLauncher: ActivityResultLauncher<Array<String>>

    private var requestPermissionResultCallback: PermissionRequestCallback? = null

    private var requestMultiplePermissionsResultCallback: MultiplePermissionRequestCallback? = null

    override lateinit var context: Context
        private set

    override fun showOptionMenu(builder: OptionMenuBuilder) {
        BindingRegister.activityMainBinding.optionMenu.show(builder)
    }

    override fun closeOptionMenu() {
        BindingRegister.activityMainBinding.optionMenu.hide()
    }

    override fun showOverlay(fromView: View, viewConstructor: (context: Context) -> View) {
        BindingRegister.activityMainBinding.overlay.showFrom(fromView, viewConstructor(context))
    }

    override fun closeOverlay() {
        BindingRegister.activityMainBinding.overlay.close()
    }

    override fun getIconPack(): IconPack = appearancePreferenceManager.iconPack

    override fun requestPermission(permission: String, callback: PermissionRequestCallback) {
        requestPermissionResultCallback = callback
        requestPermissionLauncher.launch(permission)
    }

    override fun requestPermissions(
        vararg permissions: String,
        callback: MultiplePermissionRequestCallback
    ) {
        requestMultiplePermissionsResultCallback = callback
        requestMultiplePermissionsLauncher.launch(arrayOf(*permissions))
    }

    override suspend fun addLauncherEventListener(listener: LauncherEventListener) =
        launcherEventChannel.subscribePublic(listener)

    internal fun setContext(context: Context) {
        this.context = context
        if (context is AppCompatActivity) {
            requestPermissionLauncher =
                context.registerForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { result ->
                    requestPermissionResultCallback?.let {
                        it(result)
                        requestPermissionResultCallback = null
                    }
                }

            requestMultiplePermissionsLauncher =
                context.registerForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { result ->
                    requestMultiplePermissionsResultCallback?.let {
                        it(result)
                        requestMultiplePermissionsResultCallback = null
                    }
                }
        }
    }
}