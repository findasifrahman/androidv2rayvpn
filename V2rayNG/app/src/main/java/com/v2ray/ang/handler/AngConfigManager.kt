package com.v2ray.ang.handler

import android.content.Context
import android.graphics.Bitmap
import android.text.TextUtils
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.HY2
import com.v2ray.ang.R
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.fmt.CustomFmt
import com.v2ray.ang.fmt.Hysteria2Fmt
import com.v2ray.ang.fmt.ShadowsocksFmt
import com.v2ray.ang.fmt.SocksFmt
import com.v2ray.ang.fmt.TrojanFmt
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.fmt.VmessFmt
import com.v2ray.ang.fmt.WireguardFmt
import com.v2ray.ang.handler.V2rayConfigManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import java.net.URI
import java.util.UUID

object AngConfigManager {
    private const val TAG = "AngConfigManager"

    /**
     * parse config form qrcode or...
     */
    private fun parseConfig(server: String?, subid: String = ""): Int {
        try {
            if (server == null || server.isEmpty()) {
                Log.e(TAG, "Server string is null or empty in parseConfig")
                return -1
            }

            val protocol = when {
                server.startsWith(AppConfig.VMESS) -> "VMESS"
                server.startsWith(AppConfig.VLESS) -> "VLESS"
                server.startsWith(AppConfig.SHADOWSOCKS) -> "Shadowsocks"
                server.startsWith(AppConfig.SOCKS) -> "SOCKS"
                server.startsWith(AppConfig.TROJAN) -> "Trojan"
                server.startsWith(AppConfig.WIREGUARD) -> "WireGuard"
                server.startsWith(AppConfig.HYSTERIA2) -> "Hysteria2"
                server.startsWith(AppConfig.HY2) -> "Hysteria2"
                else -> "Unknown"
            }
            Log.d(TAG, "Attempting to parse $protocol configuration")

            var config: ProfileItem? = null
            try {
                config = when {
                    server.startsWith(AppConfig.VMESS) -> {
                        Log.d(TAG, "Parsing VMESS config")
                        VmessFmt.parse(server)
                    }
                    server.startsWith(AppConfig.VLESS) -> {
                        Log.d(TAG, "Parsing VLESS config")
                        VlessFmt.parse(server)
                    }
                    server.startsWith(AppConfig.TROJAN) -> {
                        Log.d(TAG, "Parsing Trojan config")
                        TrojanFmt.parse(server)
                    }
                    server.startsWith(AppConfig.SHADOWSOCKS) -> {
                        Log.d(TAG, "Parsing Shadowsocks config")
                        ShadowsocksFmt.parse(server)
                    }
                    server.startsWith(AppConfig.SOCKS) -> {
                        Log.d(TAG, "Parsing SOCKS config")
                        SocksFmt.parse(server)
                    }
                    server.startsWith(AppConfig.WIREGUARD) -> {
                        Log.d(TAG, "Parsing WireGuard config")
                        WireguardFmt.parse(server)
                    }
                    server.startsWith(AppConfig.HYSTERIA2) || server.startsWith(AppConfig.HY2) -> {
                        Log.d(TAG, "Parsing Hysteria2 config")
                        Hysteria2Fmt.parse(server)
                    }
                    else -> null
                }
                
                if (config != null) {
                    Log.d(TAG, "Successfully parsed $protocol config")
                } else {
                    Log.e(TAG, "Failed to parse $protocol config - returned null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse $protocol config: ${e.message}")
                e.printStackTrace()
            }

            if (config == null) {
                Log.e(TAG, "Failed to parse config - config is null")
                return -1
            }

            // Save config
            if (subid.isNotEmpty()) {
                config.subscriptionId = subid
            }

            val key = MmkvManager.encodeServerConfig("", config)
            if (key.isEmpty()) {
                Log.e(TAG, "Failed to save config - key is empty")
                return -1
            }
            Log.d(TAG, "Successfully saved config with key: $key")
            return 0

        } catch (e: Exception) {
            Log.e(TAG, "parseConfig failed with error: ${e.message}")
            e.printStackTrace()
            return -1
        }
    }

    /**
     * share config
     */
    private fun shareConfig(guid: String): String {
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return ""

            return config.configType.protocolScheme + when (config.configType) {
                EConfigType.VMESS -> VmessFmt.toUri(config)
                EConfigType.CUSTOM -> ""
                EConfigType.SHADOWSOCKS -> ShadowsocksFmt.toUri(config)
                EConfigType.SOCKS -> SocksFmt.toUri(config)
                EConfigType.HTTP -> ""
                EConfigType.VLESS -> VlessFmt.toUri(config)
                EConfigType.TROJAN -> TrojanFmt.toUri(config)
                EConfigType.WIREGUARD -> WireguardFmt.toUri(config)
                EConfigType.HYSTERIA2 -> Hysteria2Fmt.toUri(config)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * share2Clipboard
     */
    fun share2Clipboard(context: Context, guid: String): Int {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return -1
            }

            Utils.setClipboard(context, conf)

        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    /**
     * share2Clipboard
     */
    fun shareNonCustomConfigsToClipboard(context: Context, serverList: List<String>): Int {
        try {
            val sb = StringBuilder()
            for (guid in serverList) {
                val url = shareConfig(guid)
                if (TextUtils.isEmpty(url)) {
                    continue
                }
                sb.append(url)
                sb.appendLine()
            }
            if (sb.count() > 0) {
                Utils.setClipboard(context, sb.toString())
            }
            return sb.lines().count()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
    }

    /**
     * share2QRCode
     */
    fun share2QRCode(guid: String): Bitmap? {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return null
            }
            return QRCodeDecoder.createQRCode(conf)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * shareFullContent2Clipboard
     */
    fun shareFullContent2Clipboard(context: Context, guid: String?): Int {
        try {
            if (guid == null) return -1
            val result = V2rayConfigManager.getV2rayConfig(context, guid)
            if (result.status) {
                val config = MmkvManager.decodeServerConfig(guid)
                if (config?.configType == EConfigType.HYSTERIA2) {
                    val socksPort = Utils.findFreePort(listOf(100 + SettingsManager.getSocksPort(), 0))
                    val hy2Config = Hysteria2Fmt.toNativeConfig(config, socksPort)
                    Utils.setClipboard(context, JsonUtil.toJsonPretty(hy2Config) + "\n" + result.content)
                    return 0
                }
                Utils.setClipboard(context, result.content)
            } else {
                return -1
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    fun importBatchConfig(server: String?, subid: String = "", append: Boolean = true): Int {
        try {
            if (server.isNullOrEmpty()) {
                Log.e(TAG, "Server string is null or empty")
                return 0
            }

            Log.d(TAG, "Importing config, first 20 chars: ${server.take(20)}...")

            // Handle URL subscriptions
            if (server.startsWith("http")) {
                Log.d(TAG, "URL subscription detected")
                try {
                    var configText = Utils.getUrlContentWithCustomUserAgent(server)
                    if (configText.isNotEmpty()) {
                        Log.d(TAG, "Successfully fetched URL content")
                        
                        var decodedContent = try {
                            val decoded = Utils.decode(configText)
                            Log.d(TAG, "Successfully decoded base64 content")
                            decoded
                        } catch (e: Exception) {
                            Log.d(TAG, "Base64 decode failed, using raw content")
                            configText
                        }

                        if (decodedContent.isEmpty()) {
                            decodedContent = configText
                        }

                        Log.d(TAG, "Attempting to parse content")
                        var count = parseBatchConfig(decodedContent, subid, append)
                        if (count > 0) {
                            Log.d(TAG, "Successfully parsed $count configurations")
                            return count
                        }

                        val resId = parseConfig(decodedContent, subid)
                        if (resId == 0) {
                            Log.d(TAG, "Successfully parsed single configuration")
                            return 1
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch subscription: ${e.message}")
                    e.printStackTrace()
                }
                return 0
            }

            // Handle direct input
            Log.d(TAG, "Processing direct input")
            var decodedContent = if (server.startsWith("ss://") || 
                                   server.startsWith("vmess://") ||
                                   server.startsWith("vless://") ||
                                   server.startsWith("trojan://") ||
                                   server.startsWith("socks://") ||
                                   server.startsWith("wireguard://") ||
                                   server.startsWith("hy2://") ||
                                   server.startsWith("hysteria2://")) {
                Log.d(TAG, "Protocol URL detected, passing directly to parser")
                server
            } else {
                try {
                    val decoded = Utils.decode(server)
                    Log.d(TAG, "Successfully decoded base64 content, length: ${decoded.length}")
                    decoded
                } catch (e: Exception) {
                    Log.d(TAG, "Base64 decode failed, using raw content")
                    server
                }
            }

            if (decodedContent.isEmpty()) {
                Log.d(TAG, "Decoded content is empty, using original input")
                decodedContent = server
            }

            var count = parseBatchConfig(decodedContent, subid, append)
            if (count > 0) {
                Log.d(TAG, "Successfully parsed $count configurations from batch")
                return count
            }

            val resId = parseConfig(decodedContent, subid)
            if (resId == 0) {
                Log.d(TAG, "Successfully parsed single configuration")
                return 1
            }

            Log.d(TAG, "Attempting to parse as custom config")
            count = parseCustomConfigServer(decodedContent, subid)
            Log.d(TAG, if (count > 0) "Successfully parsed custom config" else "Failed to parse custom config")
            return count

        } catch (e: Exception) {
            Log.e(TAG, "Import failed with error: ${e.message}")
            e.printStackTrace()
            return 0
        }
    }

    fun parseBatchConfig(servers: String?, subid: String = "", append: Boolean = true): Int {
        try {
            if (servers.isNullOrEmpty()) {
                Log.d(TAG, "Server string is null or empty in parseBatchConfig")
                return 0
            }

            var count = 0
            val lines = servers.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filter { line ->
                    line.startsWith(AppConfig.VMESS) ||
                    line.startsWith(AppConfig.VLESS) ||
                    line.startsWith(AppConfig.SHADOWSOCKS) ||
                    line.startsWith(AppConfig.SOCKS) ||
                    line.startsWith(AppConfig.TROJAN) ||
                    line.startsWith(AppConfig.WIREGUARD) ||
                    line.startsWith(AppConfig.HYSTERIA2) ||
                    line.startsWith(AppConfig.HY2)
                }
                .distinct()

            Log.d(TAG, "Found ${lines.size} valid protocol URLs to parse")

            for (line in lines) {
                val protocol = when {
                    line.startsWith(AppConfig.VMESS) -> "VMESS"
                    line.startsWith(AppConfig.VLESS) -> "VLESS"
                    line.startsWith(AppConfig.SHADOWSOCKS) -> "Shadowsocks"
                    line.startsWith(AppConfig.SOCKS) -> "SOCKS"
                    line.startsWith(AppConfig.TROJAN) -> "Trojan"
                    line.startsWith(AppConfig.WIREGUARD) -> "WireGuard"
                    line.startsWith(AppConfig.HYSTERIA2) -> "Hysteria2"
                    line.startsWith(AppConfig.HY2) -> "Hysteria2"
                    else -> "Unknown"
                }
                Log.d(TAG, "Attempting to parse $protocol URL")
                
                val resId = parseConfig(line, subid)
                if (resId == 0) {
                    count++
                    Log.d(TAG, "Successfully parsed $protocol configuration")
                } else {
                    Log.e(TAG, "Failed to parse $protocol configuration")
                }
            }
            Log.d(TAG, "Successfully parsed $count configurations")
            return count
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(AppConfig.ANG_PACKAGE, "parseBatchConfig error: ${e.message}")
        }
        return 0
    }

    fun parseCustomConfigServer(server: String?, subid: String = ""): Int {
        if (server == null) {
            return 0
        }
        if (server.contains("inbounds")
            && server.contains("outbounds")
            && server.contains("routing")
        ) {
            try {
                val serverList: Array<Any> =
                    JsonUtil.fromJson(server, Array<Any>::class.java)

                if (serverList.isNotEmpty()) {
                    var count = 0
                    for (srv in serverList.reversed()) {
                        val config = CustomFmt.parse(JsonUtil.toJson(srv)) ?: continue
                        val key = MmkvManager.encodeServerConfig("", config)
                        MmkvManager.encodeServerRaw(key, JsonUtil.toJsonPretty(srv) ?: "")
                        count += 1
                    }
                    return count
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                // For compatibility
                val config = CustomFmt.parse(server) ?: return 0
                val key = MmkvManager.encodeServerConfig("", config)
                MmkvManager.encodeServerRaw(key, server)
                return 1
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return 0
        } else if (server.startsWith("[Interface]") && server.contains("[Peer]")) {
            try {
                val config = WireguardFmt.parseWireguardConfFile(server) ?: return R.string.toast_incorrect_protocol
                val key = MmkvManager.encodeServerConfig("", config)
                MmkvManager.encodeServerRaw(key, server)
                return 1
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return 0
        } else {
            return 0
        }
    }

    fun updateConfigViaSubAll(): Int {
        var count = 0
        try {
            MmkvManager.decodeSubscriptions().forEach {
                count += updateConfigViaSub(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
        return count
    }

    fun updateConfigViaSub(it: Pair<String, SubscriptionItem>): Int {
        try {
            if (TextUtils.isEmpty(it.first)
                || TextUtils.isEmpty(it.second.remarks)
                || TextUtils.isEmpty(it.second.url)
            ) {
                return 0
            }
            if (!it.second.enabled) {
                return 0
            }
            val url = Utils.idnToASCII(it.second.url)
            if (!Utils.isValidUrl(url)) {
                return 0
            }
            Log.d(AppConfig.ANG_PACKAGE, url)

            var configText = try {
                val httpPort = SettingsManager.getHttpPort()
                Utils.getUrlContentWithCustomUserAgent(url, 30000, httpPort)
            } catch (e: Exception) {
                Log.e(AppConfig.ANG_PACKAGE, "Update subscription: proxy not ready or other error, try……")
                //e.printStackTrace()
                ""
            }
            if (configText.isEmpty()) {
                configText = try {
                    Utils.getUrlContentWithCustomUserAgent(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }
            }
            if (configText.isEmpty()) {
                return 0
            }
            return parseConfigViaSub(configText, it.first, false)
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }

    private fun parseConfigViaSub(server: String?, subid: String, append: Boolean): Int {
        var count = parseBatchConfig(Utils.decode(server), subid, append)
        if (count <= 0) {
            count = parseBatchConfig(server, subid, append)
        }
        if (count <= 0) {
            count = parseCustomConfigServer(server, subid)
        }
        return count
    }

    private fun importUrlAsSubscription(url: String): Int {
        val subscriptions = MmkvManager.decodeSubscriptions()
        subscriptions.forEach {
            if (it.second.url == url) {
                return 0
            }
        }
        val uri = URI(Utils.fixIllegalUrl(url))
        val subItem = SubscriptionItem()
        subItem.remarks = uri.fragment ?: "import sub"
        subItem.url = url
        MmkvManager.encodeSubscription("", subItem)
        return 1
    }
}
