package kenneth.app.spotlightlauncher.searching.display_adapters

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import kenneth.app.spotlightlauncher.MainActivity
import kenneth.app.spotlightlauncher.R
import kenneth.app.spotlightlauncher.searching.SearchType
import kenneth.app.spotlightlauncher.searching.Searcher
import kenneth.app.spotlightlauncher.searching.SmartSearcher
import javax.inject.Inject

/**
 * A module that provides all adapters needed to display search results.
 */
@Module
@InstallIn(ActivityComponent::class)
object AdaptersModule {
    @Provides
    fun provideAppsGridAdapter(@ActivityContext context: Context) =
        AppsGridDataAdapter.getInstance(context as MainActivity)

    @Provides
    fun provideFileListAdapter(@ActivityContext context: Context) =
        FileListDataAdapter.getInstance(context as MainActivity)

    @Provides
    fun provideSuggestedResultAdapter(@ActivityContext context: Context) =
        SuggestedResultAdapter(context as MainActivity)

    @Provides
    fun provideWebResultAdapter(@ActivityContext context: Context) =
        WebResultAdapter(context as MainActivity)
}

/**
 * ResultAdapter is used to adapt search results into views. It combines numerous RecyclerView
 * adapters into one class.
 */
class ResultAdapter @Inject constructor(
    @ActivityContext private val context: Context,
    private val webResultAdapter: WebResultAdapter,
    private val appsGridAdapter: AppsGridDataAdapter,
    private val fileListAdapter: FileListDataAdapter,
    private val suggestedResultAdapter: SuggestedResultAdapter
) {
    private lateinit var widgetListContainer: LinearLayout
    private lateinit var sectionCardList: LinearLayout

    private val activity = context as MainActivity

    fun displayResult(result: Searcher.Result, type: SearchType) {
        with(activity) {
            widgetListContainer = findViewById<LinearLayout>(R.id.widget_list_container).apply {
                visibility = View.GONE
            }
            sectionCardList = findViewById<LinearLayout>(R.id.section_card_list).apply {
                visibility = View.VISIBLE
            }
        }

        when (type) {
            SearchType.ALL -> {
                appsGridAdapter.displayData(result.apps)
                fileListAdapter.displayData(result.files)
                suggestedResultAdapter.displayResult(result.suggested)
            }
            SearchType.FILES -> {
                fileListAdapter.displayData(result.files)
            }
            SearchType.APPS -> {
                appsGridAdapter.displayData(result.apps)
            }
        }
    }

    fun hideResult() {
        appsGridAdapter.hideAppsGrid()
        fileListAdapter.hideFileList()
        webResultAdapter.hideWebResult()
        suggestedResultAdapter.hideSuggestedResult()

        sectionCardList = activity.findViewById<LinearLayout>(R.id.section_card_list).apply {
            isVisible = false
        }
    }

    fun displayWebResult(result: SmartSearcher.WebResult) {
        webResultAdapter.displayResult(result)
    }

    /**
     * Perform cleanup work on adapters
     */
    fun cleanup() {

    }
}