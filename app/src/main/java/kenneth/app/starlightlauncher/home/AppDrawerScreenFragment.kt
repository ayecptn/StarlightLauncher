package kenneth.app.starlightlauncher.home

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.core.animation.addListener
import androidx.core.os.ConfigurationCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import kenneth.app.starlightlauncher.R
import kenneth.app.starlightlauncher.api.StarlightLauncherApi
import kenneth.app.starlightlauncher.api.view.OptionMenu
import kenneth.app.starlightlauncher.appsearchmodule.AppSearchModuleSettingsProvider
import kenneth.app.starlightlauncher.appsearchmodule.databinding.AppOptionMenuBinding
import kenneth.app.starlightlauncher.dataStore
import kenneth.app.starlightlauncher.databinding.AppListSectionGridItemBinding
import kenneth.app.starlightlauncher.databinding.FragmentAppDrawerScreenBinding
import kenneth.app.starlightlauncher.extension.ExtensionManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale
import javax.inject.Inject

private const val MAX_APP_SHORTCUTS_SHOWN = 5

@AndroidEntryPoint
internal class AppDrawerScreenFragment @Inject constructor(
    private val launcher: StarlightLauncherApi,
    private val launcherApps: LauncherApps,
    private val extensionManager: ExtensionManager,
) : Fragment() {
    private val viewModel: AppDrawerScreenViewModel by viewModels()

    private lateinit var appListAdapter: AppListAdapter
    private var binding: FragmentAppDrawerScreenBinding? = null

    private val sectionGridItemBindings = mutableListOf<AppListSectionGridItemBinding>()
    private var availableSections: Set<String> = emptySet()

    private val localeBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null || intent.action != Intent.ACTION_LOCALE_CHANGED) return
            ConfigurationCompat.getLocales(context.resources.configuration)[0]?.let {
                appListAdapter.locale = it
            }
            drawAppListSectionGrid()
        }
    }

    private val appListAdapterCallback = object : AppListAdapter.Callback {
        override fun onSectionClicked(availableSections: Set<String>) {
            showAppListSectionGrid(availableSections)
        }

        override fun onItemClicked(app: LauncherActivityInfo, sourceBounds: Rect) {
            launcherApps.startMainActivity(app.componentName, app.user, sourceBounds, null)
        }

        override fun onItemLongClicked(app: LauncherActivityInfo) {
            showAppOptionMenu(app)
        }
    }

    private val sectionGridBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            hideAppListSectionGrid()
            remove()
        }
    }

    /**
     * Called when the back button is pressed when the app list is showing only one section.
     */
    private val filteredAppListBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            appListAdapter.showAllApps()
            remove()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = context?.let { context ->
        FragmentAppDrawerScreenBinding.inflate(inflater).run {
            binding = this

            with(appList) {
                layoutManager = LinearLayoutManager(context).apply {
                    stackFromEnd = true
                }
                adapter = AppListAdapter(
                    apps = launcher.installedApps,
                    allSections = context.resources.getStringArray(R.array.app_list_item_type_labels),
                    iconPack = runBlocking { launcher.iconPack.first() },
                    locale = ConfigurationCompat.getLocales(context.resources.configuration)[0]
                        ?: Locale.getDefault(),
                    callback = appListAdapterCallback,
                ).also {
                    appListAdapter = it
                }
            }

            drawAppListSectionGrid()

            root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // LinearLayoutManager.stackFromEnd sets the initial position of the app list to the bottom
        // this moves the list back to the top
        binding?.appList?.scrollToPosition(0)

        viewModel.setLauncherInstance(launcher)

        viewModel.appList.observe(viewLifecycleOwner) {
            appListAdapter.update(it)
        }
        viewModel.iconPack.observe(viewLifecycleOwner) {
            appListAdapter.iconPack = it
        }
    }

    override fun onResume() {
        super.onResume()
        context?.registerReceiver(
            localeBroadcastReceiver,
            IntentFilter(Intent.ACTION_LOCALE_CHANGED)
        )
    }

    override fun onPause() {
        context?.unregisterReceiver(localeBroadcastReceiver)
        super.onPause()
    }

    private fun drawAppListSectionGrid() {
        val binding = binding ?: return

        if (binding.appListSectionGrid.childCount > 0) {
            binding.appListSectionGrid.removeAllViews()
            sectionGridItemBindings.clear()
        }

        context?.resources?.getStringArray(R.array.app_list_item_type_labels)
            ?.forEach { sectionLabel ->
                AppListSectionGridItemBinding.inflate(
                    LayoutInflater.from(context),
                    binding.appListSectionGrid,
                    true
                ).apply {
                    root.alpha = 0f
                    label.text = sectionLabel

                    label.setOnClickListener {
                        onlyShowSectionInAppList(sectionLabel)
                    }
                }.also { sectionGridItemBindings += it }
            }
    }

    private fun showAppListSectionGrid(availableSections: Set<String>) {
        this.availableSections = availableSections
        val binding = binding ?: return

        binding.appListSectionGrid.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
            isVisible = true
        }

        AnimatorSet().run {
            playTogether(
                ObjectAnimator.ofFloat(binding.appList, "scaleX", 0.9f),
                ObjectAnimator.ofFloat(binding.appList, "scaleY", 0.9f),
                ObjectAnimator.ofFloat(binding.appList, "alpha", 0f),
            )

            val gridItemAnimators =
                binding.appListSectionGrid.children.mapIndexed { i, gridItem ->
                    val gridItemBinding = sectionGridItemBindings[i]
                    val label = gridItemBinding.label.text.toString()
                    val isClickable = availableSections.contains(label)

                    AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(gridItem, "scaleX", 0f, 1f),
                            ObjectAnimator.ofFloat(gridItem, "scaleY", 0f, 1f),
                            ObjectAnimator.ofFloat(
                                gridItem,
                                "alpha",
                                0f,
                                if (isClickable) 1f else 0.5f
                            )
                        )
                        startDelay = 10L * i
                    }
                }.toList()

            playTogether(gridItemAnimators)

            addListener(
                onEnd = {
                    binding.appList.isVisible = false
                }
            )

            start()
        }

        activity?.onBackPressedDispatcher?.addCallback(sectionGridBackPressedCallback)
    }

    private fun hideAppListSectionGrid() {
        val binding = binding ?: return

        binding.appList.isVisible = true

        AnimatorSet().run {
            val gridItemAnimators = binding.appListSectionGrid.children.map { gridItem ->
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(gridItem, "scaleX", 0f),
                        ObjectAnimator.ofFloat(gridItem, "scaleY", 0f),
                        ObjectAnimator.ofFloat(gridItem, "alpha", 0f),
                    )
                }
            }.toList()

            playTogether(gridItemAnimators)
            playTogether(
                ObjectAnimator.ofFloat(binding.appList, "scaleX", 1f),
                ObjectAnimator.ofFloat(binding.appList, "scaleY", 1f),
                ObjectAnimator.ofFloat(binding.appList, "alpha", 1f),
                ObjectAnimator.ofFloat(binding.appListSectionGrid, "scaleX", 0.9f),
                ObjectAnimator.ofFloat(binding.appListSectionGrid, "scaleY", 0.9f),
                ObjectAnimator.ofFloat(binding.appListSectionGrid, "alpha", 0f),
            )

            addListener(
                onEnd = {
                    binding.appListSectionGrid.isVisible = false
                }
            )

            start()
        }
    }

    private fun onlyShowSectionInAppList(section: String) {
        if (availableSections.contains(section)) {
            sectionGridBackPressedCallback.remove()

            appListAdapter.onlyShowSection(section)
            hideAppListSectionGrid()

            activity?.onBackPressedDispatcher?.addCallback(filteredAppListBackPressedCallback)
        }
    }

    private fun showAppOptionMenu(app: LauncherActivityInfo) {
        val context = context ?: return
        val prefs =
            (extensionManager.lookupExtension("kenneth.app.starlightlauncher.appsearchmodule")!!.settingsProvider as AppSearchModuleSettingsProvider).preferences(
                context.dataStore
            )

        launcher.showOptionMenu { menu ->
            AppOptionMenuBinding.inflate(LayoutInflater.from(context), menu, true).apply {
                val appIcon = viewModel.iconPack.value?.getIconOf(app)
                    ?: context.packageManager.getUserBadgedIcon(
                        app.getIcon(0),
                        Process.myUserHandle(),
                    )

                appOptionMenuAppLabel.text = app.label
                appOptionMenuAppIcon.contentDescription =
                    getString(R.string.app_icon_content_description, app.label)
                Glide.with(context).load(appIcon).into(appOptionMenuAppIcon)

                pinAppItem.setOnClickListener {
                    lifecycleScope.launch {
                        if (isAppPinned == true) {
                            prefs.removePinnedApp(app)
                        } else {
                            prefs.addPinnedApp(app)
                        }
                    }
                }

                uninstallItem.setOnClickListener {
                    uninstallApp(app)
                    menu.hide()
                }

                lifecycleScope.launch {
                    prefs.isAppPinned(app).collectLatest {
                        isAppPinned = it
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                loadShortcutListIntoMenu(app, menu)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun loadShortcutListIntoMenu(app: LauncherActivityInfo, menu: OptionMenu) {
        val context = context ?: return
        try {
            launcherApps.getShortcuts(
                LauncherApps.ShortcutQuery().apply {
                    setActivity(app.componentName)
                    setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                    )
                },
                Process.myUserHandle()
            )
                ?.sortedWith { shortcut1, shortcut2 ->
                    when {
                        shortcut1.isDeclaredInManifest && shortcut2.isDynamic -> -1
                        shortcut1.isDynamic && shortcut2.isDeclaredInManifest -> 1
                        else -> shortcut1.rank - shortcut2.rank
                    }
                }
                ?.take(MAX_APP_SHORTCUTS_SHOWN)
                ?.forEach { shortcutInfo ->
                    val shortcutIcon = launcherApps.getShortcutIconDrawable(
                        shortcutInfo,
                        context.resources.displayMetrics.densityDpi
                    )

                    menu.addItem(
                        shortcutIcon,
                        shortcutInfo.longLabel?.toString() ?: shortcutInfo.shortLabel.toString(),
                        applyIconTint = false,
                    ) {
                        val sourceBounds = Rect().run {
                            it.iconView.getGlobalVisibleRect(this)
                            this
                        }
                        openShortcut(shortcutInfo, sourceBounds)
                    }
                }
        } catch (ex: SecurityException) {
            // Starlight launcher has to be the default launcher app in order to query
            // app shortcuts. Otherwise, SecurityException is thrown.
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun openShortcut(shortcutInfo: ShortcutInfo, sourceBound: Rect) {
        launcherApps.startShortcut(shortcutInfo, sourceBound, null)
    }

    private fun uninstallApp(app: LauncherActivityInfo) {
        context?.startActivity(
            Intent(
                Intent.ACTION_DELETE,
                Uri.fromParts("package", app.applicationInfo.packageName, null)
            )
        )
    }
}