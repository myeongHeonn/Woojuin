package com.ssafy.woojuin.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 인터넷 연결 감시 — 오프라인 게이트(앱 전면 차단 화면)의 근거.
 *
 * 워치는 오프라인에서 할 수 있는 일이 없다(로컬 큐 없음). 화면마다 실패 처리를
 * 흩뿌리는 대신, 연결이 없으면 앱 전체를 게이트 하나로 막고 돌아오면 하던 자리
 * 그대로 이어지게 한다. 블루투스로 폰을 경유하는 프록시 연결도 INTERNET 능력을
 * 가진 네트워크로 잡히므로 여기서 구분할 필요가 없다.
 */
class ConnectivityMonitor(context: Context) {

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        _online.value = manager.activeNetwork
            ?.let { manager.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _online.value = true
            }

            override fun onLost(network: Network) {
                _online.value = false
            }
        })
    }
}
