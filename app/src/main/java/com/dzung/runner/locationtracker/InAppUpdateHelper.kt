package com.dzung.runner.locationtracker

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Helper class managing Google Play In-App Updates (Flexible and Immediate).
 * Monitors update availability, downloads updates in background, and prompts user restart on completion.
 */
class InAppUpdateHelper(private val activity: ComponentActivity) {

    companion object {
        private const val TAG = "InAppUpdateHelper"
    }

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)

    private val _isUpdateDownloaded = MutableStateFlow(false)
    val isUpdateDownloaded: StateFlow<Boolean> = _isUpdateDownloaded.asStateFlow()

    private val updateLauncher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                Log.w(TAG, "In-app update flow cancelled or failed! Result code: ${result.resultCode}")
            }
        }

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            Log.d(TAG, "In-app update download completed")
            _isUpdateDownloaded.value = true
        }
    }

    init {
        appUpdateManager.registerListener(installStateUpdatedListener)
    }

    /**
     * Checks if a new update is available on Google Play and triggers the update flow.
     */
    fun checkForUpdate(updateType: Int = AppUpdateType.FLEXIBLE) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(updateType)
            ) {
                requestUpdate(appUpdateInfo, updateType)
            } else if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                _isUpdateDownloaded.value = true
            }
        }.addOnFailureListener { e ->
            Log.w(TAG, "Failed to check for Google Play updates", e)
        }
    }

    private fun requestUpdate(appUpdateInfo: AppUpdateInfo, updateType: Int) {
        try {
            val options = AppUpdateOptions.newBuilder(updateType).build()
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                updateLauncher,
                options
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error launching in-app update flow", e)
        }
    }

    /**
     * Restarts the application and installs the downloaded update.
     */
    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    /**
     * Call in Activity.onResume() to check if a download finished in background or resume in-progress update.
     */
    fun onResume() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                _isUpdateDownloaded.value = true
            } else if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                requestUpdate(appUpdateInfo, AppUpdateType.IMMEDIATE)
            }
        }.addOnFailureListener { e ->
            Log.w(TAG, "Error checking update status in onResume", e)
        }
    }

    /**
     * Call in Activity.onDestroy() to unregister listeners.
     */
    fun onDestroy() {
        try {
            appUpdateManager.unregisterListener(installStateUpdatedListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering InstallStateUpdatedListener", e)
        }
    }
}
