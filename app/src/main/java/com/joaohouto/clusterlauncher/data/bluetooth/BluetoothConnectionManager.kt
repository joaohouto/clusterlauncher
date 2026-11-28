package com.joaohouto.clusterlauncher.data.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.joaohouto.clusterlauncher.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

enum class BtDeviceCategory {
    PHONE,
    AUDIO,
    COMPUTER,
    OTHER
}

data class BtDeviceItem(
    val address: String,
    val name: String,
    val category: BtDeviceCategory,
    val isBonded: Boolean,
    val isBonding: Boolean = false,
    val isConnected: Boolean = false,
    val device: BluetoothDevice
)

data class BluetoothUiState(
    val isSupported: Boolean = true,
    val hasPermissions: Boolean = false,
    val isEnabled: Boolean = false,
    val localName: String = "",
    val isDiscovering: Boolean = false,
    val isDiscoverable: Boolean = false,
    val pairedDevices: List<BtDeviceItem> = emptyList(),
    val discoveredDevices: List<BtDeviceItem> = emptyList(),
    val statusMessage: String? = null
)

/**
 * Gerenciador reativo de conexões Bluetooth para o modal nativo do Cockpit.
 * Utiliza a stack padrão do Android (BluetoothAdapter / BluetoothProfile) com
 * fallback seguro para perfis automotivos (A2DP_SINK / HEADSET_CLIENT) via reflection.
 */
object BluetoothConnectionManager {

    private const val TAG = "BtConnectionManager"
    private const val PROFILE_A2DP_SINK = 11
    private const val PROFILE_HEADSET_CLIENT = 16

    private val _uiState = MutableStateFlow(BluetoothUiState())
    val uiState: StateFlow<BluetoothUiState> = _uiState.asStateFlow()

    private val discoveredMap = LinkedHashMap<String, BluetoothDevice>()
    private val connectedAclAddresses = ConcurrentHashMap.newKeySet<String>()
    private val activeProxies = ConcurrentHashMap<Int, BluetoothProfile>()

