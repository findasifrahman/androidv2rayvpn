package com.globlink.vpn.ui

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.globlink.vpn.AppConfig
import com.globlink.vpn.AppConfig.ANG_PACKAGE
import com.globlink.vpn.R
import com.globlink.vpn.databinding.ActivityBypassListBinding
import com.globlink.vpn.dto.AppInfo
import com.globlink.vpn.extension.toast
import com.globlink.vpn.extension.v2RayApplication
import com.globlink.vpn.handler.MmkvManager
import com.globlink.vpn.util.AppManagerUtil
import com.globlink.vpn.util.Utils
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.Collator

class PerAppProxyActivity : BaseActivity() {
    private val binding by lazy {
        ActivityBypassListBinding.inflate(layoutInflater)
    }

    private var adapter: PerAppProxyAdapter? = null
    private var appsAll: List<AppInfo>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val dividerItemDecoration = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        binding.recyclerView.addItemDecoration(dividerItemDecoration)

        val blacklist = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)

        AppManagerUtil.rxLoadNetworkAppList(this)
            .subscribeOn(Schedulers.io())
            .map {
                if (blacklist != null) {
                    it.forEach { one ->
                        if (blacklist.contains(one.packageName)) {
                            one.isSelected = 1
                        } else {
                            one.isSelected = 0
                        }
                    }
                    val comparator = Comparator<AppInfo> { p1, p2 ->
                        when {
                            p1.isSelected > p2.isSelected -> -1
                            p1.isSelected == p2.isSelected -> 0
                            else -> 1
                        }
                    }
                    it.sortedWith(comparator)
                } else {
                    val comparator = object : Comparator<AppInfo> {
                        val collator = Collator.getInstance()
                        override fun compare(o1: AppInfo, o2: AppInfo) = collator.compare(o1.appName, o2.appName)
                    }
                    it.sortedWith(comparator)
                }
            }
