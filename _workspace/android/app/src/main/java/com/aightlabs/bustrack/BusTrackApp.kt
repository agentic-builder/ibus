package com.aightlabs.bustrack

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class BusTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}
