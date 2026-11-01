package com.pandyabhavik.pulse.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import com.pandyabhavik.pulse.Graph
import com.pandyabhavik.pulse.data.HomeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Pulse's four home-screen widgets. Each one draws [Graph.snapshot] at its real size (SizeMode.Exact).
 *
 * Glance keeps a widget's session alive for a while after it draws, and an update() during that time only
 * recomposes with the data the session already holds. So each widget collects [Widgets.redraws] and
 * reloads the snapshot whenever it changes; [Widgets.refresh] bumps it and also starts sessions that ended.
 */
abstract class PulseWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext
        val initial = Widgets.load(app)
        provideContent {
            val state by remember { Widgets.redraws.map { Widgets.load(app) } }.collectAsState(initial)
            val s = state
            if (s == null || !s.onboarded) EmptyWidget(app, emptyTab) else Draw(app, s)
        }
    }

    /** The tab the "Open Pulse to start" card opens. */
    protected open val emptyTab: String = TAB_TODAY

    @Composable
    protected abstract fun Draw(context: Context, s: HomeState)
}

/** 1. Today 2x2: the calorie ring with the balance, and steps. */
class TodayWidget : PulseWidget() {
    @Composable
    override fun Draw(context: Context, s: HomeState) = TodayView(context, s)
}

/** 2. Journey 2x2: trend weight and the next milestone. */
class JourneyWidget : PulseWidget() {
    override val emptyTab: String = TAB_JOURNEY

    @Composable
    override fun Draw(context: Context, s: HomeState) = JourneyView(context, s)
}

/** 3. Next milestone 4x1: trend weight and a bar toward the next milestone. */
class MilestoneWidget : PulseWidget() {
    override val emptyTab: String = TAB_JOURNEY

    @Composable
    override fun Draw(context: Context, s: HomeState) = MilestoneView(context, s)
}

/** 4. Today, full 4x2: ring, steps with a bar, weight trend and next milestone. */
class TodayFullWidget : PulseWidget() {
    @Composable
    override fun Draw(context: Context, s: HomeState) = TodayFullView(context, s)
}

// ------------------------------------------------------------------------------------------ receivers

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class JourneyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = JourneyWidget()
}

class MilestoneWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MilestoneWidget()
}

class TodayFullWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayFullWidget()
}

object Widgets {
    private const val TAG = "Widgets"

    /** Bumped on every refresh, so widget sessions that are still alive reload the snapshot. */
    internal val redraws = MutableStateFlow(0L)

    internal suspend fun load(context: Context): HomeState? = try {
        Graph.snapshot(context)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't load the widget state", e)
        null
    }

    /** Redraws all four widgets. Errors are logged, never thrown. */
    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        redraws.value = redraws.value + 1
        val all = listOf(TodayWidget(), JourneyWidget(), MilestoneWidget(), TodayFullWidget())
        for (widget in all) {
            try {
                widget.updateAll(app)
            } catch (e: CancellationException) {
                // Only the caller being cancelled; not an error.
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't update ${widget.javaClass.simpleName}", e)
            }
        }
    }
}
