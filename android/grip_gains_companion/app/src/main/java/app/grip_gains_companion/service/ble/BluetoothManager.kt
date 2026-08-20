package app.grip_gains_companion.service.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.grip_gains_companion.config.AppConstants
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.model.DeviceType
import app.grip_gains_companion.model.ForceDevice
import app.grip_gains_companion.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Manages Bluetooth Low Energy operations for discovering and connecting to force measurement devices
 * Supports: Tindeq Progressor, PitchSix Force Board, Weiheng WH-C06
 */
@SuppressLint("MissingPermission")
class BluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothManager"
        private const val PREFS_NAME = "bluetooth_prefs"
        private const val KEY_SELECTED_DEVICE_TYPE = "selected_device_type"
        private const val KEY_LAST_CONNECTED_DEVICE = "last_connected_device"
        private val CLIENT_CHARACTERISTIC_CONFIG: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    private var bluetoothGatt: BluetoothGatt? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var pitchSixDeviceModeCharacteristic: BluetoothGattCharacteristic? = null

    private val handler = Handler(Looper.getMainLooper())
    private val tindeqShutdownCoordinator = TindeqShutdownCoordinator(
        postDelayed = { runnable, delayMs -> handler.postDelayed(runnable, delayMs) },
        removeCallbacks = { runnable -> handler.removeCallbacks(runnable) }
    )
    private var retryCount = 0
    private var pendingDevice: ForceDevice? = null
    private var shouldAutoReconnect = true

    // Device-specific services
    private var pitchSixService: PitchSixService? = null
    private var whc06Service: WHC06Service? = null

    // Holds the UI state to sync with the clone hardware
    private var hardwareUnitIsLbs: Boolean = false

    // Preferences for persistence
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // State flows
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Initializing)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<ForceDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<ForceDevice>> = _discoveredDevices.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    private val _connectedDeviceType = MutableStateFlow<DeviceType?>(null)
    val connectedDeviceType: StateFlow<DeviceType?> = _connectedDeviceType.asStateFlow()

    private val _selectedDeviceType = MutableStateFlow(DeviceType.TINDEQ_PROGRESSOR)
    val selectedDeviceType: StateFlow<DeviceType> = _selectedDeviceType.asStateFlow()

    // Callback for force samples
    var onForceSample: ((Double, Long) -> Unit)? = null

    // Stored device address for auto-reconnect
    private var lastConnectedDeviceAddress: String? = null

    init {
        // Restore persisted device type
        val savedType = prefs.getString(KEY_SELECTED_DEVICE_TYPE, null)
        DeviceType.fromString(savedType)?.let {
            _selectedDeviceType.value = it
        }

        // Restore last connected device
        lastConnectedDeviceAddress = prefs.getString(KEY_LAST_CONNECTED_DEVICE, null)

        if (bluetoothAdapter == null) {
            _connectionState.value = ConnectionState.Error("Bluetooth not available")
        } else if (!bluetoothAdapter.isEnabled) {
            _connectionState.value = ConnectionState.Error("Bluetooth is off")
        } else {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    // MARK: - Device Type Selection

    fun setSelectedDeviceType(type: DeviceType) {
        _selectedDeviceType.value = type
        prefs.edit().putString(KEY_SELECTED_DEVICE_TYPE, type.name).apply()
        // Clear discovered devices when changing type
        _discoveredDevices.value = emptyList()
    }

    // Called from MainActivity to sync the math!
    fun setHardwareUnitIsLbs(isLbs: Boolean) {
        hardwareUnitIsLbs = isLbs
        whc06Service?.assumeHardwareIsLbs = isLbs
    }

    // MARK: - Scanning

    fun startScanning() {
        if (bluetoothAdapter?.isEnabled != true) {
            _connectionState.value = ConnectionState.Error("Bluetooth is off")
            return
        }

        // On Android 11 and below, Location Services must be enabled for BLE scanning
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (!isLocationEnabled) {
                Log.e(TAG, "Location services disabled - required for BLE scanning on Android 11 and below")
                _connectionState.value = ConnectionState.Error("Location services required")
                return
            }
        }

        Log.i(TAG, "Starting scan for ${_selectedDeviceType.value.displayName}...")
        _discoveredDevices.value = emptyList()
        _connectionState.value = ConnectionState.Scanning

        bluetoothLeScanner = bluetoothManager.adapter?.bluetoothLeScanner

        if (bluetoothLeScanner == null) {
            _connectionState.value = ConnectionState.Error("Failed to initialize scanner. Try restarting Bluetooth.")
            return
        }

        // AGGRESSIVE SCANNING: Stop Android from throttling your connection
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        // No filter - we'll filter by device type in the callback
        bluetoothLeScanner?.startScan(null, settings, scanCallback)
    }

    fun stopScanning() {
        bluetoothLeScanner?.stopScan(scanCallback)
        if (_connectionState.value == ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = result.device.name
            val deviceAddress = result.device.address

            // THE RECONNECT FIX: Process advertisements if we are Connected OR Reconnecting!
            val isWhc06Active = _connectedDeviceType.value == DeviceType.WEIHENG_WHC06 || pendingDevice?.type == DeviceType.WEIHENG_WHC06
            val isExpectedDevice = pendingDevice != null && deviceAddress == pendingDevice?.address

            if (isWhc06Active && isExpectedDevice) {
                // If we were reconnecting, and we just heard a packet, we are officially connected again!
                if (_connectionState.value == ConnectionState.Reconnecting || _connectionState.value == ConnectionState.Connecting) {
                    _connectionState.value = ConnectionState.Connected
                    _connectedDeviceName.value = pendingDevice?.name ?: "WH-C06"
                    _connectedDeviceAddress.value = deviceAddress
                    retryCount = 0
                    cancelRetryTimer()
                }
                whc06Service?.processAdvertisement(result)
            }

            // Create device if it matches the selected type
            if (deviceName != null && deviceName.isNotBlank()) {
                val inferredType = when {
                    deviceName.contains("Progressor", ignoreCase = true) -> DeviceType.TINDEQ_PROGRESSOR
                    deviceName.contains("PitchSix", ignoreCase = true) || deviceName.contains("Force Board", ignoreCase = true) -> DeviceType.PITCH_SIX_FORCE_BOARD
                    deviceName.contains("WH-C06", ignoreCase = true) || deviceName.contains("IF_B7", ignoreCase = true) -> DeviceType.WEIHENG_WHC06
                    else -> null
                }
                if (inferredType != null) {
                    val device = ForceDevice.fromScanResult(result, inferredType)
                        ?: ForceDevice(deviceAddress, deviceName, inferredType)
                    val currentList = _discoveredDevices.value.toMutableList()
                    val existingIndex = currentList.indexOfFirst { it.address == device.address }
                    if (existingIndex >= 0) {
                        currentList[existingIndex] = device
                    } else {
                        currentList.add(device)
                        if (device.address == lastConnectedDeviceAddress) {
                            connect(device)
                        }
                    }
                    _discoveredDevices.value = currentList
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            _connectionState.value = ConnectionState.Error("Scan failed: $errorCode")
        }
    }

    // MARK: - Connection

    fun connect(device: ForceDevice) {
        Log.i(TAG, "Connecting to ${device.name} (${device.type.shortName})...")
        stopScanning()
        cancelRetryTimer()

        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null

        pendingDevice = device
        shouldAutoReconnect = true
        _connectionState.value = ConnectionState.Connecting

        when (device.type) {
            DeviceType.WEIHENG_WHC06 -> {
                // WHC06 doesn't use GATT - just start processing advertisements
                connectWHC06(device)
            }
            else -> {
                // GATT-based devices (Tindeq, PitchSix)
                val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                bluetoothGatt = bluetoothDevice?.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }
        }
    }

    private fun connectWHC06(device: ForceDevice) {
        Log.i(TAG, "Setting up WHC06 advertisement-based connection...")

        // Create and configure WHC06 service
        whc06Service = WHC06Service().apply {
            assumeHardwareIsLbs = hardwareUnitIsLbs // Inject the UI state immediately on connect!
            onForceSample = { weight, timestamp ->
                this@BluetoothManager.onForceSample?.invoke(weight, timestamp)
            }
            onDisconnect = {
                Log.i(TAG, "WHC06 disconnected (no advertisements)")
                
                // If auto-reconnect is enabled, set Reconnecting state instead of Disconnected
                if (shouldAutoReconnect) {
                    _connectionState.value = ConnectionState.Reconnecting
                } else {
                    _connectionState.value = ConnectionState.Disconnected
                    _connectedDeviceName.value = null
                    _connectedDeviceType.value = null
                    _connectedDeviceAddress.value = null
                }
            }
        }

        whc06Service?.start()

        // Mark as connected
        _connectionState.value = ConnectionState.Connected
        _connectedDeviceName.value = device.name
        _connectedDeviceType.value = device.type
        _connectedDeviceAddress.value = device.address
        lastConnectedDeviceAddress = device.address
        prefs.edit().putString(KEY_LAST_CONNECTED_DEVICE, device.address).apply()
        stopScanning()

        // Resume scanning to receive advertisements
        Log.i(TAG, "Resuming scan to receive WHC06 advertisements...")
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val filter = ScanFilter.Builder().setDeviceAddress(device.address).build()
        bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
    }

    fun disconnect(preserveAutoReconnect: Boolean = false) {
        Log.i(TAG, "Disconnecting${if (preserveAutoReconnect) " (preserving auto-reconnect)" else ""}...")

        shouldAutoReconnect = false
        cancelRetryTimer()

        stopScanning()

        val deviceType = _connectedDeviceType.value ?: pendingDevice?.type
        tindeqShutdownCoordinator.disconnect(
            deviceType = deviceType,
            sendShutdownCommand = ::sendProgressorShutdownCommand,
            disconnect = { finishDisconnect(preserveAutoReconnect) }
        )
    }

    private fun finishDisconnect(preserveAutoReconnect: Boolean) {
        pendingDevice = null

        // Stop device-specific services
        pitchSixService?.stop()
        pitchSixService = null
        whc06Service?.stop()
        whc06Service = null

        bluetoothGatt?.close()
        bluetoothGatt = null
        notifyCharacteristic = null
        writeCharacteristic = null
        _connectedDeviceName.value = null
        _connectedDeviceType.value = null
        _connectedDeviceAddress.value = null

        if (!preserveAutoReconnect) {
            lastConnectedDeviceAddress = null
            prefs.edit().remove(KEY_LAST_CONNECTED_DEVICE).apply()
        }

        _discoveredDevices.value = emptyList()
        _connectionState.value = ConnectionState.Disconnected

        if (!preserveAutoReconnect) {
            startScanning()
        }
    }

    // MARK: - GATT Callback

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to ${gatt.device.name}")
                    retryCount = 0
                    _connectionState.value = ConnectionState.Connected
                    _connectedDeviceName.value = gatt.device.name ?: pendingDevice?.type?.displayName ?: "Unknown"
                    _connectedDeviceAddress.value = gatt.device.address
                    _connectedDeviceType.value = pendingDevice?.type
                    lastConnectedDeviceAddress = gatt.device.address
                    prefs.edit().putString(KEY_LAST_CONNECTED_DEVICE, gatt.device.address).apply()

                    // Discover services
                    handler.post {
                        gatt.discoverServices()
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected")
                    
                    // If auto-reconnect is enabled, set Reconnecting state instead of Disconnected
                    if (shouldAutoReconnect) {
                        _connectionState.value = ConnectionState.Reconnecting
                        scheduleRetry()
                    } else {
                        _connectionState.value = ConnectionState.Disconnected
                        _connectedDeviceName.value = null
                        _connectedDeviceType.value = null
                        _connectedDeviceAddress.value = null
                    }
                    
                    notifyCharacteristic = null
                    writeCharacteristic = null

                    // Stop device-specific services
                    pitchSixService?.stop()
                    pitchSixService = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                return
            }

            Log.i(TAG, "Services discovered: ${gatt.services.size}")

            val deviceType = pendingDevice?.type ?: return

            when (deviceType) {
                DeviceType.TINDEQ_PROGRESSOR -> setupProgressorService(gatt)
                DeviceType.PITCH_SIX_FORCE_BOARD -> setupPitchSixService(gatt)
                else -> {}
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                AppLogger.i(TAG, "Notifications enabled successfully for ${descriptor.characteristic.uuid}")
                Log.i(TAG, "Notifications enabled")
                // Start measurement based on device type
                val deviceType = pendingDevice?.type
                when (deviceType) {
                    DeviceType.TINDEQ_PROGRESSOR -> startProgressorMeasurement()
                    DeviceType.PITCH_SIX_FORCE_BOARD -> pitchSixService?.let { service ->
                        pitchSixDeviceModeCharacteristic?.let { char ->
                            AppLogger.i(TAG, "Sending start streaming command to Device Mode characteristic ${char.uuid}")
                            service.startStreaming(gatt, char)
                        } ?: AppLogger.e(TAG, "PitchSix Device Mode characteristic not available")
                    }
                    else -> {}
                }
            } else {
                AppLogger.e(TAG, "Failed to enable notifications: $status")
                Log.e(TAG, "Failed to enable notifications: $status")
            }
        }

        // API 33+ callback with ByteArray parameter
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            //AppLogger.d(TAG, "onCharacteristicChanged: ${characteristic.uuid}, ${value.size} bytes")
            handleCharacteristicChanged(characteristic, value)
        }

        // API < 33 callback without ByteArray parameter
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value
            AppLogger.d(TAG, "onCharacteristicChanged (legacy): ${characteristic.uuid}, ${value?.size ?: 0} bytes")
            if (value != null) {
                handleCharacteristicChanged(characteristic, value)
            }
        }

        private fun handleCharacteristicChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val deviceType = pendingDevice?.type
            when (deviceType) {
                DeviceType.TINDEQ_PROGRESSOR -> {
                    if (characteristic.uuid == AppConstants.PROGRESSOR_NOTIFY_CHARACTERISTIC_UUID) {
                        parseProgressorNotification(value)
                    }
                }
                DeviceType.PITCH_SIX_FORCE_BOARD -> {
                    //AppLogger.d(TAG, "Passing to PitchSixService: ${value.size} bytes")
                    pitchSixService?.parseNotification(value)
                }
                else -> {}
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            tindeqShutdownCoordinator.onWriteComplete(characteristic.uuid)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                AppLogger.i(TAG, "Write successful to ${characteristic.uuid}")
                Log.i(TAG, "Write successful")
            } else {
                AppLogger.e(TAG, "Write failed to ${characteristic.uuid}: $status")
                Log.e(TAG, "Write failed: $status")
            }
        }
    }

    // MARK: - Tindeq Progressor Setup

    private fun setupProgressorService(gatt: BluetoothGatt) {
        val service = gatt.getService(AppConstants.PROGRESSOR_SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "Progressor service not found")
            return
        }

        Log.i(TAG, "Found Progressor service")

        notifyCharacteristic = service.getCharacteristic(AppConstants.PROGRESSOR_NOTIFY_CHARACTERISTIC_UUID)
        writeCharacteristic = service.getCharacteristic(AppConstants.PROGRESSOR_WRITE_CHARACTERISTIC_UUID)

        if (notifyCharacteristic == null) {
            Log.e(TAG, "Notify characteristic not found")
            return
        }

        enableNotifications(gatt, notifyCharacteristic!!)
    }

    private fun startProgressorMeasurement() {
        if (writeCharacteristic == null) {
            Log.e(TAG, "Write characteristic not available")
            return
        }

        Log.i(TAG, "Sending start weight command...")
        if (!writeProgressorCommand(AppConstants.PROGRESSOR_START_WEIGHT_COMMAND)) {
            Log.e(TAG, "Failed to queue start weight command")
        }
    }

    private fun sendProgressorShutdownCommand(command: ByteArray): Boolean {
        if (writeCharacteristic == null) {
            Log.e(TAG, "Write characteristic not available for shutdown")
            return false
        }

        Log.i(TAG, "Sending Tindeq shutdown command...")
        return writeProgressorCommand(command)
    }

    private fun writeProgressorCommand(command: ByteArray): Boolean {
        val characteristic = writeCharacteristic ?: return false
        val gatt = bluetoothGatt ?: return false

        // Use appropriate API based on Android version
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+: new method signature
            gatt.writeCharacteristic(
                characteristic,
                command,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            // API < 33: old method signature
            @Suppress("DEPRECATION")
            characteristic.value = command
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun parseProgressorNotification(data: ByteArray) {
        // Verify packet type and minimum size
        if (data.size < AppConstants.PACKET_MIN_SIZE ||
            data[0] != AppConstants.WEIGHT_DATA_PACKET_TYPE) {
            return
        }

        // Parse ALL samples from notification
        val payload = data.copyOfRange(2, data.size)

        var offset = 0
        while (offset + AppConstants.PROGRESSOR_SAMPLE_SIZE <= payload.size) {
            val weightBytes = payload.copyOfRange(offset, offset + 4)
            val timeBytes = payload.copyOfRange(offset + 4, offset + 8)

            val weightFloat = ByteBuffer.wrap(weightBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .float
            val timestamp = ByteBuffer.wrap(timeBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
                .toLong() and 0xFFFFFFFFL

            onForceSample?.invoke(weightFloat.toDouble(), timestamp)

            offset += AppConstants.PROGRESSOR_SAMPLE_SIZE
        }
    }

    // MARK: - PitchSix Setup

    private fun setupPitchSixService(gatt: BluetoothGatt) {
        // List all available services for debugging
        AppLogger.i(TAG, "Available services on PitchSix device:")
        gatt.services.forEach { svc ->
            AppLogger.i(TAG, "  Service: ${svc.uuid}")
            svc.characteristics.forEach { char ->
                val props = mutableListOf<String>()
                if ((char.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) props.add("READ")
                if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) props.add("WRITE")
                if ((char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) props.add("NOTIFY")
                if ((char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) props.add("INDICATE")
                AppLogger.i(TAG, "    Characteristic: ${char.uuid} [${props.joinToString(", ")}]")
            }
        }
        
        // Get Force Service for receiving force data (notifications)
        val forceService = gatt.getService(AppConstants.PITCH_SIX_FORCE_SERVICE_UUID)
        if (forceService == null) {
            AppLogger.e(TAG, "PitchSix Force Service not found")
            Log.e(TAG, "PitchSix Force Service not found")
            return
        }
        AppLogger.i(TAG, "Found PitchSix Force Service: ${forceService.uuid}")

        // Get Device Mode Service for sending commands (streaming mode, tare, etc.)
        val deviceModeService = gatt.getService(AppConstants.PITCH_SIX_DEVICE_MODE_SERVICE_UUID)
        if (deviceModeService == null) {
            AppLogger.e(TAG, "PitchSix Device Mode Service not found")
            Log.e(TAG, "PitchSix Device Mode Service not found")
            return
        }
        AppLogger.i(TAG, "Found PitchSix Device Mode Service: ${deviceModeService.uuid}")

        // Get Force characteristic for receiving data
        notifyCharacteristic = forceService.getCharacteristic(AppConstants.PITCH_SIX_FORCE_CHARACTERISTIC_UUID)
        if (notifyCharacteristic == null) {
            AppLogger.e(TAG, "PitchSix Force characteristic not found")
            Log.e(TAG, "PitchSix Force characteristic not found")
            return
        }
        AppLogger.i(TAG, "Found PitchSix Force characteristic: ${notifyCharacteristic?.uuid}")

        // Get Device Mode characteristic for sending commands
        pitchSixDeviceModeCharacteristic = deviceModeService.getCharacteristic(AppConstants.PITCH_SIX_DEVICE_MODE_CHARACTERISTIC_UUID)
        if (pitchSixDeviceModeCharacteristic == null) {
            AppLogger.e(TAG, "PitchSix Device Mode characteristic not found")
            Log.e(TAG, "PitchSix Device Mode characteristic not found")
            return
        }
        AppLogger.i(TAG, "Found PitchSix Device Mode characteristic: ${pitchSixDeviceModeCharacteristic?.uuid}")

        // Get Tare characteristic (optional, for direct tare command)
        writeCharacteristic = forceService.getCharacteristic(AppConstants.PITCH_SIX_TARE_CHARACTERISTIC_UUID)
        AppLogger.i(TAG, "Found PitchSix Tare characteristic: ${writeCharacteristic?.uuid}")

        // Create and configure PitchSix service
        pitchSixService = PitchSixService().apply {
            onForceSample = { weight, timestamp ->
                this@BluetoothManager.onForceSample?.invoke(weight, timestamp)
            }
        }
        pitchSixService?.start()

        enableNotifications(gatt, notifyCharacteristic!!)
    }

    // MARK: - Helper Methods

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        AppLogger.i(TAG, "Enabling notifications for ${characteristic.uuid}...")
        Log.i(TAG, "Enabling notifications...")
        
        val success = gatt.setCharacteristicNotification(characteristic, true)
        AppLogger.i(TAG, "setCharacteristicNotification returned: $success")

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            AppLogger.i(TAG, "Writing to CCCD descriptor...")
            // Use appropriate API based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+: new method signature
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                // API < 33: old method signature
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        } else {
            AppLogger.e(TAG, "CCCD descriptor not found for ${characteristic.uuid}")
        }
    }

    // MARK: - Retry Logic

    private fun calculateRetryDelay(): Long {
        val baseDelay = 1000L
        val delay = baseDelay * (1L shl minOf(retryCount, 5))
        return minOf(delay, AppConstants.MAX_RETRY_DELAY_MS)
    }

    private fun scheduleRetry() {
        val device = pendingDevice ?: return

        retryCount++
        val delay = calculateRetryDelay()
        Log.i(TAG, "Scheduling retry #$retryCount in ${delay}ms...")

        handler.postDelayed({
            if (shouldAutoReconnect) {
                Log.i(TAG, "Retrying connection to ${device.name}...")
                _connectionState.value = ConnectionState.Reconnecting

                when (device.type) {
                    DeviceType.WEIHENG_WHC06 -> connectWHC06(device)
                    else -> {
                        bluetoothGatt?.disconnect()
                        bluetoothGatt?.close()
                        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                        bluetoothGatt = bluetoothDevice?.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    }
                }
            }
        }, delay)
    }

    private fun cancelRetryTimer() {
        handler.removeCallbacksAndMessages(null)
    }
}
