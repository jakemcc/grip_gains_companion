package app.grip_gains_companion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.grip_gains_companion.data.PreferencesRepository
import app.grip_gains_companion.debug.DebugForceDataSource
import app.grip_gains_companion.debug.createDebugForceDataSource
import app.grip_gains_companion.model.ConnectionState
import app.grip_gains_companion.model.ProgressorState
import app.grip_gains_companion.service.BackgroundInactivityShutdownTimer
import app.grip_gains_companion.service.ProgressorHandler
import app.grip_gains_companion.service.TargetFeedbackEvent
import app.grip_gains_companion.service.ble.BluetoothManager
import app.grip_gains_companion.service.web.WebViewBridge
import app.grip_gains_companion.ui.screens.DeviceScannerScreen
import app.grip_gains_companion.ui.screens.LogViewerScreen
import app.grip_gains_companion.ui.screens.MainScreen
import app.grip_gains_companion.ui.screens.SettingsScreen
import app.grip_gains_companion.ui.theme.GripGainsTheme
import app.grip_gains_companion.util.AudioManagerGripPeriodVolumeAccess
import app.grip_gains_companion.util.CountdownSound
import app.grip_gains_companion.util.GripAudioRouting
import app.grip_gains_companion.util.GripPeriodVolumeMuter
import app.grip_gains_companion.util.HapticManager
import app.grip_gains_companion.util.TargetSoundSettings
import app.grip_gains_companion.util.ToneGenerator
import app.grip_gains_companion.util.playEnabledTargetFeedback
import app.grip_gains_companion.util.playTargetFeedbackPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var progressorHandler: ProgressorHandler
    private lateinit var webViewBridge: WebViewBridge
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var hapticManager: HapticManager
    private lateinit var gripPeriodVolumeMuter: GripPeriodVolumeMuter
    private lateinit var debugForceDataSource: DebugForceDataSource
    private val countdownSound = CountdownSound(playSecond = ToneGenerator::playCountdownTone)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundInactivityShutdownTimer by lazy {
        BackgroundInactivityShutdownTimer(
            postDelayed = { runnable, delayMs -> mainHandler.postDelayed(runnable, delayMs) },
            removeCallbacks = { runnable -> mainHandler.removeCallbacks(runnable) },
            shutdown = { bluetoothManager.disconnect(preserveAutoReconnect = true) }
        )
    }
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false
    
    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ uses new Bluetooth permissions
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            // Android 11 and below require location for BLE scanning
            // (BLUETOOTH and BLUETOOTH_ADMIN are normal permissions, granted at install)
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            bluetoothManager.startScanning()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Initialize services
        bluetoothManager = BluetoothManager(this)
        progressorHandler = ProgressorHandler()
        debugForceDataSource = createDebugForceDataSource(
            scope = lifecycleScope,
            onSample = progressorHandler::processSample
        )
        webViewBridge = WebViewBridge()
        preferencesRepository = PreferencesRepository(this)
        hapticManager = HapticManager(this)
        gripPeriodVolumeMuter = GripPeriodVolumeMuter(AudioManagerGripPeriodVolumeAccess(this))
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                textToSpeechReady = true
            }
        }
        
        // Auto-detect unit preference on first launch
        lifecycleScope.launch {
            preferencesRepository.initializeUnitsIfNeeded()
        }
        
        // Connect BLE samples to handler
        bluetoothManager.onForceSample = { force, timestamp, isSlowScale ->
            lifecycleScope.launch {
                progressorHandler.processSample(force, timestamp, isSlowScale)
            }
        }
        
        // Set up event handlers
        setupEventHandlers()
        
        // Check permissions and start scanning
        if (hasAllPermissions()) {
            bluetoothManager.startScanning()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
        
        setContent {
            val connectionState by bluetoothManager.connectionState.collectAsState()
            val isConnected = connectionState == ConnectionState.Connected
            val simulatedForceActive by debugForceDataSource.isRunning.collectAsState()
            
            // Collect preferences
            val useLbs by preferencesRepository.useLbs.collectAsState(initial = false)
            val showStatusBar by preferencesRepository.showStatusBar.collectAsState(initial = true)
            val expandedForceBar by preferencesRepository.expandedForceBar.collectAsState(initial = true)

            // --- SYNC HARDWARE PARSER TO UI ---
            LaunchedEffect(useLbs) {
                bluetoothManager.setHardwareUnitIsLbs(useLbs)
            }

            val showForceGraph by preferencesRepository.showForceGraph.collectAsState(initial = true)
            val forceGraphWindow by preferencesRepository.forceGraphWindow.collectAsState(initial = 5)
            val enableTargetWeight by preferencesRepository.enableTargetWeight.collectAsState(initial = true)
            val useManualTarget by preferencesRepository.useManualTarget.collectAsState(initial = false)
            val manualTargetWeight by preferencesRepository.manualTargetWeight.collectAsState(initial = 20.0)
            val weightTolerance by preferencesRepository.weightTolerance.collectAsState(initial = 0.5)
            val enableHaptics by preferencesRepository.enableHaptics.collectAsState(initial = true)
            val enableTargetSound by preferencesRepository.enableTargetSound.collectAsState(initial = true)
            val enableCalibration by preferencesRepository.enableCalibration.collectAsState(initial = true)
            val mutePhoneDuringGrip by preferencesRepository.mutePhoneDuringGrip.collectAsState(initial = false)
            val showGripStats by preferencesRepository.showGripStats.collectAsState(initial = true)
            val showEndOfSessionSummary by preferencesRepository.showEndOfSessionSummary.collectAsState(
                initial = false
            )
            
            // Update handler settings
            LaunchedEffect(enableCalibration) {
                progressorHandler.enableCalibration = enableCalibration
            }
            
            // Screen state
            var skippedDevice by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(false) }
            var showLogViewer by remember { mutableStateOf(false) }
            var activeGripReconnect by remember { mutableStateOf(false) }
            
            // Haptic feedback on connect
            LaunchedEffect(connectionState, simulatedForceActive) {
                when (connectionState) {
                    ConnectionState.Reconnecting -> {
                        if (progressorHandler.engaged && !activeGripReconnect) {
                            activeGripReconnect = true
                            progressorHandler.onConnectionLost()
                            speakStatus("Disconnected")
                            if (enableHaptics) {
                                hapticManager.warning()
                            }
                        }
                    }
                    ConnectionState.Connected -> {
                        if (activeGripReconnect) {
                            progressorHandler.onConnectionRestored()
                            speakStatus("Connected")
                            activeGripReconnect = false
                        }
                        if (enableHaptics) {
                            hapticManager.success()
                        }
                    }
                    ConnectionState.Disconnected -> {
                        if (simulatedForceActive) {
                            activeGripReconnect = false
                        } else if (progressorHandler.engaged && !activeGripReconnect) {
                            activeGripReconnect = true
                            progressorHandler.onConnectionLost()
                            speakStatus("Disconnected")
                            if (enableHaptics) {
                                hapticManager.warning()
                            }
                        } else if (!activeGripReconnect) {
                            progressorHandler.reset()
                        }
                    }
                    else -> {}
                }
            }
            
            GripGainsTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        showLogViewer -> {
                            LogViewerScreen(
                                onDismiss = { showLogViewer = false }
                            )
                        }
                        
                        showSettings -> {
                            SettingsScreen(
                                preferencesRepository = preferencesRepository,
                                bluetoothManager = bluetoothManager,
                                webViewBridge = webViewBridge,
                                onDismiss = { showSettings = false },
                                onDisconnect = {
                                    showSettings = false
                                    if (simulatedForceActive) {
                                        debugForceDataSource.stop()
                                        progressorHandler.reset()
                                        if (hasAllPermissions()) {
                                            bluetoothManager.startScanning()
                                        }
                                    } else {
                                        bluetoothManager.disconnect()
                                    }
                                    skippedDevice = false
                                },
                                onConnectDevice = {
                                    showSettings = false
                                    debugForceDataSource.stop()
                                    progressorHandler.reset()
                                    if (hasAllPermissions()) {
                                        bluetoothManager.startScanning()
                                    }
                                    skippedDevice = false
                                },
                                onRecalibrate = {
                                    showSettings = false
                                    progressorHandler.recalibrate()
                                    webViewBridge.refreshButtonState()
                                },
                                onViewLogs = {
                                    showSettings = false
                                    showLogViewer = true
                                },
                                simulatedForceActive = simulatedForceActive,
                                onPreviewTargetFeedback = { action, spokenDirectionsEnabled ->
                                    action.playTargetFeedbackPreview(
                                        spokenDirectionsEnabled = spokenDirectionsEnabled,
                                        playWarning = ToneGenerator::playWarningTone,
                                        playHigh = ToneGenerator::playHighTone,
                                        playLow = ToneGenerator::playLowTone,
                                        playOnTarget = ToneGenerator::playOnTargetTone,
                                        speak = ::speakTargetDirection
                                    )
                                }
                            )
                        }
                        
                        isConnected || simulatedForceActive || skippedDevice -> {
                            MainScreen(
                                bluetoothManager = bluetoothManager,
                                progressorHandler = progressorHandler,
                                webViewBridge = webViewBridge,
                                showStatusBar = showStatusBar,
                                expandedForceBar = expandedForceBar,
                                showForceGraph = showForceGraph,
                                forceGraphWindow = forceGraphWindow,
                                useLbs = useLbs,
                                enableTargetWeight = enableTargetWeight,
                                useManualTarget = useManualTarget,
                                manualTargetWeight = manualTargetWeight,
                                weightTolerance = weightTolerance,
                                mutePhoneDuringGrip = mutePhoneDuringGrip,
                                showGripStats = showGripStats,
                                showEndOfSessionSummary = showEndOfSessionSummary,
                                simulatedForceActive = simulatedForceActive,
                                simulatedTargetWeight = debugForceDataSource.targetWeightKg.takeIf {
                                    simulatedForceActive
                                },
                                onSettingsTap = { showSettings = true },
                                onMutePhoneDuringGripToggle = {
                                    lifecycleScope.launch {
                                        preferencesRepository.setMutePhoneDuringGrip(!mutePhoneDuringGrip)
                                    }
                                },
                                onUnitToggle = {
                                    lifecycleScope.launch {
                                        preferencesRepository.setUseLbs(!useLbs)
                                    }
                                }
                            )
                        }
                        
                        else -> {
                            DeviceScannerScreen(
                                bluetoothManager = bluetoothManager,
                                onDeviceSelected = { device ->
                                    bluetoothManager.connect(device)
                                },
                                onSkipDevice = {
                                    skippedDevice = true
                                },
                                showSimulatedDataOption = debugForceDataSource.isAvailable,
                                onUseSimulatedData = {
                                    bluetoothManager.stopScanning()
                                    progressorHandler.reset()
                                    debugForceDataSource.start()
                                    skippedDevice = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Determine if we should end the session instead of failing.
     * Returns true if grip fails before the configured threshold % of target duration.
     */
    private suspend fun shouldEndSessionOnEarlyFail(): Boolean {
        val enabled = preferencesRepository.enableEndSessionOnEarlyFail.first()
        if (!enabled) return false
        
        val targetDuration = webViewBridge.targetDuration.value ?: return false
        val remainingTime = webViewBridge.remainingTime.value ?: return false
        if (targetDuration <= 0) return false
        
        val elapsedTime = targetDuration - remainingTime
        val thresholdPercent = preferencesRepository.earlyFailThresholdPercent.first()
        val thresholdSeconds = targetDuration.toDouble() * thresholdPercent
        return elapsedTime.toDouble() < thresholdSeconds
    }
    
    private fun setupEventHandlers() {
        // Grip failed -> click fail or end session button
        lifecycleScope.launch {
            progressorHandler.gripFailed.collect {
                if (debugForceDataSource.isRunning.value) return@collect

                if (shouldEndSessionOnEarlyFail()) {
                    webViewBridge.clickEndSessionButton()
                } else {
                    webViewBridge.clickFailButton()
                }
                
                val enableHaptics = preferencesRepository.enableHaptics.first()
                if (enableHaptics) {
                    hapticManager.warning()
                }
            }
        }
        
        // Calibration complete
        lifecycleScope.launch {
            progressorHandler.calibrationCompleted.collect {
                val enableHaptics = preferencesRepository.enableHaptics.first()
                if (enableHaptics) {
                    hapticManager.light()
                }
            }
        }
        
        // Target-weight feedback
        lifecycleScope.launch {
            progressorHandler.targetFeedbackEvents.collectLatest { event ->
                if (debugForceDataSource.isRunning.value) return@collectLatest

                val enableHaptics = preferencesRepository.enableHaptics.first()
                val soundSettings = TargetSoundSettings(
                    masterEnabled = preferencesRepository.enableTargetSound.first(),
                    tooHeavyEnabled = preferencesRepository.enableTooHeavySound.first(),
                    tooLightEnabled = preferencesRepository.enableTooLightSound.first(),
                    backOnTargetEnabled = preferencesRepository.enableBackOnTargetSound.first(),
                    spokenDirectionsEnabled = preferencesRepository.enableSpokenDirections.first()
                )
                
                if (enableHaptics && event is TargetFeedbackEvent.OffTarget) {
                    hapticManager.warning()
                }
                
                event.playEnabledTargetFeedback(
                    settings = soundSettings,
                    playHigh = ToneGenerator::playHighTone,
                    playLow = ToneGenerator::playLowTone,
                    playOnTarget = ToneGenerator::playOnTargetTone,
                    speak = ::speakTargetDirection
                )
            }
        }
        
        // Button state changes
        lifecycleScope.launch {
            webViewBridge.buttonEnabled
                .combine(debugForceDataSource.isRunning) { enabled, simulated -> enabled || simulated }
                .collect { progressorHandler.canEngage = it }
        }

        // Leaving the completed-session screen starts a fresh, in-memory summary.
        lifecycleScope.launch {
            webViewBridge.saveButtonVisible.drop(1).collect { visible ->
                if (!visible) progressorHandler.clearSessionRepResults()
            }
        }
        
        // Update handler with target weight from web
        lifecycleScope.launch {
            webViewBridge.targetWeight
                .combine(debugForceDataSource.isRunning) { weight, simulated -> weight to simulated }
                .collect { (weight, simulated) ->
                    if (simulated) {
                        progressorHandler.targetWeight = debugForceDataSource.targetWeightKg
                        return@collect
                    }

                    val enableTargetWeight = preferencesRepository.enableTargetWeight.first()
                    val useManualTarget = preferencesRepository.useManualTarget.first()

                    if (enableTargetWeight && !useManualTarget) {
                        progressorHandler.targetWeight = weight
                    }
                }
        }

        // Timer countdown sound for pre-start and rest timers.
        lifecycleScope.launch {
            webViewBridge.remainingTime
                .combine(webViewBridge.buttonEnabled) { remainingTime, failButtonEnabled ->
                    remainingTime to failButtonEnabled
                }
                .collect { (remainingTime, failButtonEnabled) ->
                    if (preferencesRepository.enableTimerCountdownSound.first() && !failButtonEnabled) {
                        countdownSound.onRemainingTimeChanged(remainingTime)
                    } else {
                        countdownSound.onRemainingTimeChanged(null)
                    }
                }
        }

        lifecycleScope.launch {
            progressorHandler.state
                .combine(preferencesRepository.mutePhoneDuringGrip) { state, mutePhoneDuringGrip ->
                    (state is ProgressorState.Gripping) to mutePhoneDuringGrip
                }
                .collect { (isGripActive, mutePhoneDuringGrip) ->
                    gripPeriodVolumeMuter.onGripActiveChanged(
                        isGripActive = isGripActive,
                        enabled = mutePhoneDuringGrip
                    )
                }
        }
    }
    
    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun speakStatus(text: String) {
        speak(text, "connection-status-$text")
    }

    private fun speakTargetDirection(text: String): Boolean {
        return speak(
            text = text,
            utteranceId = "target-direction",
            audioStream = GripAudioRouting.TARGET_GUIDANCE_STREAM
        )
    }

    private fun speak(text: String, utteranceId: String, audioStream: Int? = null): Boolean {
        if (!textToSpeechReady) return false

        val params = audioStream?.let { stream ->
            Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, stream)
            }
        }
        return textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            utteranceId
        ) == TextToSpeech.SUCCESS
    }

    override fun onStart() {
        super.onStart()
        if (::bluetoothManager.isInitialized) {
            backgroundInactivityShutdownTimer.onEnteredForeground()
            if (
                !debugForceDataSource.isRunning.value &&
                hasAllPermissions() &&
                bluetoothManager.connectionState.value == ConnectionState.Disconnected
            ) {
                bluetoothManager.startScanning()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (::bluetoothManager.isInitialized) {
            backgroundInactivityShutdownTimer.onEnteredBackground()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        backgroundInactivityShutdownTimer.onEnteredForeground()
        if (::gripPeriodVolumeMuter.isInitialized) {
            gripPeriodVolumeMuter.restoreIfNeeded()
        }
        if (::debugForceDataSource.isInitialized) {
            debugForceDataSource.stop()
        }
        bluetoothManager.disconnect()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
