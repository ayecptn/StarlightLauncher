package kenneth.app.spotlightlauncher.appsearchmodule

import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.preference.PreferenceManager
import kenneth.app.spotlightlauncher.api.SearchModule
import kenneth.app.spotlightlauncher.api.SearchResult
import kenneth.app.spotlightlauncher.api.SpotlightLauncherApi
import kenneth.app.spotlightlauncher.api.utils.sortByRegex

private const val MODULE_NAME = "kenneth.app.spotlightlauncher.appsearchmodule"

typealias AppList = List<ResolveInfo>

class AppSearchModule : BroadcastReceiver(), SearchModule {
    override val name = MODULE_NAME
    override val displayName = "Apps"
    override val description = "Searches for apps installed on your device."

    override val adapter
        get() = searchResultAdapter

    private lateinit var searchResultAdapter: AppSearchResultAdapter
    private lateinit var mainContext: Context
    private val currentAppList = mutableListOf<ResolveInfo>()
    private val appLabels = mutableMapOf<String, String>()

    internal lateinit var preferences: AppSearchModulePreferences
        private set

    internal val context
        get() = mainContext

    override fun initialize(launcher: SpotlightLauncherApi) {
        mainContext = launcher.context
        searchResultAdapter = AppSearchResultAdapter(this, launcher)
        preferences = AppSearchModulePreferences(context)

        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        context.packageManager.queryIntentActivities(mainIntent, 0)
            .filter { (it.activityInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 1 }
            .forEach {
                val packageName = it.activityInfo.packageName
                val label =
                    it.activityInfo.applicationInfo.loadLabel(context.packageManager).toString()
                currentAppList.add(it)
                appLabels[packageName] = label
            }

        context.registerReceiver(this, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        })
    }

    override fun cleanup() {
        context.unregisterReceiver(this)
    }

    override fun search(keyword: String, keywordRegex: Regex): Result =
        Result(
            query = keyword,
            apps = currentAppList
                .filter { app -> appLabels[app.activityInfo.packageName]?.contains(keywordRegex) == true }
                .sortedWith { app1, app2 ->
                    val appName1 = appLabels[app1.activityInfo.packageName]!!
                    val appName2 = appLabels[app2.activityInfo.packageName]!!
                    return@sortedWith sortByRegex(appName1, appName2, keywordRegex)
                }
        )

    override fun onReceive(context: Context?, intent: Intent?) {
        val receivedPackageName = intent?.data?.schemeSpecificPart
        when (intent?.action) {
            Intent.ACTION_PACKAGE_REMOVED -> {
                currentAppList.removeAt(
                    currentAppList.indexOfFirst {
                        it.activityInfo.packageName == receivedPackageName
                    }
                )
            }
            Intent.ACTION_PACKAGE_ADDED -> {
                val packageLauncherIntent = Intent().apply {
                    `package` = receivedPackageName
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                context?.packageManager
                    ?.resolveActivity(packageLauncherIntent, 0)
                    ?.let { currentAppList.add(it) }
            }
        }
    }

    class Result(query: String, val apps: AppList) : SearchResult(query, MODULE_NAME)
}