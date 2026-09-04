package com.daumo.ads

object AdsLoggerConfig {
    // This should be set from the app module during initialization
    var isDebugMode: Boolean = true
        private set
    
    fun setDebugMode(debug: Boolean) {
        isDebugMode = debug
    }
}
