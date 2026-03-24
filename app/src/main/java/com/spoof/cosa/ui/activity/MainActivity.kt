package com.spoof.cosa.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spoof.cosa.BuildConfig
import com.spoof.cosa.R
import com.spoof.cosa.common.SpoofConfig
import com.spoof.cosa.data.CosaMaintenanceActions
import com.spoof.cosa.data.SpoofSettings
import com.spoof.cosa.data.XposedServiceBridge
import io.github.libxposed.service.XposedService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private var spoofSettings: SpoofSettings? = null
    private val maintenanceActions = CosaMaintenanceActions()
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var serviceStatusState by mutableStateOf(ServiceStatus.WAITING)
    private var frameworkInfoState by mutableStateOf("")
    private var currentFakeValueState by mutableStateOf(SpoofConfig.defaultFakePrjname)
    private var inputValueState by mutableStateOf(SpoofConfig.defaultFakePrjname)
    private var lastSyncedFakeValueState by mutableStateOf(SpoofConfig.defaultFakePrjname)
    private var isApplyingState by mutableStateOf(false)
    private var showConfirmDialogState by mutableStateOf(false)

    private val serviceListener = object : XposedServiceBridge.Listener {
        override fun onServiceChanged(service: XposedService?) {
            runOnUiThread { bindServiceState(service) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        frameworkInfoState = getString(R.string.service_status_waiting_desc)

        setContent {
            val isDark = isSystemInDarkTheme()
            val appColors = remember(isDark) { if (isDark) AppColors.Dark else AppColors.Light }
            AppTheme(appColors) {
                MainScreen(appColors)

                if (showConfirmDialogState) {
                    ApplyConfirmDialog(
                        colors = appColors,
                        onDismiss = { showConfirmDialogState = false },
                        onConfirm = {
                            showConfirmDialogState = false
                            applySavedValueAndReboot()
                        }
                    )
                }
            }
        }

        XposedServiceBridge.addListener(serviceListener)
    }

    override fun onDestroy() {
        XposedServiceBridge.removeListener(serviceListener)
        backgroundExecutor.shutdown()
        super.onDestroy()
    }

    private fun bindServiceState(service: XposedService?) {
        if (service == null) {
            spoofSettings = null
            serviceStatusState = ServiceStatus.WAITING
            frameworkInfoState = getString(R.string.service_status_waiting_desc)
            currentFakeValueState = SpoofConfig.defaultFakePrjname
            return
        }

        runCatching {
            val settings = SpoofSettings(service.getRemotePreferences(SpoofConfig.remotePrefsGroup))
            spoofSettings = settings
            serviceStatusState = ServiceStatus.CONNECTED
            frameworkInfoState = getString(
                R.string.framework_info,
                service.frameworkName,
                service.frameworkVersion,
                service.apiVersion
            )
            val currentValue = settings.getFakePrjname()
            currentFakeValueState = currentValue ?: SpoofConfig.defaultFakePrjname
            if (inputValueState.isBlank() || inputValueState == lastSyncedFakeValueState) {
                inputValueState = currentFakeValueState
            }
            lastSyncedFakeValueState = currentFakeValueState
        }.onFailure {
            spoofSettings = null
            serviceStatusState = ServiceStatus.ERROR
            frameworkInfoState = it.message ?: it.javaClass.simpleName
            currentFakeValueState = SpoofConfig.defaultFakePrjname
        }
    }

    private fun saveFakePrjname(newValue: String) {
        if (isApplyingState) return

        val settings = spoofSettings
        if (settings == null) {
            Toast.makeText(this, R.string.service_unavailable_tip, Toast.LENGTH_SHORT).show()
            return
        }

        val success = settings.setFakePrjname(newValue)
        if (!success) {
            Toast.makeText(this, R.string.spoof_prjname_save_failed_tip, Toast.LENGTH_SHORT).show()
            return
        }

        currentFakeValueState = settings.getFakePrjname() ?: SpoofConfig.defaultFakePrjname
        inputValueState = currentFakeValueState
        lastSyncedFakeValueState = currentFakeValueState
        showApplyConfirmationDialog()
    }

    private fun showApplyConfirmationDialog() {
        showConfirmDialogState = true
    }

    private fun applySavedValueAndReboot() {
        isApplyingState = true
        Toast.makeText(this, R.string.spoof_prjname_saved_tip, Toast.LENGTH_SHORT).show()

        backgroundExecutor.execute {
            when (val result = maintenanceActions.applySavedValueAndReboot()) {
                CosaMaintenanceActions.ApplyResult.Success -> {
                    runOnUiThread {
                        Toast.makeText(this, R.string.apply_rebooting_tip, Toast.LENGTH_LONG).show()
                    }
                }
                is CosaMaintenanceActions.ApplyResult.Failure -> {
                    runOnUiThread {
                        isApplyingState = false
                        Toast.makeText(
                            this,
                            getString(R.string.apply_failed_tip, result.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    @Composable
    fun AppTheme(colors: AppColors, content: @Composable () -> Unit) {
        // Pure solid background - no gradients, no glass, no transparency shapes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            content()
        }
    }

    @Composable
    fun MainScreen(colors: AppColors) {
        val scrollState = rememberScrollState()
        val focusManager = LocalFocusManager.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { focusManager.clearFocus() }
                .systemBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Pure text header - solid font color
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.app_subtitle),
                fontSize = 15.sp,
                color = colors.textSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            StatusSection(colors)
            Spacer(modifier = Modifier.height(20.dp))
            SettingsSection(
                colors = colors,
                inputValue = inputValueState,
                onInputValueChange = { newValue ->
                    inputValueState = newValue
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    saveFakePrjname(inputValueState)
                }
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    @Composable
    fun StatusSection(colors: AppColors) {
        SolidCard(colors) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.status_section_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    ContainerLabel(text = "v${BuildConfig.VERSION_NAME}", colors = colors)
                }

                Spacer(modifier = Modifier.height(24.dp))

                InfoRow(
                    title = stringResource(R.string.service_status_label),
                    colors = colors,
                    content = {
                        val statusColor = when (serviceStatusState) {
                            ServiceStatus.WAITING -> colors.textSecondary
                            ServiceStatus.CONNECTED -> colors.success
                            ServiceStatus.ERROR -> colors.error
                        }
                        val statusIcon = when (serviceStatusState) {
                            ServiceStatus.WAITING -> Icons.Rounded.Info
                            ServiceStatus.CONNECTED -> Icons.Rounded.CheckCircle
                            ServiceStatus.ERROR -> Icons.Rounded.Warning
                        }
                        val statusText = when (serviceStatusState) {
                            ServiceStatus.WAITING -> stringResource(R.string.service_status_waiting)
                            ServiceStatus.CONNECTED -> stringResource(R.string.service_status_connected)
                            ServiceStatus.ERROR -> stringResource(R.string.service_status_error)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = statusColor
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                Spacer(modifier = Modifier.height(16.dp))

                InfoRow(
                    title = stringResource(R.string.framework_info_label),
                    colors = colors,
                    value = frameworkInfoState
                )

                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                Spacer(modifier = Modifier.height(16.dp))

                InfoRow(
                    title = stringResource(R.string.current_value_label),
                    colors = colors,
                    value = currentFakeValueState,
                    isValueHighlighted = true
                )
            }
        }
    }

    @Composable
    fun SettingsSection(
        colors: AppColors,
                inputValue: String,
                onInputValueChange: (String) -> Unit,
                onSaveClicked: () -> Unit
    ) {
        val isServiceConnected = serviceStatusState == ServiceStatus.CONNECTED

        SolidCard(colors) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.spoof_prjname_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.spoof_prjname_default, SpoofConfig.defaultFakePrjname),
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Solid color input field - no gradients
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
                        .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputValue.isEmpty()) {
                        Text(
                            text = stringResource(R.string.spoof_prjname_title),
                            color = colors.textTertiary,
                            fontSize = 16.sp
                        )
                    }
                    BasicTextField(
                        value = inputValue,
                        onValueChange = onInputValueChange,
                        textStyle = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = isServiceConnected && !isApplyingState,
                        modifier = Modifier.fillMaxWidth(),
                        cursorBrush = SolidColor(colors.accent)
                    )
                }

                AnimatedVisibility(visible = !isServiceConnected) {
                    Text(
                        text = stringResource(R.string.service_unavailable_tip),
                        color = colors.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Solid action button - NO GRADIANT
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isServiceConnected && !isApplyingState) colors.accent else colors.disabled)
                        .clickable(
                            enabled = isServiceConnected && !isApplyingState,
                            onClick = onSaveClicked
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isApplyingState) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.spoof_prjname_save_and_apply),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.status_tip),
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    @Composable
    fun SolidCard(colors: AppColors, content: @Composable () -> Unit) {
        // Plain solid rectangle with moderate corners, clear border, NO SHADOW, NO BLUR
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.divider, RoundedCornerShape(16.dp))
        ) {
            content()
        }
    }

    @Composable
    fun ContainerLabel(text: String, colors: AppColors) {
        // Solid light-colored box - no alpha channels
        Box(
            modifier = Modifier
                .background(colors.accentSurface, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent
            )
        }
    }

    @Composable
    fun InfoRow(
        title: String,
        colors: AppColors,
        value: String = "",
        isValueHighlighted: Boolean = false,
        content: (@Composable () -> Unit)? = null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = colors.textSecondary,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (content != null) {
                content()
            } else {
                Text(
                    text = value,
                    fontSize = if (isValueHighlighted) 17.sp else 15.sp,
                    fontWeight = if (isValueHighlighted) FontWeight.SemiBold else FontWeight.Medium,
                    color = colors.textPrimary,
                    lineHeight = 22.sp
                )
            }
        }
    }

    @Composable
    fun ApplyConfirmDialog(colors: AppColors, onDismiss: () -> Unit, onConfirm: () -> Unit) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.divider, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.apply_confirm_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.apply_confirm_message),
                        fontSize = 15.sp,
                        color = colors.textSecondary,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = stringResource(android.R.string.cancel),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accent)
                                .clickable(onClick = onConfirm)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.apply_confirm_positive),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    enum class ServiceStatus {
        WAITING, CONNECTED, ERROR
    }

    // Flat Solid Color Palette - No Glass, No Transparent UI Elements
    class AppColors(
        val background: Color,
        val surface: Color,
        val surfaceVariant: Color,
        val textPrimary: Color,
        val textSecondary: Color,
        val textTertiary: Color,
        val divider: Color,
        val accent: Color,
        val accentSurface: Color,
        val success: Color,
        val error: Color,
        val disabled: Color
    ) {
        companion object {
            val Light = AppColors(
                background = Color(0xFFF2F2F7),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFF9F9F9),
                textPrimary = Color(0xFF000000),
                textSecondary = Color(0xFF666666),
                textTertiary = Color(0xFF999999),
                divider = Color(0xFFE5E5EA),
                accent = Color(0xFF007AFF),
                accentSurface = Color(0xFFE5F1FF), // Solid light blue
                success = Color(0xFF34C759),
                error = Color(0xFFFF3B30),
                disabled = Color(0xFFD1D1D6)
            )

            val Dark = AppColors(
                background = Color(0xFF000000),
                surface = Color(0xFF1C1C1E),
                surfaceVariant = Color(0xFF2C2C2E),
                textPrimary = Color(0xFFFFFFFF),
                textSecondary = Color(0xFFEBEBF5),
                textTertiary = Color(0xFF8E8E93),
                divider = Color(0xFF38383A),
                accent = Color(0xFF0A84FF),
                accentSurface = Color(0xFF002E66), // Solid dark blue
                success = Color(0xFF32D74B),
                error = Color(0xFFFF453A),
                disabled = Color(0xFF3A3A3C)
            )
        }
    }
}
