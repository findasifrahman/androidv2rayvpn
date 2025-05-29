package com.globlink.vpn.ui

import android.content.Intent
import android.graphics.Color
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.globlink.vpn.AngApplication.Companion.application
import com.globlink.vpn.AppConfig
import com.globlink.vpn.R
import com.globlink.vpn.databinding.ItemQrcodeBinding
import com.globlink.vpn.databinding.ItemRecyclerFooterBinding
import com.globlink.vpn.databinding.ItemRecyclerMainBinding
import com.globlink.vpn.dto.EConfigType
import com.globlink.vpn.dto.ServersCache
import com.globlink.vpn.extension.toast
import com.globlink.vpn.handler.AngConfigManager
import com.globlink.vpn.handler.MmkvManager
import com.globlink.vpn.helper.ItemTouchHelperAdapter
import com.globlink.vpn.helper.ItemTouchHelperViewHolder
import com.globlink.vpn.service.V2RayServiceManager
import com.globlink.vpn.util.Utils
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import java.util.concurrent.TimeUnit

class MainRecyclerAdapter(
    private val activity: MainActivity,
    private val serversCache: List<ServersCache>,
    private val onServerSelected: (String) -> Unit,
    private val onServerRemoved: (String) -> Unit,
    private val getPosition: (String) -> Int
) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>(), ItemTouchHelperAdapter {
    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
    }

    private val share_method: Array<out String> by lazy {
        activity.resources.getStringArray(R.array.share_method)
    }
    var isRunning = false

    override fun getItemCount() = serversCache.size + 1

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val guid = serversCache[position].guid
            val profile = serversCache[position].profile

            val aff = MmkvManager.decodeServerAffiliationInfo(guid)

            holder.itemMainBinding.tvName.text = "GlobLink.Server." + (position+1).toString()
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            holder.itemMainBinding.tvTestResult.text = aff?.getTestDelayString().orEmpty()
            if ((aff?.testDelayMillis ?: 0L) < 0L) {
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(activity, R.color.colorPingRed))
            } else {
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(activity, R.color.colorPing))
            }
            if (guid == MmkvManager.getSelectServer()) {
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.color.selected_server_highlight)
            } else {
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(0)
            }
            holder.itemMainBinding.tvSubscription.text = MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks ?: ""

            var shareOptions = share_method.asList()
            when (profile.configType) {
                EConfigType.CUSTOM -> {
                    holder.itemMainBinding.tvType.text = activity.getString(R.string.server_customize_config)
                    shareOptions = shareOptions.takeLast(1)
                }
                else -> {
                    holder.itemMainBinding.tvType.text = ""
                }
            }

            val strState = "${
                profile.server?.let {
                    if (it.contains(":"))
                        it.split(":").take(1).joinToString(":", postfix = ":***")
                    else
                        it.split('.').dropLast(2).joinToString(".", postfix = ".***")
                }
            } : ${profile.serverPort}"

            holder.itemMainBinding.tvStatistics.text = "Server-Hongkong"

            holder.itemMainBinding.infoContainer.setOnClickListener {
                val selected = MmkvManager.getSelectServer()
                if (guid != selected) {
                    MmkvManager.setSelectServer(guid)
                    if (!TextUtils.isEmpty(selected)) {
                        notifyItemChanged(getPosition(selected.orEmpty()))
                    }
                    notifyItemChanged(getPosition(guid))
                    if (isRunning) {
                        Utils.stopVService(activity)
                        Observable.timer(500, TimeUnit.MILLISECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                V2RayServiceManager.startV2Ray(activity)
                            }
                    }
                }
            }
        }
        if (holder is FooterViewHolder) {
            if (true) {
                holder.itemFooterBinding.layoutEdit.visibility = View.INVISIBLE
            } else {
                holder.itemFooterBinding.layoutEdit.setOnClickListener {
                    Utils.openUri(activity, "${Utils.decode(AppConfig.PromotionUrl)}?t=${System.currentTimeMillis()}")
                }
            }
        }
    }

    private fun shareFullContent(guid: String) {
        if (AngConfigManager.shareFullContent2Clipboard(activity, guid) == 0) {
            activity.toast(R.string.toast_success)
        } else {
            activity.toast(R.string.toast_failure)
        }
    }

    private fun removeServer(guid: String, position: Int) {
        onServerRemoved(guid)
        notifyItemRemoved(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM ->
                MainViewHolder(ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false))

            else ->
                FooterViewHolder(ItemRecyclerFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == serversCache.size) {
            VIEW_TYPE_FOOTER
        } else {
            VIEW_TYPE_ITEM
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) :
        BaseViewHolder(itemMainBinding.root), ItemTouchHelperViewHolder

    class FooterViewHolder(val itemFooterBinding: ItemRecyclerFooterBinding) :
        BaseViewHolder(itemFooterBinding.root)

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        // Implementation needed
        return false
    }

    override fun onItemMoveCompleted() {
        // Implementation needed
    }

    override fun onItemDismiss(position: Int) {
        // Implementation needed
    }
}
