package com.focusstudy.poc

import android.app.Application

class FocusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionEngine.init(this)
    }
}
