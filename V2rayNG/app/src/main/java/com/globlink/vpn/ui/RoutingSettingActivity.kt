package com.globlink.vpn.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
//import com.tbruyelle.rxpermissions3.RxPermissions
import com.globlink.vpn.AppConfig
import com.globlink.vpn.R
import com.globlink.vpn.databinding.ActivityRoutingSettingBinding
import com.globlink.vpn.dto.RulesetItem
import com.globlink.vpn.extension.toast
import com.globlink.vpn.handler.MmkvManager
import com.globlink.vpn.handler.SettingsManager
import com.globlink.vpn.helper.SimpleItemTouchHelperCallback
import com.globlink.vpn.util.JsonUtil
import com.globlink.vpn.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoutingSettingActivity : BaseActivity() {
    private val binding by lazy { ActivityRoutingSettingBinding.inflate(layoutInflater) }

    // Launcher for requesting the CAMERA permission
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Launch the appropriate scanner based on the flag
                scanQRcodeForRulesets.launch(Intent(this, ScannerActivity::class.java))
            } else {
                // Permission denied; show a toast message
                Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    var rulesets: MutableList<RulesetItem> = mutableListOf()
    private val adapter by lazy { RoutingSettingRecyclerAdapter(this) }
    private var mItemTouchHelper: ItemTouchHelper? = null
    private val routing_domain_strategy: Array<out String> by lazy {
        resources.getStringArray(R.array.routing_domain_strategy)
    }
    private val preset_rulesets: Array<out String> by lazy {
        resources.getStringArray(R.array.preset_rulesets)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        title = getString(R.string.routing_settings_title)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        mItemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter))
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)

        val found = Utils.arrayFind(routing_domain_strategy, MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: "")
        found.let { binding.spDomainStrategy.setSelection(if (it >= 0) it else 0) }
        binding.spDomainStrategy.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, routing_domain_strategy[position])
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_routing_setting, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.add_rule -> {
            startActivity(Intent(this, RoutingEditActivity::class.java))
            true
        }

        R.id.user_asset_setting -> {
            startActivity(Intent(this, UserAssetActivity::class.java))
            true
        }

        R.id.import_predefined_rulesets -> {
            AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    AlertDialog.Builder(this).setItems(preset_rulesets.asList().toTypedArray()) { _, i ->
                        try {
                            lifecycleScope.launch(Dispatchers.IO) {
                                SettingsManager.resetRoutingRulesetsFromPresets(this@RoutingSettingActivity, i)
                                launch(Dispatchers.Main) {
                                    refreshData()
                                    toast(R.string.toast_success)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.show()


                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    //do noting
                }
                .show()
            true
        }

        R.id.import_rulesets_from_clipboard -> {
            AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val clipboard = try {
                        Utils.getClipboard(this)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        toast(R.string.toast_failure)
                        return@setPositiveButton
                    }
                    lifecycleScope.launch(Dispatchers.IO) {
                        val result = SettingsManager.resetRoutingRulesets(clipboard)
                        withContext(Dispatchers.Main) {
                            if (result) {
                                refreshData()
                                toast(R.string.toast_success)
                            } else {
                                toast(R.string.toast_failure)
                            }
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    //do nothing
                }
                .show()
            true
        }

        R.id.import_rulesets_from_qrcode -> {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
           /* RxPermissions(this)
                .request(Manifest.permission.CAMERA)
                .subscribe {
                    if (it)
                        scanQRcodeForRulesets.launch(Intent(this, ScannerActivity::class.java))
                    else
                        toast(R.string.toast_permission_denied)
                }*/
            true
        }


        R.id.export_rulesets_to_clipboard -> {
            val rulesetList = MmkvManager.decodeRoutingRulesets()
            if (rulesetList.isNullOrEmpty()) {
                toast(R.string.toast_failure)
            } else {
                Utils.setClipboard(this, JsonUtil.toJson(rulesetList))
                toast(R.string.toast_success)
            }
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private val scanQRcodeForRulesets = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importRulesetsFromQRcode(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    private fun importRulesetsFromQRcode(qrcode: String?): Boolean {
        AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(qrcode)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            refreshData()
                            toast(R.string.toast_success)
                        } else {
                            toast(R.string.toast_failure)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do nothing
            }
            .show()
        return true
    }

    fun refreshData() {
        rulesets.clear()
        rulesets.addAll(MmkvManager.decodeRoutingRulesets() ?: mutableListOf())
        adapter.notifyDataSetChanged()
    }

}
