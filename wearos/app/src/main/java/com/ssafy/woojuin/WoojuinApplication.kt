package com.ssafy.woojuin

import android.app.Application
import com.ssafy.woojuin.data.fake.Repositories

class WoojuinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Repositories.init(this)
    }
}
