package com.outsystems.experts.arcgis

import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaInterface
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.CordovaWebView
import org.json.JSONArray

private const val ACTION_OPEN_MAPS = "openMaps"

class ArcGISPlugin : CordovaPlugin() {

    override fun initialize(cordova: CordovaInterface?, webView: CordovaWebView?) {
        super.initialize(cordova, webView)
    }

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        if (action == ACTION_OPEN_MAPS) {
            prepareShowMaps(callbackContext)
            return true
        }
        return false
    }

    private fun prepareShowMaps(callbackContext: CallbackContext) {
        try {
            callbackContext.success()
        } catch (ex: Exception) {
            callbackContext.error(ex.message)
        }
    }
}