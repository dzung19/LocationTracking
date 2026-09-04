package com.daumo.ads

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

class ManifestUpdater private constructor(
    private val context: Context
) {
    private val TAG = "ADS_DEBUG"
    
    companion object {
        @Volatile
        private var INSTANCE: ManifestUpdater? = null
        
        fun getInstance(context: Context): ManifestUpdater {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ManifestUpdater(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Fallback application ID from manifest
        private const val FALLBACK_APPLICATION_ID = ""
    }
    
    /**
     * Lấy Application ID từ manifest hoặc fallback
     * Đây là giá trị được set trong AndroidManifest.xml
     */
    fun getApplicationId(): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            val applicationId = appInfo.metaData?.getString("com.google.android.gms.ads.APPLICATION_ID")
            
            if (!applicationId.isNullOrEmpty()) {
                applicationId
            } else {
                Log.w(TAG, "Application ID not found in manifest, using fallback")
                FALLBACK_APPLICATION_ID
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading Application ID from manifest", e)
            FALLBACK_APPLICATION_ID
        }
    }
    
    /**
     * Kiểm tra xem Application ID có hợp lệ không
     */
    fun isValidApplicationId(applicationId: String): Boolean {
        return applicationId.startsWith("ca-app-pub-") && applicationId.contains("~")
    }
    
    /**
     * Lấy Application ID từ manifest
     */
    fun getEffectiveApplicationId(currentConfig: AdConfigSet?): String {
        // Use manifest ID since Firebase config no longer provides applicationId
        val manifestId = getApplicationId()
        if (isValidApplicationId(manifestId)) {
            return manifestId
        }
        
        // Final fallback
        return FALLBACK_APPLICATION_ID
    }
    
    /**
     * Kiểm tra Application ID và log thông tin override
     * Note: Firebase ID sẽ override manifest ID qua RequestConfiguration
     */
    fun checkApplicationId(newApplicationId: String): Boolean {
        return try {
            if (!isValidApplicationId(newApplicationId)) {
                Log.e(TAG, "Invalid application ID: $newApplicationId")
                return false
            }
            
            val manifestId = getApplicationId()
            Log.i(TAG, "📋 Manifest Application ID (fallback): $manifestId")
            Log.i(TAG, "🔥 Firebase Config Application ID (will override): $newApplicationId")
            
            if (manifestId == newApplicationId) {
                Log.i(TAG, "✅ Application IDs match - no override needed: $newApplicationId")
                return true
            } else {
                Log.i(TAG, "🔄 Firebase Override Active!")
                Log.i(TAG, "   Manifest ID: $manifestId (fallback)")
                Log.i(TAG, "   Firebase ID: $newApplicationId (active override)")
                Log.i(TAG, "   RequestConfiguration will override manifest ID")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Application ID", e)
            false
        }
    }
    
    /**
     * Log thông tin về Application ID đang được sử dụng
     */
    fun logApplicationIdInfo(currentConfig: AdConfigSet?) {
        val effectiveId = getEffectiveApplicationId(currentConfig)
        val manifestId = getApplicationId()
        
        Log.i(TAG, "=== Application ID Info ===")
        Log.i(TAG, "Manifest ID: $manifestId")
        Log.i(TAG, "Effective ID: $effectiveId")
        
        val source = when {
            effectiveId == manifestId -> "Manifest (AndroidManifest.xml)"
            else -> "Fallback (Hardcoded)"
        }
        
        Log.i(TAG, "Source: $source")
        Log.i(TAG, "========================")
    }
}
