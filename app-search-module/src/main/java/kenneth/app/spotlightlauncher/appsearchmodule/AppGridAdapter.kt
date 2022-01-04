package kenneth.app.spotlightlauncher.appsearchmodule

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import kenneth.app.spotlightlauncher.api.SpotlightLauncherApi
import kenneth.app.spotlightlauncher.api.view.OptionMenu
import kenneth.app.spotlightlauncher.appsearchmodule.databinding.AppGridItemBinding
import kenneth.app.spotlightlauncher.appsearchmodule.databinding.AppOptionMenuBinding


internal class AppGridAdapter(
    private val module: AppSearchModule,
    internal val apps: MutableList<ResolveInfo>,
    private val launcher: SpotlightLauncherApi
) :
    RecyclerView.Adapter<AppGridItem>() {
    private lateinit var selectedApp: ResolveInfo

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppGridItem {
        val binding = AppGridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppGridItem(binding)
    }

    override fun onBindViewHolder(holder: AppGridItem, position: Int) {
        val app = apps[position]
        val appName = app.loadLabel(launcher.context.packageManager)

        with(holder.binding) {
            appIcon.apply {
                contentDescription =
                    context.getString(R.string.app_icon_content_description, appName)
                setImageBitmap(launcher.getIconPack().getIconOf(app))
            }

            if (module.preferences.shouldShowAppLabels) {
                appLabel.apply {
                    isVisible = true
                    text = appName
                }
            } else {
                appLabel.isVisible = false
            }

            root.setOnLongClickListener {
                selectedApp = app
                showAppOptionMenu()
            }
        }
    }

    override fun getItemCount(): Int = apps.size

    private fun showAppOptionMenu(): Boolean {
        launcher.showOptionMenu(::createAppOptionMenu)
        return true
    }

    private fun createAppOptionMenu(menu: OptionMenu) {
        AppOptionMenuBinding.inflate(LayoutInflater.from(launcher.context), menu).also {
            it.uninstallItem.setOnClickListener { uninstallApp() }
        }
    }

    private fun uninstallApp() {
        launcher.context.startActivity(
            Intent(
                Intent.ACTION_DELETE,
                Uri.fromParts("package", selectedApp.activityInfo.packageName, null)
            )
        )
    }
}

internal class AppGridItem(internal val binding: AppGridItemBinding) :
    RecyclerView.ViewHolder(binding.root)