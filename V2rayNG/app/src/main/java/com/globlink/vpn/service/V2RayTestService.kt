package com.globlink.vpn.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.globlink.vpn.AppConfig.MSG_MEASURE_CONFIG
import com.globlink.vpn.AppConfig.MSG_MEASURE_CONFIG_CANCEL
import com.globlink.vpn.AppConfig.MSG_MEASURE_CONFIG_SUCCESS
import com.globlink.vpn.dto.EConfigType
import com.globlink.vpn.extension.serializable
import com.globlink.vpn.handler.MmkvManager
import com.globlink.vpn.handler.V2rayConfigManager
import com.globlink.vpn.util.MessageUtil
import com.globlink.vpn.util.PluginUtil
import com.globlink.vpn.util.SpeedtestUtil
import com.globlink.vpn.util.Utils
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import libv2ray.Libv2ray
import java.util.concurrent.Executors

class V2RayTestService : Service() {
    private val realTestScope by lazy { CoroutineScope(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()).asCoroutineDispatcher()) }

    override fun onCreate() {
        super.onCreate()
        Seq.setContext(this)
        Libv2ray.initV2Env(Utils.userAssetPath(this), Utils.getDeviceIdForXUDPBaseKey())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getIntExtra("key", 0)) {
            MSG_MEASURE_CONFIG -> {
                val guid = intent.serializable<String>("content") ?: ""
                realTestScope.launch {
                    val result = startRealPing(guid)
                    MessageUtil.sendMsg2UI(this@V2RayTestService, MSG_MEASURE_CONFIG_SUCCESS, Pair(guid, result))
                }
            }

            MSG_MEASURE_CONFIG_CANCEL -> {
                realTestScope.coroutineContext[Job]?.cancelChildren()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startRealPing(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        if (config.configType == EConfigType.HYSTERIA2) {
            val delay = PluginUtil.realPingHy2(this, config)
            return delay
        } else {
            val config = V2rayConfigManager.getV2rayConfig(this, guid)
            if (!config.status) {
                return retFailure
            }
            return SpeedtestUtil.realPing(config.content)
        }
    }
}
