package com.aightlabs.bustrack

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BusTrackWidget : GlanceAppWidget() {

    companion object {
        suspend fun updateAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val widget = BusTrackWidget()
            manager.getGlanceIds(BusTrackWidget::class.java).forEach { glanceId ->
                widget.update(context, glanceId)
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        val prefs = PrefsManager(context)
        val isActive = prefs.isActive()
        val kindergartenName = prefs.getKindergartenName()
        val vehicleName = prefs.getVehicleName()

        val backgroundColor = if (isActive) {
            ColorProvider(Color(0xFF4CAF50))  // Green
        } else {
            ColorProvider(Color(0xFF9E9E9E))  // Grey
        }

        val statusText = if (isActive) {
            val startTime = prefs.getStartTime()
            val elapsed = SystemClock.elapsedRealtime() - startTime
            val minutes = (elapsed / 60000).toInt()
            if (minutes < 60) {
                "운행 중 ${minutes}분"
            } else {
                val hours = minutes / 60
                val mins = minutes % 60
                "운행 중 ${hours}시간 ${mins}분"
            }
        } else {
            "운행 시작"
        }

        val labelText = if (kindergartenName.isNotEmpty() && vehicleName.isNotEmpty()) {
            "${kindergartenName} ${vehicleName}"
        } else {
            "버스 위치추적"
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .clickable(actionRunCallback<ToggleAction>())
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isActive) "\u25CF" else "\u25CB",  // Filled/Empty circle
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 24.sp
                    )
                )
                Text(
                    text = statusText,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.padding(top = 4.dp)
                )
                Text(
                    text = labelText,
                    style = TextStyle(
                        color = ColorProvider(Color(0xDDFFFFFF)),
                        fontSize = 14.sp
                    ),
                    modifier = GlanceModifier.padding(top = 4.dp)
                )
            }
        }
    }
}

class ToggleAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = PrefsManager(context)

        if (!prefs.isRegistered()) {
            // Launch MainActivity for registration
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return
        }

        if (prefs.isActive()) {
            // Stop tracking
            LocationService.stop(context)
            prefs.setActive(false)
        } else {
            // Start tracking
            LocationService.start(context)
            prefs.setActive(true)
            prefs.setStartTime(SystemClock.elapsedRealtime())
        }

        // Update widget
        BusTrackWidget().update(context, glanceId)
    }
}