    @Volatile
    private var isRegistered = false
    private var appContext: Context? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            activeProxies[profile] = proxy
            appContext?.let { refreshState(it) }
        }

        override fun onServiceDisconnected(profile: Int) {
            activeProxies.remove(profile)
            appContext?.let { refreshState(it) }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED,
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    refreshState(context)
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _uiState.update { it.copy(isDiscovering = true) }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _uiState.update { it.copy(isDiscovering = false) }
                }

                BluetoothDevice.ACTION_FOUND -> {
                    val device = extractDevice(intent) ?: return
                    val address = device.address ?: return
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        synchronized(discoveredMap) {
                            discoveredMap[address] = device
                        }
                        refreshState(context)
                    }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = extractDevice(intent)
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    if (device != null && bondState == BluetoothDevice.BOND_BONDED) {
                        synchronized(discoveredMap) {
                            discoveredMap.remove(device.address)
                        }
                    }
                    refreshState(context)
                }

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    extractDevice(intent)?.address?.let { connectedAclAddresses.add(it) }
                    refreshState(context)
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    extractDevice(intent)?.address?.let { connectedAclAddresses.remove(it) }
                    refreshState(context)
                }
            }
        }
    }

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasRequiredPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getAdapter(context: Context): BluetoothAdapter? {
        val manager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        @Suppress("DEPRECATION")
        return manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    fun startObserving(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx

        if (!isRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            runCatching {
                ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
                isRegistered = true
            }.onFailure { e ->
                Log.w(TAG, "Falha ao registrar receiver Bluetooth", e)
            }
        }

        bindProfileProxies(ctx)
        refreshState(ctx)
    }

    @SuppressLint("MissingPermission")
    fun stopObserving(context: Context) {
        val ctx = context.applicationContext
        runCatching {
            val adapter = getAdapter(ctx)
            if (hasRequiredPermissions(ctx) && adapter?.isDiscovering == true) {
                adapter.cancelDiscovery()
            }
        }
        if (isRegistered) {
            runCatching { ctx.unregisterReceiver(receiver) }
            isRegistered = false
        }
        val adapter = getAdapter(ctx)
        if (adapter != null) {
            activeProxies.forEach { (profile, proxy) ->
                runCatching { adapter.closeProfileProxy(profile, proxy) }
            }
        }
        activeProxies.clear()
        _uiState.update { it.copy(isDiscovering = false, statusMessage = null) }
    }

    private fun bindProfileProxies(context: Context) {
        if (!hasRequiredPermissions(context)) return
        val adapter = getAdapter(context) ?: return
        if (!adapter.isEnabled) return

        val profiles = listOf(
            BluetoothProfile.A2DP,
            BluetoothProfile.HEADSET,
            PROFILE_A2DP_SINK,
            PROFILE_HEADSET_CLIENT
        )
        profiles.forEach { profile ->
            if (!activeProxies.containsKey(profile)) {
                runCatching {
                    adapter.getProfileProxy(context, profileListener, profile)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshState(context: Context) {
        val adapter = getAdapter(context)
        if (adapter == null) {
            _uiState.value = BluetoothUiState(
                isSupported = false,
                hasPermissions = true,
                isEnabled = false
            )
            return
        }

        val hasPerms = hasRequiredPermissions(context)
        if (!hasPerms) {
            _uiState.update {
                it.copy(
                    isSupported = true,
                    hasPermissions = false
                )
            }
            return
        }

        val isEnabled = runCatching { adapter.isEnabled }.getOrDefault(false)
        if (isEnabled && activeProxies.isEmpty()) {
            bindProfileProxies(context)
        }

        val localName = runCatching { adapter.name.orEmpty() }.getOrDefault("")
        val isDiscovering = if (isEnabled) runCatching { adapter.isDiscovering }.getOrDefault(false) else false
        val isDiscoverable = if (isEnabled) {
            runCatching { adapter.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE }.getOrDefault(false)
        } else {
            false
        }

        val connectedAddresses = resolveConnectedAddresses()
        val bondedRaw = if (isEnabled) {
            runCatching { adapter.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        } else {
            emptySet()
        }

        val bondedAddresses = bondedRaw.map { it.address }.toSet()
        val pairedList = bondedRaw.map { device ->
            val connected = connectedAddresses.contains(device.address) || isDeviceConnectedReflection(device)
            device.toUiItem(isBonded = true, isConnected = connected)
        }.sortedWith(
            compareByDescending<BtDeviceItem> { it.isConnected }
                .thenBy { it.name.lowercase() }
        )

        val discoveredList = if (isEnabled) {
            synchronized(discoveredMap) {
                discoveredMap.values
                    .filter { it.address !in bondedAddresses }
                    .map { device ->
                        device.toUiItem(
                            isBonded = false,
                            isBonding = runCatching { device.bondState == BluetoothDevice.BOND_BONDING }.getOrDefault(false),
                            isConnected = false
                        )
                    }
                    .sortedBy { it.name.lowercase() }
            }
        } else {
            emptyList()
        }

        _uiState.update { current ->
            current.copy(
                isSupported = true,
                hasPermissions = true,
                isEnabled = isEnabled,
                localName = localName,
                isDiscovering = isDiscovering,
                isDiscoverable = isDiscoverable,
                pairedDevices = pairedList,
                discoveredDevices = discoveredList
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(context: Context) {
        if (!hasRequiredPermissions(context)) return
        val adapter = getAdapter(context) ?: return
        if (!adapter.isEnabled) return

        synchronized(discoveredMap) {
            discoveredMap.clear()
        }
        runCatching {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            val started = adapter.startDiscovery()
            _uiState.update {
                it.copy(
                    isDiscovering = started,
                    discoveredDevices = emptyList(),
                    statusMessage = if (started) null else context.getString(R.string.bt_status_scan_failed)
                )
            }
        }.onFailure { e ->
            Log.w(TAG, "Erro ao iniciar busca Bluetooth", e)
            _uiState.update { it.copy(statusMessage = context.getString(R.string.bt_status_scan_failed)) }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery(context: Context) {
        if (!hasRequiredPermissions(context)) return
        runCatching {
            getAdapter(context)?.cancelDiscovery()
        }
        _uiState.update { it.copy(isDiscovering = false) }
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(context: Context, item: BtDeviceItem) {
        if (!hasRequiredPermissions(context)) return
        runCatching {
            getAdapter(context)?.cancelDiscovery()
            val started = item.device.createBond()
            _uiState.update {
                it.copy(
                    statusMessage = if (started) {
                        context.getString(R.string.bt_status_pairing, item.name)
                    } else {
                        context.getString(R.string.bt_status_pair_failed)
                    }
                )
            }
            refreshState(context)
        }.onFailure { e ->
            Log.w(TAG, "Falha ao parear dispositivo ${item.address}", e)
            _uiState.update { it.copy(statusMessage = context.getString(R.string.bt_status_pair_failed)) }
        }
    }

    @SuppressLint("MissingPermission")
    fun unpairDevice(context: Context, item: BtDeviceItem) {
        if (!hasRequiredPermissions(context)) return
        val removed = runCatching {
            val method = item.device.javaClass.getMethod("removeBond")
            method.invoke(item.device) as? Boolean ?: false
        }.getOrDefault(false)

        _uiState.update {
            it.copy(
                statusMessage = if (removed) {
                    context.getString(R.string.bt_status_unpaired, item.name)
                } else {
                    context.getString(R.string.bt_status_action_restricted)
                }
            )
        }
        refreshState(context)
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(context: Context, item: BtDeviceItem) {
        if (!hasRequiredPermissions(context)) return
        runCatching { getAdapter(context)?.cancelDiscovery() }

        var invoked = false
        for (proxy in activeProxies.values) {
            val success = runCatching {
                val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                method.invoke(proxy, item.device) as? Boolean ?: true
            }.getOrDefault(false)
            if (success) invoked = true
        }

        _uiState.update {
            it.copy(
                statusMessage = if (invoked) {
                    context.getString(R.string.bt_status_connecting, item.name)
                } else {
                    context.getString(R.string.bt_status_action_restricted)
                }
            )
        }
        refreshState(context)
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice(context: Context, item: BtDeviceItem) {
        if (!hasRequiredPermissions(context)) return

        var invoked = false
        for (proxy in activeProxies.values) {
            val success = runCatching {
                val method = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                method.invoke(proxy, item.device) as? Boolean ?: true
            }.getOrDefault(false)
            if (success) invoked = true
        }

        _uiState.update {
            it.copy(
                statusMessage = if (invoked) {
                    context.getString(R.string.bt_status_disconnecting, item.name)
                } else {
                    context.getString(R.string.bt_status_action_restricted)
                }
            )
        }
        refreshState(context)
    }

    @SuppressLint("MissingPermission")
    fun setBluetoothEnabledLegacy(context: Context, enable: Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return false
        val adapter = getAdapter(context) ?: return false
        return runCatching {
            @Suppress("DEPRECATION")
            if (enable) adapter.enable() else adapter.disable()
        }.getOrDefault(false)
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    @SuppressLint("MissingPermission")
    private fun resolveConnectedAddresses(): Set<String> {
        val result = HashSet<String>(connectedAclAddresses)
        for (proxy in activeProxies.values) {
            runCatching {
                proxy.connectedDevices.forEach { device ->
                    device.address?.let { result.add(it) }
                }
            }
        }
        return result
    }

    private fun isDeviceConnectedReflection(device: BluetoothDevice): Boolean {
        return runCatching {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toUiItem(
        isBonded: Boolean,
        isBonding: Boolean = false,
        isConnected: Boolean = false
    ): BtDeviceItem {
        val displayName = runCatching { name?.takeIf { it.isNotBlank() } }.getOrNull() ?: address
        val majorClass = runCatching { bluetoothClass?.majorDeviceClass }.getOrNull()
        val category = when (majorClass) {
            BluetoothClass.Device.Major.PHONE -> BtDeviceCategory.PHONE
            BluetoothClass.Device.Major.AUDIO_VIDEO -> BtDeviceCategory.AUDIO
            BluetoothClass.Device.Major.COMPUTER -> BtDeviceCategory.COMPUTER
            else -> BtDeviceCategory.OTHER
        }
        return BtDeviceItem(
            address = address,
            name = displayName,
            category = category,
            isBonded = isBonded,
            isBonding = isBonding,
            isConnected = isConnected,
            device = this
        )
    }

    @Suppress("DEPRECATION")
    private fun extractDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }
}
