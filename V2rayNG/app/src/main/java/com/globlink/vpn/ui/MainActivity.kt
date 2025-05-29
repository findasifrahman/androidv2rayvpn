package com.globlink.vpn.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
//import com.tbruyelle.rxpermissions3.RxPermissions
import com.globlink.vpn.AppConfig
import com.globlink.vpn.AppConfig.VPN
import com.globlink.vpn.R
import com.globlink.vpn.databinding.ActivityMainBinding
import com.globlink.vpn.dto.EConfigType
import com.globlink.vpn.extension.toast
import com.globlink.vpn.handler.AngConfigManager
import com.globlink.vpn.handler.MigrateManager
import com.globlink.vpn.handler.MmkvManager
import com.globlink.vpn.helper.SimpleItemTouchHelperCallback
import com.globlink.vpn.service.V2RayServiceManager
import com.globlink.vpn.service.V2RayVpnService
import com.globlink.vpn.util.Utils
import com.globlink.vpn.util.CryptoUtils
import com.globlink.vpn.viewmodel.MainViewModel


import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drakeet.support.toast.ToastCompat
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityMainBinding
    private val mainViewModel: MainViewModel by viewModels()
    
    private val adapter: MainRecyclerAdapter by lazy { 
        MainRecyclerAdapter(
            activity = this,
            serversCache = mainViewModel.serversCache,
            onServerSelected = { guid: String ->
                // Handle server selection
                val selected = MmkvManager.getSelectServer()
                if (guid != selected) {
                    MmkvManager.setSelectServer(guid)
                    if (!TextUtils.isEmpty(selected)) {
                        adapter.notifyItemChanged(mainViewModel.getPosition(selected.orEmpty()))
                    }
                    adapter.notifyItemChanged(mainViewModel.getPosition(guid))
                    if (adapter.isRunning) {
                        Utils.stopVService(this)
                        Observable.timer(500, TimeUnit.MILLISECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                V2RayServiceManager.startV2Ray(this)
                            }
                    }
                }
            },
            onServerRemoved = { guid: String ->
                mainViewModel.removeServer(guid)
            },
            getPosition = { guid: String ->
                mainViewModel.getPosition(guid)
            }
        )
    }
    private var mItemTouchHelper: ItemTouchHelper? = null
    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    this,
                    R.string.toast_permission_denied_notification,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private var forConfig: Boolean = false
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                if (forConfig)
                    scanQRCodeForConfig.launch(Intent(this, ScannerActivity::class.java))
                else
                    scanQRCodeForUrlToCustomConfig.launch(Intent(this, ScannerActivity::class.java))
            } else {
                Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    private var pendingUri: Uri? = null

    private val readContentPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pendingUri?.let { uri ->
                    try {
                        contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                            importCustomizeConfig(reader.readText())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                toast(R.string.toast_permission_denied)
            }
            pendingUri = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupUI()
        observeViewModel()

        // Set initial FAB icon based on VPN state
        if (mainViewModel.isRunning.value == true) {
            binding.fabAdd.setImageResource(R.drawable.ic_stop_24dp)
            binding.fabAdd.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_active))
        } else {
            binding.fabAdd.setImageResource(R.drawable.ic_play_24dp)
            binding.fabAdd.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_inactive))
        }

        binding.fabAdd.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                Utils.stopVService(this)
            } else if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: VPN) == VPN) {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    startV2Ray()
                } else {
                    requestVpnPermission.launch(intent)
                }
            } else {
                startV2Ray()
            }
        }

        binding.layoutTest.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                setTestState(getString(R.string.connection_test_testing))
                mainViewModel.testCurrentServerRealPing()
            }
        }

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        mItemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter))
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)

        binding.tabGroup.isVisible = false // Hide subscription tabs
        migrateLegacy()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupUI() {
        binding.fabAdd.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                Utils.stopVService(this)
            } else if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: VPN) == VPN) {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    startV2Ray()
                } else {
                    requestVpnPermission.launch(intent)
                }
            } else {
                startV2Ray()
            }
        }
    }

    private fun observeViewModel() {
        mainViewModel.serverList.observe(this) { servers ->
            // Update UI with server list
        }
        mainViewModel.updateListAction.observe(this) { index ->
            if (index >= 0) {
                adapter.notifyItemChanged(index)
            } else {
                adapter.notifyDataSetChanged()
            }
        }
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            adapter.isRunning = isRunning
            if (isRunning) {
                binding.fabAdd.setImageResource(R.drawable.ic_stop_24dp)
                binding.fabAdd.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_active))
                setTestState(getString(R.string.connection_connected))
                binding.layoutTest.isFocusable = true
            } else {
                binding.fabAdd.setImageResource(R.drawable.ic_play_24dp)
                binding.fabAdd.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_inactive))
                setTestState(getString(R.string.connection_not_connected))
                binding.layoutTest.isFocusable = false
            }
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun migrateLegacy() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = MigrateManager.migrateServerConfig2Profile()
            launch(Dispatchers.Main) {
                if (result) {
                    toast(getString(R.string.migration_success))
                    mainViewModel.reloadServerList()
                } else {
                    //toast(getString(R.string.migration_fail))
                }
            }

        }
    }

    fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        V2RayServiceManager.startV2Ray(this)
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            Utils.stopVService(this)
        }
        Observable.timer(500, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                startV2Ray()
            }
    }

    public override fun onResume() {
        super.onResume()
        mainViewModel.reloadServerList()
    }

    public override fun onPause() {
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.sub_update)?.isVisible = false // Hide subscription update menu item
        
        // Center the plus button
        val addConfigItem = menu.findItem(R.id.add_config)
        addConfigItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        
        // Make the text item non-clickable
        menu.findItem(R.id.add_server_text)?.setEnabled(false)
        
        // Style the menu items
        val menuItems = listOf(
            R.id.service_restart,
            R.id.del_all_config,
            R.id.ping_all
        )
        
        menuItems.forEach { id ->
            menu.findItem(id)?.apply {
                setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                icon?.setTint(ContextCompat.getColor(this@MainActivity, R.color.color_fab_active))
            }
        }
        
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode(true)
            true
        }
        R.id.import_clipboard -> {
            importClipboard()
            true
        }
        R.id.ping_all -> {
            toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
            mainViewModel.testAllTcping()
            true
        }
        R.id.service_restart -> {
            restartV2Ray()
            true
        }
        R.id.del_all_config -> {
            val dialog = AlertDialog.Builder(this)
                .setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    binding.pbWaiting.show()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val ret = mainViewModel.removeAllServer()
                        launch(Dispatchers.Main) {
                            mainViewModel.reloadServerList()
                            val toast = ToastCompat.makeText(this@MainActivity, getString(R.string.title_del_config_count, ret), Toast.LENGTH_LONG)
                            val textView = toast.view?.findViewById<TextView>(android.R.id.message)
                            textView?.setTextColor(Color.BLACK)
                            textView?.textSize = 16f
                            toast.show()
                            binding.pbWaiting.hide()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    //do noting
                }
                .create()
            
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.BLACK)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.BLACK)
                dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(Color.BLACK)
            }
            
            dialog.show()
            true
        }
        /*R.id.del_duplicate_config -> {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    binding.pbWaiting.show()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val ret = mainViewModel.removeDuplicateServer()
                        launch(Dispatchers.Main) {
                            mainViewModel.reloadServerList()
                            toast(getString(R.string.title_del_duplicate_config_count, ret))
                            binding.pbWaiting.hide()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    //do noting
                }
                .show()
            true
        }*/
        else -> super.onOptionsItemSelected(item)
    }

    private fun importQRcode(forConfig: Boolean): Boolean {
        requestCameraPermission(forConfig);
        return true
    }

    fun requestCameraPermission(useConfigScanner: Boolean) {
        forConfig = useConfigScanner
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private val scanQRCodeForConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importBatchConfig(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    private val scanQRCodeForUrlToCustomConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importConfigCustomUrl(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    private fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun importBatchConfig(server: String?) {
        binding.pbWaiting.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Debug: Print original input
                android.util.Log.d("V2rayNG", "Original input: $server")
                
                // Decrypt if encrypted
                val decryptedServer = if (server != null && CryptoUtils.isEncrypted(server)) {
                    android.util.Log.d("V2rayNG", "Input appears to be encrypted, attempting decryption")
                    val decrypted = CryptoUtils.decryptAES(server)
                    android.util.Log.d("V2rayNG", "Decrypted result: $decrypted")
                    decrypted
                } else {
                    android.util.Log.d("V2rayNG", "Input is not encrypted, using as is")
                    server
                }
                
                android.util.Log.d("V2rayNG", "Final config to import: $decryptedServer")
                
                val count = AngConfigManager.importBatchConfig(decryptedServer, "", true)
                delay(500L)
                withContext(Dispatchers.Main) {
                    if (count > 0) {
                        toast(getString(R.string.title_import_config_count, count))
                        mainViewModel.reloadServerList()
                    } else {
                        toast(R.string.toast_failure)
                    }
                    binding.pbWaiting.hide()
                }
            } catch (e: Exception) {
                android.util.Log.e("V2rayNG", "Error in importBatchConfig", e)
                withContext(Dispatchers.Main) {
                    toast(R.string.toast_failure)
                    binding.pbWaiting.hide()
                }
                e.printStackTrace()
            }
        }
    }

    private fun importConfigCustomUrl(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val configText = try {
                    Utils.getUrlContentWithCustomUserAgent(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }
                launch(Dispatchers.Main) {
                    importCustomizeConfig(configText)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun importCustomizeConfig(server: String?) {
        try {
            if (server == null || TextUtils.isEmpty(server)) {
                toast(R.string.toast_none_data)
                return
            }
            if (mainViewModel.appendCustomConfigServer(server)) {
                mainViewModel.reloadServerList()
                toast(R.string.toast_success)
            } else {
                toast(R.string.toast_failure)
            }
        } catch (e: Exception) {
            ToastCompat.makeText(this, "${getString(R.string.toast_malformed_josn)} ${e.cause?.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            return
        }
    }

    private fun setTestState(content: String?) {
        binding.tvTestState.text = content
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.settings -> {
                startActivity(
                    Intent(this, SettingsActivity::class.java)
                        .putExtra("isRunning", mainViewModel.isRunning.value == true)
                )
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showImportOptions() {
        val options = arrayOf(
            getString(R.string.menu_item_import_config_qrcode),
            getString(R.string.menu_item_import_config_clipboard)
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.menu_item_import_config)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importQRcode(true)
                    1 -> importClipboard()
                }
            }
            .show()
    }
}
