package com.aightlabs.bustrack

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class BusTrackApp : Application() {
    companion object {
        const val DB_URL = "https://ibus-bustrack-default-rtdb.asia-southeast1.firebasedatabase.app"
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseDatabase.getInstance(DB_URL).setPersistenceEnabled(true)
    }
}
