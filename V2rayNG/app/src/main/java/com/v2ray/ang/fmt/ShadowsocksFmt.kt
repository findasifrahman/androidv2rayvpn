package com.v2ray.ang.fmt

import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.NetworkType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.util.Utils
import java.net.URI
import java.net.URLDecoder
import java.util.Base64

object ShadowsocksFmt : FmtBase() {
    private const val TAG = "ShadowsocksFmt"

    fun parse(uri: String): ProfileItem? {
        Log.d(TAG, "Attempting to parse Shadowsocks URL, first 20 chars: ${uri.take(20)}...")
        
        try {
            if (!uri.lowercase().startsWith(AppConfig.SHADOWSOCKS)) {
                Log.e(TAG, "Not a valid Shadowsocks URL - wrong prefix")
                return null
            }

            // Try SIP002 format first
            val result = parseSip002(uri)
            if (result != null) {
                Log.d(TAG, "Successfully parsed SIP002 format")
                return result
            }
            Log.d(TAG, "SIP002 parsing failed, trying legacy format")

            // Try legacy format
            return parseLegacy(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}", e)
            return null
        }
    }

    private fun parseSip002(ssUri: String): ProfileItem? {
        Log.d(TAG, "Attempting SIP002 parse")
        try {
            // Remove the "ss://" prefix before parsing
            val uri = URI(ssUri)
            Log.d(TAG, "URI parsed successfully")

            val userInfoBase64 = uri.userInfo
            if (userInfoBase64.isNullOrEmpty()) {
                Log.e(TAG, "User info is null or empty")
                return null
            }
            Log.d(TAG, "User info base64 length: ${userInfoBase64.length}")

            // Add padding if needed
            val padding = when (userInfoBase64.length % 4) {
                2 -> "=="
                3 -> "="
                else -> ""
            }
            val toDecode = userInfoBase64 + padding

            val userInfo = try {
                Base64.getUrlDecoder().decode(toDecode).toString(Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode user info: ${e.message}")
                return null
            }
            Log.d(TAG, "Decoded user info length: ${userInfo.length}")

            val parts = userInfo.split(":")
            Log.d(TAG, "User info parts count: ${parts.size}")
            if (parts.size != 2) {
                Log.e(TAG, "Invalid user info format - expected 2 parts, got ${parts.size}")
                return null
            }

            val method = parts[0]
            val password = parts[1]
            Log.d(TAG, "Method: $method, Password length: ${password.length}")

            val host = uri.host
            if (host.isNullOrEmpty()) {
                Log.e(TAG, "Host is null or empty")
                return null
            }

            val port = uri.port
            if (port <= 0) {
                Log.e(TAG, "Invalid port: $port")
                return null
            }

            Log.d(TAG, "Host: $host, Port: $port")

            val profile = ProfileItem.create(EConfigType.SHADOWSOCKS)
            profile.remarks = host
            profile.server = host
            profile.serverPort = port.toString()
            profile.method = method
            profile.password = password

            // Handle plugin
            val queryParam = uri.query
            if (!queryParam.isNullOrEmpty()) {
                Log.d(TAG, "Processing query parameters")
                val queryPairs = queryParam.split("&")
                    .map { it.split("=") }
                    .filter { it.size == 2 }
                    .map { Pair(it[0], URLDecoder.decode(it[1], "UTF-8")) }
                    .toMap()

                queryPairs["plugin"]?.let { plugin ->
                    Log.d(TAG, "Found plugin: ${plugin.take(10)}...")
                    if (plugin.startsWith("obfs-local") || plugin.startsWith("simple-obfs")) {
                        val pluginParts = plugin.split(";")
                        pluginParts.forEach {
                            when {
                                it.startsWith("obfs=") -> {
                                    profile.network = NetworkType.TCP.type
                                    profile.headerType = "http"
                                    Log.d(TAG, "Set HTTP obfs")
                                }
                                it.startsWith("obfs-host=") -> {
                                    profile.host = it.substringAfter("obfs-host=")
                                    Log.d(TAG, "Set obfs host")
                                }
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "SIP002 parse successful")
            return profile
        } catch (e: Exception) {
            Log.e(TAG, "SIP002 parse error: ${e.message}", e)
            return null
        }
    }

    private fun parseLegacy(ssUri: String): ProfileItem? {
        Log.d(TAG, "Attempting legacy parse")
        try {
            val uri = ssUri.replace(AppConfig.SHADOWSOCKS, "")
            val decoded = Utils.decode(uri)
            if (decoded.isNullOrEmpty()) {
                Log.e(TAG, "Legacy decode failed - null or empty result")
                return null
            }
            Log.d(TAG, "Legacy decoded string length: ${decoded.length}")

            val match = "((.+?):(.+)@(.+?):(\\d+))".toRegex().find(decoded)
            if (match == null) {
                Log.e(TAG, "Legacy format regex match failed")
                return null
            }

            val profile = ProfileItem.create(EConfigType.SHADOWSOCKS)
            profile.remarks = match.groupValues[4]
            profile.server = match.groupValues[4]
            profile.serverPort = match.groupValues[5]
            profile.method = match.groupValues[2]
            profile.password = match.groupValues[3]

            Log.d(TAG, "Legacy parse successful")
            return profile
        } catch (e: Exception) {
            Log.e(TAG, "Legacy parse error: ${e.message}", e)
            return null
        }
    }

    fun toUri(config: ProfileItem): String {
        val pw = "${config.method}:${config.password}"
        return toUri(config, Utils.encode(pw), null)
    }

    fun toOutbound(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = OutboundBean.create(EConfigType.SHADOWSOCKS)

        outboundBean?.settings?.servers?.first()?.let { server ->
            server.address = profileItem.server.orEmpty()
            server.port = profileItem.serverPort.orEmpty().toInt()
            server.password = profileItem.password
            server.method = profileItem.method
        }

        val sni = outboundBean?.streamSettings?.populateTransportSettings(
            profileItem.network.orEmpty(),
            profileItem.headerType,
            profileItem.host,
            profileItem.path,
            profileItem.seed,
            profileItem.quicSecurity,
            profileItem.quicKey,
            profileItem.mode,
            profileItem.serviceName,
            profileItem.authority,
        )

        outboundBean?.streamSettings?.populateTlsSettings(
            profileItem.security.orEmpty(),
            profileItem.insecure == true,
            if (profileItem.sni.isNullOrEmpty()) sni else profileItem.sni,
            profileItem.fingerPrint,
            profileItem.alpn,
            profileItem.publicKey,
            profileItem.shortId,
            profileItem.spiderX,
        )

        return outboundBean
    }
}