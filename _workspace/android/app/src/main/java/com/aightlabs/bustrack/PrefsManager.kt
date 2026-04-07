package com.aightlabs.bustrack

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bustrack_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_VEHICLE_ID = "vehicleId"
        private const val KEY_KINDERGARTEN_ID = "kindergartenId"
        private const val KEY_VEHICLE_NAME = "vehicleName"
        private const val KEY_KINDERGARTEN_NAME = "kindergartenName"
        private const val KEY_ROUTE = "route"
        private const val KEY_IS_ACTIVE = "isActive"
        private const val KEY_START_TIME = "startTime"
        private const val KEY_REGISTERED = "registered"
    }

    fun saveRegistration(
        vehicleId: String,
        kindergartenId: String,
        vehicleName: String,
        kindergartenName: String,
        route: String
    ) {
        prefs.edit()
            .putString(KEY_VEHICLE_ID, vehicleId)
            .putString(KEY_KINDERGARTEN_ID, kindergartenId)
            .putString(KEY_VEHICLE_NAME, vehicleName)
            .putString(KEY_KINDERGARTEN_NAME, kindergartenName)
            .putString(KEY_ROUTE, route)
            .putBoolean(KEY_REGISTERED, true)
            .apply()
    }

    fun isRegistered(): Boolean = prefs.getBoolean(KEY_REGISTERED, false)

    fun getVehicleId(): String = prefs.getString(KEY_VEHICLE_ID, "") ?: ""
    fun getKindergartenId(): String = prefs.getString(KEY_KINDERGARTEN_ID, "") ?: ""
    fun getVehicleName(): String = prefs.getString(KEY_VEHICLE_NAME, "") ?: ""
    fun getKindergartenName(): String = prefs.getString(KEY_KINDERGARTEN_NAME, "") ?: ""
    fun getRoute(): String = prefs.getString(KEY_ROUTE, "") ?: ""

    fun setActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ACTIVE, active).apply()
    }

    fun isActive(): Boolean = prefs.getBoolean(KEY_IS_ACTIVE, false)

    fun setStartTime(time: Long) {
        prefs.edit().putLong(KEY_START_TIME, time).apply()
    }

    fun getStartTime(): Long = prefs.getLong(KEY_START_TIME, 0L)
}