//                .map {
//                    val comparator = object : Comparator<AppInfo> {
//                        val collator = Collator.getInstance()
//                        override fun compare(o1: AppInfo, o2: AppInfo) = collator.compare(o1.appName, o2.appName)
//                    }
//                    it.sortedWith(comparator)
//                }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                appsAll = it
                adapter = PerAppProxyAdapter(this, it, blacklist)
                binding.recyclerView.adapter = adapter
                binding.pbWaiting.visibility = View.GONE
            }
        /***
        recycler_view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
        var dst = 0
        val threshold = resources.getDimensionPixelSize(R.dimen.bypass_list_header_height) * 2
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        dst += dy
        if (dst > threshold) {
        header_view.hide()
        dst = 0
        } else if (dst < -20) {
        header_view.show()
        dst = 0
        }
        }

        var hiding = false
        fun View.hide() {
        val target = -height.toFloat()
        if (hiding || translationY == target) return
        animate()
        .translationY(target)
        .setInterpolator(AccelerateInterpolator(2F))
        .setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator?) {
        hiding = false
        }
        })
        hiding = true
        }

        var showing = false
        fun View.show() {
        val target = 0f
        if (showing || translationY == target) return
        animate()
        .translationY(target)
        .setInterpolator(DecelerateInterpolator(2F))
        .setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator?) {
        showing = false
        }
        })
        showing = true
        }
        })
         ***/

        binding.switchPerAppProxy.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, isChecked)
        }
        binding.switchPerAppProxy.isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)

        binding.switchBypassApps.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, isChecked)
        }
        binding.switchBypassApps.isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)

        /***
        et_search.setOnEditorActionListener { v, actionId, event ->
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
        //hide
        var imm: InputMethodManager = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)

        val key = v.text.toString().toUpperCase()
        val apps = ArrayList<AppInfo>()
        if (TextUtils.isEmpty(key)) {
        appsAll?.forEach {
        apps.add(it)
        }
        } else {
        appsAll?.forEach {
        if (it.appName.toUpperCase().indexOf(key) >= 0) {
        apps.add(it)
        }
        }
        }
        adapter = PerAppProxyAdapter(this, apps, adapter?.blacklist)
        recycler_view.adapter = adapter
        adapter?.notifyDataSetChanged()
        true
        } else {
        false
        }
        }
         ***/
    }

    override fun onPause() {
        super.onPause()
        adapter?.let {
            MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, it.blacklist)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_bypass_list, menu)

        val searchItem = menu.findItem(R.id.search_view)
        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    filterProxyApp(newText.orEmpty())
                    return false
                }
            })
        }


        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.select_all -> adapter?.let {
            val pkgNames = it.apps.map { it.packageName }
            if (it.blacklist.containsAll(pkgNames)) {
                it.apps.forEach {
                    val packageName = it.packageName
                    adapter?.blacklist?.remove(packageName)
                }
            } else {
                it.apps.forEach {
                    val packageName = it.packageName
                    adapter?.blacklist?.add(packageName)
                }
            }
            it.notifyDataSetChanged()
            true
        } == true

        R.id.select_proxy_app -> {
            selectProxyApp()
            true
        }

        R.id.import_proxy_app -> {
            importProxyApp()
            true
        }

        R.id.export_proxy_app -> {
            exportProxyApp()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun selectProxyApp() {
        toast(R.string.msg_downloading_content)
        val url = AppConfig.androidpackagenamelistUrl
        lifecycleScope.launch(Dispatchers.IO) {
            val content = Utils.getUrlContext(url, 5000)
            launch(Dispatchers.Main) {
                Log.d(ANG_PACKAGE, content)
                selectProxyApp(content, true)
                toast(R.string.toast_success)
            }
        }
    }

    private fun importProxyApp() {
        val content = Utils.getClipboard(applicationContext)
        if (TextUtils.isEmpty(content)) return
        selectProxyApp(content, false)
        toast(R.string.toast_success)
    }

    private fun exportProxyApp() {
        var lst = binding.switchBypassApps.isChecked.toString()

        adapter?.blacklist?.forEach block@{
            lst = lst + System.getProperty("line.separator") + it
        }
        Utils.setClipboard(applicationContext, lst)
        toast(R.string.toast_success)
    }

    private fun selectProxyApp(content: String, force: Boolean): Boolean {
        try {
            val proxyApps = if (TextUtils.isEmpty(content)) {
                Utils.readTextFromAssets(v2RayApplication, "proxy_packagename.txt")
            } else {
                content
            }
            if (TextUtils.isEmpty(proxyApps)) return false

            adapter?.blacklist?.clear()

            if (binding.switchBypassApps.isChecked) {
                adapter?.let {
                    it.apps.forEach block@{
                        val packageName = it.packageName
                        Log.d(ANG_PACKAGE, packageName)
                        if (!inProxyApps(proxyApps, packageName, force)) {
                            adapter?.blacklist?.add(packageName)
                            println(packageName)
                            return@block
                        }
                    }
                    it.notifyDataSetChanged()
                }
            } else {
                adapter?.let {
                    it.apps.forEach block@{
                        val packageName = it.packageName
                        Log.d(ANG_PACKAGE, packageName)
                        if (inProxyApps(proxyApps, packageName, force)) {
                            adapter?.blacklist?.add(packageName)
                            println(packageName)
                            return@block
                        }
                    }
                    it.notifyDataSetChanged()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun inProxyApps(proxyApps: String, packageName: String, force: Boolean): Boolean {
        if (force) {
            if (packageName == "com.google.android.webview") return false
            if (packageName.startsWith("com.google")) return true
        }

        return proxyApps.indexOf(packageName) >= 0
    }

    private fun filterProxyApp(content: String): Boolean {
        val apps = ArrayList<AppInfo>()

        val key = content.uppercase()
        if (key.isNotEmpty()) {
            appsAll?.forEach {
                if (it.appName.uppercase().indexOf(key) >= 0
                    || it.packageName.uppercase().indexOf(key) >= 0
                ) {
                    apps.add(it)
                }
            }
        } else {
            appsAll?.forEach {
                apps.add(it)
            }
        }

        adapter = PerAppProxyAdapter(this, apps, adapter?.blacklist)
        binding.recyclerView.adapter = adapter
        adapter?.notifyDataSetChanged()
        return true
    }
}
