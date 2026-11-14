package com.wayfare.ride

import android.app.Application
import com.wayfare.ride.data.SessionStore
import com.wayfare.ride.data.VhayaApiClient
import com.wayfare.ride.data.WayfareDatabase
import com.wayfare.ride.data.WayfareRepository

class WayfareApplication : Application() {
    val database: WayfareDatabase by lazy { WayfareDatabase.getInstance(this) }
    val sessionStore: SessionStore by lazy { SessionStore(this) }
    val apiClient: VhayaApiClient by lazy { VhayaApiClient() }
    val repository: WayfareRepository by lazy {
        WayfareRepository(
            context = applicationContext,
            db = database,
            sessionStore = sessionStore,
            apiClient = apiClient
        )
    }
}
