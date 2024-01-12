package com.outsystems.experts.arcgis

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arcgismaps.ApiKey
import com.arcgismaps.ArcGISEnvironment
import com.arcgismaps.Color
import com.arcgismaps.geometry.Envelope
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.PortalItem
import com.arcgismaps.mapping.symbology.SimpleLineSymbol
import com.arcgismaps.mapping.symbology.SimpleLineSymbolStyle
import com.arcgismaps.mapping.view.Graphic
import com.arcgismaps.mapping.view.GraphicsOverlay
import com.arcgismaps.mapping.view.MapView
import com.arcgismaps.mapping.view.ScreenCoordinate
import com.arcgismaps.portal.Portal
import com.arcgismaps.tasks.offlinemaptask.GenerateOfflineMapJob
import com.arcgismaps.tasks.offlinemaptask.GenerateOfflineMapParameters
import com.arcgismaps.tasks.offlinemaptask.OfflineMapTask
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File

class DownloadMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var takeMapOfflineButton: Button
    private lateinit var resetButton: Button
    private lateinit var ivBack: ImageView

    // create a symbol to show a box around the extent we want to download
    private val downloadArea: Graphic = Graphic().apply {
        symbol = SimpleLineSymbol(SimpleLineSymbolStyle.Solid, Color.red, 1F)
    }

    // keep the instance graphic overlay to add graphics on the map
    private var graphicsOverlay: GraphicsOverlay = GraphicsOverlay()

    @SuppressLint("DiscouragedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layoutResourceId = getResourceId("activity_download_map", "layout")
        setContentView(layoutResourceId)
        setupViews()

        @SuppressLint("DiscouragedApi")
        val apiKey = getString(resources.getIdentifier("app_api_key", "string", packageName))

        ArcGISEnvironment.apiKey = ApiKey.create(apiKey)
        ArcGISEnvironment.applicationContext = applicationContext
        lifecycle.addObserver(mapView)

        setUpMapView()
        setupListeners()
    }

    @SuppressLint("DiscouragedApi")
    private fun setUpMapView() {
        // create a portal item with the itemId of the web map
        val url = getString(resources.getIdentifier("app_portal_item_url", "string", packageName))
        val portal = Portal(url)
        
        val itemId = getString(resources.getIdentifier("app_portal_item_id", "string", packageName))
        val portalItem = PortalItem(portal, itemId)

        // clear graphic overlays
        graphicsOverlay.graphics.clear()
        mapView.graphicsOverlays.clear()

        // add the download graphic to the graphics overlay
        graphicsOverlay.graphics.add(downloadArea)
        val map = ArcGISMap(portalItem)
        lifecycleScope.launch {
            map.load()
                .onFailure {
                    showMessage(it.message.toString())
                }
                .onSuccess {
                    // limit the map scale to the largest layer scale
                    if (map.operationalLayers.isNotEmpty()) {
                        map.maxScale = map.operationalLayers[6].maxScale ?: 0.0
                        map.minScale = map.operationalLayers[6].minScale ?: 0.0
                    }
                    // set the map to the map view
                    mapView.map = map
                    // add the graphics overlay to the map view when it is created
                    mapView.graphicsOverlays.add(graphicsOverlay)
                }
        }

        lifecycleScope.launch {
            mapView.viewpointChanged.collect {
                // upper left corner of the area to take offline
                val minScreenPoint = ScreenCoordinate(200.0, 200.0)
                // lower right corner of the downloaded area
                val maxScreenPoint = ScreenCoordinate(
                    mapView.width - 200.0,
                    mapView.height - 200.0
                )
                // convert screen points to map points
                val minPoint = mapView.screenToLocation(minScreenPoint)
                val maxPoint = mapView.screenToLocation(maxScreenPoint)
                // use the points to define and return an envelope
                if (minPoint != null && maxPoint != null) {
                    val envelope = Envelope(minPoint, maxPoint)
                    downloadArea.geometry = envelope
                }
            }
        }
    }

    private fun setupListeners() {
        takeMapOfflineButton.setOnClickListener {
            createOfflineMapJob()
        }

        ivBack.setOnClickListener {
            finish()
        }

        resetButton.setOnClickListener {
            takeMapOfflineButton.isEnabled = true
            resetButton.isEnabled = false
            // set up the portal item to take offline
            setUpMapView()
        }
    }

    private fun createOfflineMapJob() {
        // offline map path
        val offlineMapPath = getExternalFilesDir(null)?.path + "/offlineMap"
        // delete any offline map already in the cache
        File(offlineMapPath).deleteRecursively()
        // specify the extent, min scale, and max scale as parameters
        var minScale: Double = mapView.mapScale.value
        val maxScale: Double = mapView.map?.maxScale ?: 0.0
        // minScale must always be larger than maxScale
        if (minScale <= maxScale) {
            minScale = maxScale + 1
        }
        // get the geometry of the downloadArea
        val geometry = downloadArea.geometry
        if (geometry == null) {
            showMessage("Could not get geometry of the downloadArea")
            return
        }
        // set the offline map parameters
        val generateOfflineMapParameters = GenerateOfflineMapParameters(
            geometry, minScale, maxScale
        ).apply {
            // set job to cancel on any errors
            continueOnErrors = false
        }
        // get the map from the MapView
        val map = mapView.map
        if (map == null) {
            showMessage("Could not get map from MapView")
            return
        }
        // create an offline map task with the map
        val offlineMapTask = OfflineMapTask(map)
        // create an offline map job with the download directory path and parameters and start the job
        val offlineMapJob = offlineMapTask.createGenerateOfflineMapJob(
            generateOfflineMapParameters,
            offlineMapPath
        )
        // create an alert dialog to show the download progress
        val progressDialog = createProgressDialog(offlineMapJob)
        // handle offline job loading, error and succeed status
        lifecycleScope.launch {
            progressDialog.show()
            displayOfflineMapFromJob(offlineMapJob, progressDialog)
        }
    }

    private suspend fun displayOfflineMapFromJob(
        offlineMapJob: GenerateOfflineMapJob,
        progressDialog: AlertDialog
    ) {

        // create a flow-collector for the job's progress
        lifecycleScope.launch {
            offlineMapJob.progress.collect {
                // display the current job's progress value
                val progressPercentage = offlineMapJob.progress.value
                Log.v("TAG", ">>>> Percentage: $progressPercentage")
                progressDialog.setMessage("Downloading...$progressPercentage%")
              //  progressDialogLayout.progressBar.progress = progressPercentage
                // progressDialogLayout.progressTextView.text = "$progressPercentage%"
            }
        }

        // start the job
        offlineMapJob.start()
        offlineMapJob.result().onSuccess {
            mapView.map = it.offlineMap
            graphicsOverlay.graphics.clear()
            // disable the button to take the map offline once the offline map is showing
            takeMapOfflineButton.isEnabled = false
            resetButton.isEnabled = true

            showMessage("Map saved at: " + offlineMapJob.downloadDirectoryPath)


            // close the progress dialog
            progressDialog.dismiss()
        }.onFailure {
            progressDialog.dismiss()
            showMessage(it.message.toString())
        }
    }

    private fun createProgressDialog(job: GenerateOfflineMapJob): AlertDialog {
        val builder = AlertDialog.Builder(this).apply {
            setTitle("Generating offline map...")
            setMessage("Please wait...")
            setCancelable(false)
            setNegativeButton("Cancel") { _, _ ->
                lifecycleScope.launch { job.cancel() }
            }
        }
        return builder.create()
    }

    private fun setupViews() {
        mapView = findViewById(getViewId("mapView"))
        takeMapOfflineButton = findViewById(getViewId("takeMapOfflineButton"))
        resetButton = findViewById(getViewId("resetButton"))
        ivBack = findViewById(getViewId("ivBack"))
    }

    @SuppressLint("DiscouragedApi")
    private fun getResourceId(resourceName: String, resourceType: String): Int {
        return resources.getIdentifier(resourceName, resourceType, packageName)
    }

    private fun getViewId(viewName: String): Int {
        return getResourceId(viewName, "id")
    }

    private fun showMessage(message: String) {
        Log.e(localClassName, message)
        Snackbar.make(mapView, message, Snackbar.LENGTH_SHORT).show()
    }
}