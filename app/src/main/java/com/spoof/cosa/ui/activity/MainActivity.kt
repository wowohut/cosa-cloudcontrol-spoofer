package com.spoof.cosa.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.spoof.cosa.BuildConfig
import com.spoof.cosa.R
import com.spoof.cosa.common.SpoofConfig
import com.spoof.cosa.data.CosaMaintenanceActions
import com.spoof.cosa.data.SpoofSettings
import com.spoof.cosa.data.XposedServiceBridge
import com.spoof.cosa.databinding.ActivityMainBinding
import io.github.libxposed.service.XposedService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var spoofSettings: SpoofSettings? = null
    private val maintenanceActions = CosaMaintenanceActions()
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var isApplying = false

    private val serviceListener = object : XposedServiceBridge.Listener {
        override fun onServiceChanged(service: XposedService?) {
            runOnUiThread { bindServiceState(service) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.versionValue.text = getString(R.string.module_version, BuildConfig.VERSION_NAME)
        binding.defaultValueText.text = getString(R.string.spoof_prjname_default, SpoofConfig.defaultFakePrjname)
        binding.fakePrjnameInput.setText(SpoofConfig.defaultFakePrjname)
        binding.saveButton.setOnClickListener { saveFakePrjname() }

        renderDisconnectedState()
        XposedServiceBridge.addListener(serviceListener)
    }

    override fun onDestroy() {
        XposedServiceBridge.removeListener(serviceListener)
        backgroundExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun bindServiceState(service: XposedService?) {
        if (service == null) {
            spoofSettings = null
            renderDisconnectedState()
            return
        }

        runCatching {
            val settings = SpoofSettings(service.getRemotePreferences(SpoofConfig.remotePrefsGroup))
            spoofSettings = settings
            renderConnectedState(service, settings)
        }.onFailure {
            spoofSettings = null
            renderServiceError(it)
        }
    }

    private fun renderDisconnectedState() {
        binding.serviceStatusValue.text = getString(R.string.service_status_waiting)
        binding.frameworkInfoValue.text = getString(R.string.service_status_waiting_desc)
        binding.currentValueValue.text = getString(
            R.string.status_current_fake_prjname,
            SpoofConfig.defaultFakePrjname
        )
        binding.saveButton.text = getString(R.string.spoof_prjname_save_and_apply)
        binding.saveButton.isEnabled = false
        binding.fakePrjnameInput.isEnabled = false
        binding.serviceHintText.isVisible = true
    }

    private fun renderConnectedState(service: XposedService, settings: SpoofSettings) {
        val currentValue = settings.getFakePrjname()
        binding.serviceStatusValue.text = getString(R.string.service_status_connected)
        binding.frameworkInfoValue.text = getString(
            R.string.framework_info,
            service.frameworkName,
            service.frameworkVersion,
            service.apiVersion
        )
        binding.currentValueValue.text = getString(R.string.status_current_fake_prjname, currentValue)
        binding.fakePrjnameInput.setText(currentValue)
        binding.fakePrjnameInput.isEnabled = !isApplying
        binding.saveButton.text = getString(
            if (isApplying) R.string.spoof_prjname_applying else R.string.spoof_prjname_save_and_apply
        )
        binding.saveButton.isEnabled = !isApplying
        binding.serviceHintText.isVisible = false
    }

    private fun renderServiceError(error: Throwable) {
        binding.serviceStatusValue.text = getString(R.string.service_status_error)
        binding.frameworkInfoValue.text = error.message ?: error.javaClass.simpleName
        binding.currentValueValue.text = getString(
            R.string.status_current_fake_prjname,
            SpoofConfig.defaultFakePrjname
        )
        binding.saveButton.text = getString(R.string.spoof_prjname_save_and_apply)
        binding.saveButton.isEnabled = false
        binding.fakePrjnameInput.isEnabled = false
        binding.serviceHintText.isVisible = true
    }

    private fun saveFakePrjname() {
        if (isApplying) return

        val settings = spoofSettings
        if (settings == null) {
            Toast.makeText(this, R.string.service_unavailable_tip, Toast.LENGTH_SHORT).show()
            return
        }

        val success = settings.setFakePrjname(binding.fakePrjnameInput.text?.toString())
        if (!success) {
            Toast.makeText(this, R.string.spoof_prjname_save_failed_tip, Toast.LENGTH_SHORT).show()
            return
        }

        val currentValue = settings.getFakePrjname()
        binding.fakePrjnameInput.setText(currentValue)
        binding.currentValueValue.text = getString(R.string.status_current_fake_prjname, currentValue)
        showApplyConfirmationDialog()
    }

    private fun showApplyConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.apply_confirm_title)
            .setMessage(R.string.apply_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.apply_confirm_positive) { _, _ ->
                applySavedValueAndReboot()
            }
            .show()
    }

    private fun applySavedValueAndReboot() {
        isApplying = true
        setApplyingUiState(true)
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
                        isApplying = false
                        setApplyingUiState(false)
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

    private fun setApplyingUiState(applying: Boolean) {
        binding.fakePrjnameInput.isEnabled = !applying
        binding.saveButton.isEnabled = !applying && spoofSettings != null
        binding.saveButton.text = getString(
            if (applying) R.string.spoof_prjname_applying else R.string.spoof_prjname_save_and_apply
        )
    }
}
