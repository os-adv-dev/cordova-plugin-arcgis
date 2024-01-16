package com.outsystems.experts.arcgis

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.arcgismaps.ApiKey
import com.arcgismaps.ArcGISEnvironment
import com.arcgismaps.LoadStatus
import com.arcgismaps.geometry.*
import com.arcgismaps.location.LocationDisplayAutoPanMode
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.BasemapStyle
import com.arcgismaps.mapping.MobileMapPackage
import com.arcgismaps.mapping.symbology.*
import com.arcgismaps.mapping.view.Graphic
import com.arcgismaps.mapping.view.GraphicsOverlay
import com.arcgismaps.mapping.view.MapView
import com.arcgismaps.mapping.view.geometryeditor.FreehandTool
import com.arcgismaps.mapping.view.geometryeditor.GeometryEditor
import com.arcgismaps.mapping.view.geometryeditor.VertexTool
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var recenterMap: ImageView
    private lateinit var tvCancel: View
    private lateinit var ivBack: View
    private lateinit var downloadButton: ImageView
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioPolygon: RadioButton
    private lateinit var radioCircle: RadioButton
    private lateinit var radioLine: RadioButton
    private lateinit var btnUndo: Button
    private lateinit var btnSave: Button

    // create a symbol for a line graphic
    private val lineSymbol: SimpleLineSymbol by lazy {
        SimpleLineSymbol(
            SimpleLineSymbolStyle.Solid,
            com.arcgismaps.Color(Color.parseColor("#FE8700")),
            4f
        )
    }

    // create a symbol for the fill graphic
    private val fillSymbol: SimpleFillSymbol by lazy {
        SimpleFillSymbol(
            SimpleFillSymbolStyle.Cross,
            com.arcgismaps.Color(Color.parseColor("#6DFE8700")),
            lineSymbol
        )
    }

    // create a symbol for the point graphic
    private val pointSymbol: SimpleMarkerSymbol by lazy {
        SimpleMarkerSymbol(
            SimpleMarkerSymbolStyle.Square,
            com.arcgismaps.Color(Color.parseColor("#FE8700")),
            20f
        )
    }

    // file path to store the offline map package
    private val offlineMapPath by lazy {
        getExternalFilesDir(null)?.path + "/offlineMap"
    }

    // keep the instance graphic overlay to add graphics on the map
    private var graphicsOverlay: GraphicsOverlay = GraphicsOverlay()

    // keep the instance of the freehand tool
    private val freehandTool: FreehandTool = FreehandTool()

    // keep the instance of the vertex tool
    private val vertexTool: VertexTool = VertexTool()

    // keep the instance to create new geometries, and change existing geometries
    private var geometryEditor: GeometryEditor = GeometryEditor()

    private val databaseHelper: DatabaseHelper by lazy { DatabaseHelper(this) }

    @SuppressLint("DiscouragedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layoutResourceId = getResourceId("activity_map", "layout")
        setContentView(layoutResourceId)
        setupViews()

        @SuppressLint("DiscouragedApi")
        val apiKey = getString(resources.getIdentifier("app_api_key", "string", packageName))

        ArcGISEnvironment.apiKey = ApiKey.create(apiKey)
        ArcGISEnvironment.applicationContext = applicationContext

        lifecycle.addObserver(mapView)

        // Load Map online or offline
        loadMapBasedOnConnectivity()

        mapView.apply {
            graphicsOverlays.add(graphicsOverlay)
        }

        setupListeners()
    }

    private fun loadMapBasedOnConnectivity() {
        if (isNetworkAvailable()) {
            loadOnlineMap()
        } else {
            loadOfflineMap()
        }
    }

    private fun loadOnlineMap() {
        showDownloadButton(isVisible = true)

        lifecycleScope.launch {
            val map = ArcGISMap(BasemapStyle.ArcGISNavigationNight)
            mapView.map = map

            // set MapView's geometry editor to sketch on map
            mapView.geometryEditor = geometryEditor

            // Set up the location display
            val locationDisplay = mapView.locationDisplay
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

    private fun loadOfflineMap() {
        showDownloadButton(isVisible = false)
        lifecycleScope.launch {
            // check if the offline map package file exists
            if (File(offlineMapPath).exists()) {
                Log.v("TAG", ">>>> Offline Map EXISTS -->>> $offlineMapPath")

                // load it as a MobileMapPackage
                val mapPackage = MobileMapPackage(offlineMapPath)
                mapPackage.load().onFailure {
                    // if the load fails, show an error and return
                    showMessage("Error loading map package: ${it.message}")
                    return@launch
                }
                // add the map from the mobile map package to the MapView
                mapView.map = mapPackage.maps.first()
                // clear all the drawn graphics
                graphicsOverlay.graphics.clear()

                // set MapView's geometry editor to sketch on map
                mapView.geometryEditor = geometryEditor

                // Load Draw in offline map
                setupLoadedDraw()

            } else {
                Log.v("TAG", ">>>> Offline Map NO EXISTS -->>> $offlineMapPath")
            }
        }
    }

    private fun setupListeners() {
        recenterMap.setOnClickListener { zoomToUserLocation() }
        tvCancel.setOnClickListener { finish() }
        ivBack.setOnClickListener { onBackPressed() }
        downloadButton.setOnClickListener { startActivity(Intent(this, DownloadMapActivity::class.java)) }

        // Radio Polygon
        radioPolygon.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val selected: Int = getResourceId("ic_polygon_selected", "drawable")
                radioPolygon.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(this, selected),
                    null,
                    null
                )
                canStartDrawing(DRAW_POLYGON)
            } else {
                val unselected: Int = getResourceId("ic_polygon", "drawable")
                radioPolygon.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(this, unselected),
                    null,
                    null
                )
            }
        }

        // Radio Circle
        radioCircle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val selected: Int = getResourceId("ic_circle_selected", "drawable")
                radioCircle.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(this, selected),
                    null,
                    null
                )
                canStartDrawing(DRAW_CIRCLE)
            } else {
                val unselected: Int = getResourceId("ic_circle", "drawable")
                radioCircle.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(this, unselected),
                    null,
                    null
                )
            }
        }

        // Radio Line
        radioLine.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val selected: Int = getResourceId("ic_line_selected", "drawable")
                radioLine.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(this, selected),
                    null,
                    null
                )
                canStartDrawing(DRAW_LINE)
            } else {
                val unselected: Int = getResourceId("ic_line", "drawable")
                radioLine.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(this, unselected),
                    null,
                    null
                )
            }
        }

        // Undo button clicked
        btnUndo.setOnClickListener {
            // TODO display a pop-up to confirm DELETE ALL graph
            clear()

            lifecycleScope.launch {
                databaseHelper.deleteAllGraphics()
            }
        }
        // Save button clicked
        btnSave.setOnClickListener {
            if (!isAnyRadioButtonSelected(radioGroup)) {
                showMessage(getString(getResourceId("none_selected_message", "string")))
            } else {
                stopDrawingAndSave()
            }
        }

        afterMapLoaded()
    }

    private fun afterMapLoaded() {
        lifecycleScope.launch {
            mapView.map?.loadStatus?.collectLatest {
                when(it) {
                    LoadStatus.Loaded ->  {
                        setupLoadedDraw()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun setupLoadedDraw() {
        try {
            val symbolJson = databaseHelper.getAllGraphics()
            if (symbolJson.isNotEmpty()) {
                symbolJson.forEach {
                    val geometry = Geometry.fromJsonOrNull(it)

                    if (geometry != null) {
                        geometryEditor.start(geometry)
                        val graphic = Graphic(geometry).apply {
                            // assign a symbol based on geometry type
                            symbol = when (geometry) {
                                is Polygon -> fillSymbol
                                is Polyline -> lineSymbol
                                is Point, is Multipoint -> pointSymbol
                                else -> null
                            }
                        }
                        graphicsOverlay.graphics.add(graphic)
                        geometryEditor.stop()
                    }
                }
            }
        } catch (ex: Exception) {
            showMessage("Error to retrieve the geometry saved!")
        }
    }

    private fun isAnyRadioButtonSelected(radioGroup: RadioGroup): Boolean {
        return radioGroup.checkedRadioButtonId != -1
    }

    private fun canStartDrawing(drawType: Int) {
        //  disableRadioGroupWhileDrawing()

        geometryEditor.apply {
            when (drawType) {
                DRAW_LINE -> {
                    tool = vertexTool
                    start(GeometryType.Polyline)
                }
                DRAW_POLYGON -> {
                    tool = vertexTool
                    start(GeometryType.Polygon)
                }
                DRAW_CIRCLE -> {
                    tool = freehandTool
                    start(GeometryType.Polyline)
                }
            }
        }
    }

    private fun enableRadioGroupSaveRedoClicked() {
        lifecycleScope.launch {
            radioGroup.isEnabled = true
            for (i in 0 until radioGroup.childCount) {
                val child = radioGroup.getChildAt(i)
                child.isEnabled = true
            }
            radioGroup.alpha = 1.0f
        }
    }

    /**
     * Clear the MapView of all the graphics and reset selections
     */
    private fun clear() {
        graphicsOverlay.graphics.clear()
        geometryEditor.clearGeometry()
        geometryEditor.clearSelection()
        geometryEditor.stop()
        radioGroup.clearCheck()
    }

    private fun stopDrawingAndSave() {
        // get the geometry from sketch editor
        val sketchGeometry = geometryEditor.geometry.value
            ?: return showMessage("Error retrieving geometry")

        if (!GeometryBuilder.builder(sketchGeometry).isSketchValid) {
            return reportNotValid()
        }

        // stops the editing session
        geometryEditor.stop()

        // clear the UI selection
        radioGroup.clearCheck()
        // create a graphic from the sketch editor geometry
        val graphic = Graphic(sketchGeometry).apply {
            // assign a symbol based on geometry type
            symbol = when (sketchGeometry) {
                is Polygon -> fillSymbol
                is Polyline -> lineSymbol
                is Point, is Multipoint -> pointSymbol
                else -> null
            }
        }

        // add the graphic to the graphics overlay
        graphicsOverlay.graphics.add(graphic)

        // save in data helper
        val json = sketchGeometry.toJson()
        databaseHelper.saveGraphicJson(json)
    }

    private fun setupViews() {
        mapView = findViewById(getViewId("mapView"))
        recenterMap = findViewById(getViewId("recenterMap"))
        tvCancel = findViewById(getViewId("tvCancel"))
        ivBack = findViewById(getViewId("ivBack"))
        radioGroup = findViewById(getViewId("radioGroup"))
        radioPolygon = findViewById(getViewId("radioPolygon"))
        radioLine = findViewById(getViewId("radioLine"))
        radioCircle = findViewById(getViewId("radioCircle"))
        btnUndo = findViewById(getViewId("btnUndo"))
        btnSave = findViewById(getViewId("btnSave"))
        downloadButton = findViewById(getViewId("downloadButton"))
    }

    /**
     * Request fine and coarse location permissions for API level 23+.
     */
    private fun requestPermissions() {
        val permissionCheckCoarseLocation = ContextCompat.checkSelfPermission(
            this,
            ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val permissionCheckFineLocation = ContextCompat.checkSelfPermission(
            this,
            ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!(permissionCheckCoarseLocation && permissionCheckFineLocation)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION),
                REQUEST_CODE_PERMISSION
            )
        } else {
            lifecycleScope.launch {
                mapView.locationDisplay.dataSource.start().onSuccess {
                    zoomToUserLocation()
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
                }
            }
        } else {
            Snackbar.make(
                mapView,
                "Location permissions required to run this sample!",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun zoomToUserLocation() {
        if (this::mapView.isInitialized) {
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

    private fun showMessage(message: String) {
        Log.e(localClassName, message)
        Snackbar.make(mapView, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showDownloadButton(isVisible: Boolean) {
        downloadButton.isVisible = isVisible
    }

    private fun reportNotValid() {
        // get the geometry currently being added to map
        val geometry = geometryEditor.geometry.value ?: return showMessage("Geometry not found")
        val validIfText: String = when (geometry) {
            is Polyline -> getString(getResourceId("invalid_polyline_message", "string"))
            is Polygon -> getString(getResourceId("invalid_polygon_message", "string"))
            else -> getString(getResourceId("none_selected_message", "string"))
        }
        // set the invalid message to the TextView.
        showMessage(validIfText)
    }

    companion object {
        private const val DRAW_POLYGON = 1
        private const val DRAW_CIRCLE = 2
        private const val DRAW_LINE = 3
        private const val REQUEST_CODE_PERMISSION = 2
    }
}

fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } else {
        val networkInfo = connectivityManager.activeNetworkInfo ?: return false
        return networkInfo.isConnected
    }
}