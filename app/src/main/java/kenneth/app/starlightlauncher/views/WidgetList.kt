package kenneth.app.starlightlauncher.views

import android.animation.ObjectAnimator
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kenneth.app.starlightlauncher.animations.CardAnimation
import kenneth.app.starlightlauncher.api.StarlightLauncherApi
import kenneth.app.starlightlauncher.utils.activity
import kenneth.app.starlightlauncher.widgets.AddedWidget
import kenneth.app.starlightlauncher.widgets.WidgetPreferenceChanged
import kenneth.app.starlightlauncher.widgets.WidgetPreferenceManager
import java.util.*
import javax.inject.Inject

private const val ACTIVITY_RESULT_REGISTRY_KEY_REQUEST_BIND_WIDGET =
    "ACTIVITY_RESULT_REGISTRY_KEY_REQUEST_BIND_WIDGET"

private const val ACTIVITY_RESULT_REGISTRY_KEY_CONFIGURE_WIDGET =
    "ACTIVITY_RESULT_REGISTRY_KEY_CONFIGURE_WIDGET"

/**
 * Contains a list of widgets on the home screen.
 */
@AndroidEntryPoint
class WidgetList(context: Context, attrs: AttributeSet) : ReorderableList(context, attrs) {
    @Inject
    lateinit var widgetPreferenceManager: WidgetPreferenceManager

    @Inject
    lateinit var appWidgetHost: AppWidgetHost

    @Inject
    lateinit var launcher: StarlightLauncherApi

    private val animations: List<CardAnimation>

    private val appWidgetManager = AppWidgetManager.getInstance(context.applicationContext)

