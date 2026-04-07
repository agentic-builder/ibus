package com.aightlabs.bustrack

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class BusTrackWidget : GlanceAppWidget() {

    companion object {
        // Glance 상태 키
        val KEY_IS_ACTIVE = booleanPreferencesKey("glance_is_active")
        val KEY_START_TIME = longPreferencesKey("glance_start_time")

        suspend fun updateAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val widget = BusTrackWidget()
            manager.getGlanceIds(BusTrackWidget::class.java).forEach { glanceId ->
                widget.update(context, glanceId)
            }
        }

        // Glance 상태를 변경하고 위젯 갱신
        suspend fun setActiveState(context: Context, glanceId: GlanceId, active: Boolean) {
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[KEY_IS_ACTIVE] = active
                if (active) {
                    prefs[KEY_START_TIME] = SystemClock.elapsedRealtime()
                }
            }
            BusTrackWidget().update(context, glanceId)
        }

        // updateAll 버전 (자동 종료 등에서 사용)
        suspend fun setActiveStateAll(context: Context, active: Boolean) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(BusTrackWidget::class.java).forEach { glanceId ->
                setActiveState(context, glanceId, active)
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
        // Glance 내장 상태에서 읽기 (SharedPreferences가 아님)
        val glancePrefs = currentState<Preferences>()
        val isActive = glancePrefs[KEY_IS_ACTIVE] ?: false
        val startTime = glancePrefs[KEY_START_TIME] ?: 0L

        // 차량 정보는 SharedPreferences에서 (등록 시 저장됨)
        val prefs = PrefsManager(context)
        val kindergartenName = prefs.getKindergartenName()
        val vehicleName = prefs.getVehicleName()

        val backgroundColor = if (isActive) {
            ColorProvider(Color(0xFF4CAF50))
        } else {
            ColorProvider(Color(0xFF9E9E9E))
        }

        val statusText = if (isActive && startTime > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - startTime
            val minutes = (elapsed / 60000).toInt()
            if (minutes < 60) {
                "운행 중 ${minutes}분"
            } else {
                val hours = minutes / 60
                val mins = minutes % 60
                "운행 중 ${hours}시간 ${mins}분"
            }
        } else if (isActive) {
            "운행 중"
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
                    text = if (isActive) "\u25CF" else "\u25CB",
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
        Log.d("ToggleAction", "Widget clicked!")
        val prefs = PrefsManager(context)

        if (!prefs.isRegistered()) {
            Log.d("ToggleAction", "Not registered")
            return
        }

        val wasActive = prefs.isActive()
        Log.d("ToggleAction", "Toggle: wasActive=$wasActive")

        // 1. SharedPreferences 변경 (LocationService용)
        if (wasActive) {
            prefs.setActive(false)
        } else {
            prefs.setActive(true)
            prefs.setStartTime(SystemClock.elapsedRealtime())
        }

        // 2. Glance 상태 변경 + 위젯 갱신 (핵심: Glance가 상태 변경을 감지하여 recompose)
        BusTrackWidget.setActiveState(context, glanceId, !wasActive)

        // 3. 서비스 시작/종료
        if (wasActive) {
            LocationService.stop(context)
        } else {
            LocationService.start(context)
        }

        Log.d("ToggleAction", "Toggle complete: active=${!wasActive}")
    }
}
