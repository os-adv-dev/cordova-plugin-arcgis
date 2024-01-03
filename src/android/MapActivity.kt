package com.outsystems.experts.arcgis

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arcgismaps.ApiKey
import com.arcgismaps.ArcGISEnvironment
import com.arcgismaps.location.LocationDisplayAutoPanMode
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.BasemapStyle
import com.arcgismaps.mapping.view.MapView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView

    @SuppressLint("DiscouragedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layoutResourceId = getResourceId("activity_map", "layout")
        setContentView(layoutResourceId)
        mapView = findViewById(getViewId("mapView"))

        @SuppressLint("DiscouragedApi")
        val apiKey = getString(resources.getIdentifier("app_api_key", "string", packageName))
        
        ArcGISEnvironment.apiKey = ApiKey.create(apiKey)
        ArcGISEnvironment.applicationContext = applicationContext

        lifecycle.addObserver(mapView)

        // create and add a map with a navigation night basemap style
        val map = ArcGISMap(BasemapStyle.ArcGISNavigationNight)
        mapView.map = map

        // Set up the location display
        val locationDisplay = mapView.locationDisplay

        lifecycleScope.launch {
            // listen to changes in the status of the location data source
            locationDisplay.dataSource.start()
                .onSuccess {
                    zoomToUserLocation()
                }.onFailure {
                    // check permissions to see if failure may be due to lack of permissions
                    requestPermissions()
                }
        }
    }

    /**
     * Request fine and coarse location permissions for API level 23+.
     */
    private fun requestPermissions() {
        val permissionCheckCoarseLocation = ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val permissionCheckFineLocation = ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!(permissionCheckCoarseLocation && permissionCheckFineLocation)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION),
                REQUEST_CODE_PERMISSION
            )
        } else {
            // permission already granted, so start the location display
            lifecycleScope.launch {
                mapView.locationDisplay.dataSource.start().onSuccess {
                    zoomToUserLocation()
                   // activityMainBinding.spinner.setSelection(1, true)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch {
                mapView.locationDisplay.dataSource.start().onSuccess {
                    zoomToUserLocation()
                  //  activityMainBinding.spinner.setSelection(1, true)
                }
            }
        } else {
            Snackbar.make(
                mapView,
                "Location permissions required to run this sample!",
                Snackbar.LENGTH_LONG
            ).show()
            // update UI to reflect that the location display did not actually start
           // activityMainBinding.spinner.setSelection(0, true)
        }
    }

    private fun zoomToUserLocation() {
        if (this::mapView.isInitialized) {
            Log.v("TAG", "✅ -- Setting the user LocationDisplayAutoPanMode.Recenter")
            mapView.locationDisplay.setAutoPanMode(LocationDisplayAutoPanMode.Recenter)
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun getResourceId(resourceName: String, resourceType: String): Int {
        return resources.getIdentifier(resourceName, resourceType, packageName)
    }

    private fun getViewId(viewName: String): Int {
        return getResourceId(viewName, "id")
    }

    companion object {
        private const val REQUEST_CODE_PERMISSION = 2
    }
}