    private val showAnimator = ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
        duration = 200
    }

    private val hideAnimator = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f).apply {
        duration = 200
    }

    private val requestBindWidgetLauncher: ActivityResultLauncher<Intent>?
    private val configureWidgetActivityLauncher: ActivityResultLauncher<Intent>?

    private val addedWidgets: MutableList<AddedWidget>

    /**
     * Maps app widget IDs to the corresponding [AddedWidget.AndroidWidget]
     */
    private val appWidgetIdMap = mutableMapOf<Int, AddedWidget.AndroidWidget>()

    /**
     * Maps names of providers of app widgets to their index in this widget list.
     */
    private val widgetIndices = mutableMapOf<String, Int>()

    private val widgetListAdapter: WidgetListAdapter

    /**
     * The widget view holder currently in edit mode.
     */
    private var widgetViewHolderInEditMode: WidgetListAdapterItem? = null

    init {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
        )
        animations = generateAnimations()
        addedWidgets = widgetPreferenceManager.addedWidgets.toMutableList().onEach {
            if (it is AddedWidget.AndroidWidget) {
                appWidgetIdMap[it.appWidgetId] = it
            }
        }
        requestBindWidgetLauncher = activity?.activityResultRegistry?.register(
            ACTIVITY_RESULT_REGISTRY_KEY_REQUEST_BIND_WIDGET,
            ActivityResultContracts.StartActivityForResult(),
            ::onRequestBindWidgetResult
        )
        configureWidgetActivityLauncher = activity?.activityResultRegistry?.register(
            ACTIVITY_RESULT_REGISTRY_KEY_CONFIGURE_WIDGET,
            ActivityResultContracts.StartActivityForResult(),
            ::onConfigureWidgetResult
        )

        layoutManager = LinearLayoutManager(context)
        adapter = WidgetListAdapter(context, addedWidgets).also { widgetListAdapter = it }

        widgetPreferenceManager.addOnWidgetPreferenceChangedListener {
            when (it) {
                is WidgetPreferenceChanged.NewAndroidWidgetAdded -> {
                    onAndroidWidgetAdded(it.addedWidget, it.appWidgetProviderInfo)
                }

                else -> {
                }
            }
        }

        addOnOrderChangedListener(::onWidgetOrderChanged)
        addOnSelectionChangedListener(::onWidgetLongPressed)
    }

    /**
     * Shows all the widgets in this list.
     */
    fun showWidgets() {
        showAnimator.start()
    }

    /**
     * Hides all the widgets in this list. Note that this does not remove children in the layout.
     */
    fun hideWidgets() {
        hideAnimator.start()
    }

    private fun onRequestBindWidgetResult(result: ActivityResult?) {
        val data = result?.data ?: return
        val extras = data.extras
        val appWidgetId =
            extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: return
        val appWidgetProviderInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)

        when (result.resultCode) {
            Activity.RESULT_OK -> {
                appWidgetIdMap[appWidgetId]?.let {
                    configureWidget(it, appWidgetProviderInfo)
                }
            }
            Activity.RESULT_CANCELED -> {
                appWidgetIdMap.remove(appWidgetId)
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                widgetPreferenceManager.removeAndroidWidget(appWidgetProviderInfo)
            }
        }
    }

    private fun onConfigureWidgetResult(result: ActivityResult?) {
        val data = result?.data ?: return
        val extras = data.extras
        val appWidgetId =
            extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: return

        when (result.resultCode) {
            Activity.RESULT_OK -> {
                appWidgetIdMap[appWidgetId]?.let {
                    addAndroidWidget(it)
                }
            }
            Activity.RESULT_CANCELED -> {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
            }
        }
    }

    private fun bindWidget(
        widget: AddedWidget.AndroidWidget,
        appWidgetProviderInfo: AppWidgetProviderInfo,
    ) {
        val bindAllowed = appWidgetManager.bindAppWidgetIdIfAllowed(
            widget.appWidgetId,
            appWidgetProviderInfo.provider
        )

        if (bindAllowed) {
            configureWidget(widget, appWidgetProviderInfo)
        } else {
            requestBindWidgetPermission(appWidgetProviderInfo, widget.appWidgetId)
        }
    }

    private fun configureWidget(
        widget: AddedWidget.AndroidWidget,
        appWidgetProviderInfo: AppWidgetProviderInfo
    ) {
        if (appWidgetProviderInfo.configure != null) {
            Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).run {
                component = appWidgetProviderInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget.appWidgetId)

                configureWidgetActivityLauncher?.launch(this)
            }
        } else {
            addAndroidWidget(widget)
        }
    }

    private fun addAndroidWidget(widget: AddedWidget.AndroidWidget) {
        widgetListAdapter.addAndroidWidget(widget)
    }

    private fun requestBindWidgetPermission(
        appWidgetProviderInfo: AppWidgetProviderInfo,
        appWidgetId: Int
    ) {
        requestBindWidgetLauncher?.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, appWidgetProviderInfo.provider)
        })
    }

    private fun onAndroidWidgetAdded(
        widget: AddedWidget.AndroidWidget,
        appWidgetProviderInfo: AppWidgetProviderInfo
    ) {
        addedWidgets += widget
        val index = addedWidgets.size - 1
        widgetIndices[widget.provider.flattenToString()] = index
        appWidgetIdMap[widget.appWidgetId] = widget
        bindWidget(widget, appWidgetProviderInfo)
    }

    private fun onWidgetOrderChanged(fromPosition: Int, toPosition: Int) {
        widgetPreferenceManager.changeWidgetOrder(fromPosition, toPosition)
    }

    private fun onWidgetLongPressed(viewHolder: ViewHolder?) {
        if (viewHolder is WidgetListAdapterItem) {
            // unselect currently selected widget
            widgetViewHolderInEditMode?.binding?.isEditing = false
            // enable editing of the newly selected widget
            viewHolder.binding.isEditing = true
            widgetViewHolderInEditMode = viewHolder
        }
    }

    /**
     * Generates card animations for every widget.
     */
    private fun generateAnimations(): List<CardAnimation> =
        children.foldIndexed(mutableListOf()) { i, anims, child ->
            anims.apply { add(CardAnimation(child, i * 20L)) }
        }
}
