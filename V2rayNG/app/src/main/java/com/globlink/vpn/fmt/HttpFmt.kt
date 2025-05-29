package com.globlink.vpn.fmt

import com.globlink.vpn.dto.EConfigType
import com.globlink.vpn.dto.ProfileItem
import com.globlink.vpn.dto.V2rayConfig.OutboundBean
import com.globlink.vpn.extension.isNotNullEmpty
import kotlin.text.orEmpty

object HttpFmt : FmtBase() {
    fun toOutbound(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = OutboundBean.create(EConfigType.HTTP)

        outboundBean?.settings?.servers?.first()?.let { server ->
            server.address = profileItem.server.orEmpty()
            server.port = profileItem.serverPort.orEmpty().toInt()
            if (profileItem.username.isNotNullEmpty()) {
                val socksUsersBean = OutboundBean.OutSettingsBean.ServersBean.SocksUsersBean()
                socksUsersBean.user = profileItem.username.orEmpty()
                socksUsersBean.pass = profileItem.password.orEmpty()
                server.users = listOf(socksUsersBean)
            }
        }

        return outboundBean
    }


}
