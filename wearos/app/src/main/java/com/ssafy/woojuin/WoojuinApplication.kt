package com.ssafy.woojuin

import android.app.Application
import com.ssafy.woojuin.data.AppServices
import com.ssafy.woojuin.data.fake.Repositories

class WoojuinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 실서버 조각(인증·API)과 데모 조각(fake)이 공존한다 — 실연결이 하나씩 대체한다
        AppServices.init(this)
        Repositories.init(this)
    }
}
