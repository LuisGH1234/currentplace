package com.example.currentplace

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.media.MediaPlayer
import android.nfc.Tag
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Adapter
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.PlaceLikelihood

import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse
import kotlinx.android.synthetic.main.activity_maps.*
import java.lang.Exception
import java.util.*
import kotlin.properties.Delegates

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
// https://analytics.consorciohbo.com.pe/servicio/api/entregas
    private lateinit var mMap: GoogleMap

    // New variables for Current Place Picker
    private val TAG: String = "MapsActivity"
    lateinit var lstPlaces: ListView
    private lateinit var mPlacesClient: PlacesClient
    // private lateinit var aas: Geocoder
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider
    private lateinit var mLastknowLocation: Location

    // A default location (UPC San isidro) and default zoom to use when location permission is
    // not granted
    private val mDefaultLocation = LatLng(-12.0874512,-77.0521308)
    private val default_Zoom: Float = 15.00f
    private val permission_request_Access_fine_location = 1
    private var mLocationPermissiongranted = false

    // Used for selecting the current place
    private val M_MAX_Entries = 5
    private lateinit var mLikelyPlaceNames: Array<String?>
    private lateinit var mLikelyPlaceAddress: Array<String?>
    private lateinit var mLikePlaceAttributions: Array<String?>
    private lateinit var mLikelyPlaceLatLng: Array<LatLng?>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Set up the action toolbar
        setSupportActionBar(toolbar)

        // Set up the view
        lstPlaces = findViewById(R.id.listPlaces)

        // Initialize the Places client
        val apiKey = getString(R.string.google_maps_key)
        Places.initialize(applicationContext, apiKey)
        mPlacesClient = Places.createClient(this)
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.i(TAG, "item ID: ${item.itemId}")
        return when(item.itemId) {
            R.id.action_geolocate -> {
                pickCurrentPlace()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback
         * onRequestPermissionResult.
         */
        mLocationPermissiongranted = false
        if (ContextCompat.checkSelfPermission(this.applicationContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissiongranted = true
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                permission_request_Access_fine_location)
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Add a marker in UPC San Isidro and move the camera
        val sydney = mDefaultLocation
        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Peru"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))

        // Enable the zoom controls for the map
        mMap.uiSettings.isZoomControlsEnabled = true

        // Prompt the user for permission
        getLocationPermission()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        // super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        mLocationPermissiongranted = false
        when(requestCode) {
            permission_request_Access_fine_location -> {
                // If request is cancelled, the result arrays are empty
                if(grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissiongranted = true
                }
            }
        }
    }

    // @SuppressLint("MissingPermission")
    private fun getCurrentPlaceLikelihood() {
        // Use fields to define the data types to return
        val placeFields = listOf(Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)
        Log.i(TAG, "${Place.Field.NAME}, ${Place.Field.ADDRESS}, ${Place.Field.LAT_LNG}")

        // Get the likely places - that is, the businesses and other points of interest that
        // are the best match for the device's current location
        @SuppressWarnings("MissingPermission") val request =
            FindCurrentPlaceRequest.builder(placeFields).build()
        val placeResponse = mPlacesClient.findCurrentPlace(request)
        // Log.i(TAG, "Response: ${placeResponse.result.toString()}")
        placeResponse.addOnCompleteListener {
            if(it.isSuccessful) {
                val response = it.result
                // Set the count, handling case where less than 5 entries are returned
                var count = if(response!!.placeLikelihoods.size < M_MAX_Entries) {
                    response.placeLikelihoods.size
                } else M_MAX_Entries

                var i = 0
                mLikelyPlaceNames = arrayOfNulls(count)
                mLikelyPlaceAddress = arrayOfNulls(count)
                mLikePlaceAttributions = arrayOfNulls(count)
                mLikelyPlaceLatLng = arrayOfNulls(count)

                for(placeLikelihood in response.placeLikelihoods) {
                    val currPlace = placeLikelihood.place

                    mLikelyPlaceNames[i] = currPlace.name
                    mLikelyPlaceAddress[i] = currPlace.address
                    mLikePlaceAttributions[i] = currPlace.attributions?.joinToString(" ")
                    mLikelyPlaceLatLng[i] = currPlace.latLng

                    val currLatLng = if (mLikelyPlaceLatLng[i] != null)
                        mLikelyPlaceLatLng[i].toString() else ""
                    // val currLatLng = if(mLikelyPlaceLatLng[i] == null) "" else mLikelyPlaceLatLng

                    Log.i(TAG,
                        "Place ${currPlace.name} has likelihood ${placeLikelihood.likelihood} at $currLatLng")
                    i++
                    if(i > count-1) break
                }

                // Populate the ListView
                fillPlacesList()
            } else {
                var exception = it.exception
                if(exception is ApiException) {
                    Log.e(TAG, "Place not found: ${exception.statusCode}: ${exception.message}")
                }
            }
        }
    }

    // @SuppressLint("MissingPermission")
    private fun getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, with may be null in rare
         * cases when location is not available
         */
        try {
            if(mLocationPermissiongranted) {
                val locationResult = mFusedLocationProviderClient.lastLocation
                locationResult.addOnCompleteListener {
                    if (it.isSuccessful) {
                        // Set the map's camera position to the current location of the device
                        mLastknowLocation = it.result!!
                        Log.d(TAG, "Latitude: ${mLastknowLocation.latitude}")
                        Log.d(TAG, "Longitude: ${mLastknowLocation.longitude}")
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                            LatLng(mLastknowLocation.latitude,
                                mLastknowLocation.longitude), default_Zoom))
                    } else {
                        Log.d(TAG, "Current location is null. Using defaults.")
                        Log.d(TAG, "Exception ${it.exception}")
                        mMap.moveCamera(CameraUpdateFactory
                            .newLatLngZoom(mDefaultLocation, default_Zoom))
                    }
                    getCurrentPlaceLikelihood()
                }
            }
        } catch (ex: SecurityException) {
            Log.e(TAG, "Exception: ${ex.message}")
        }
    }

    private fun pickCurrentPlace() {
        if(mMap == null) return
        if(mLocationPermissiongranted) getDeviceLocation()
        else {
            // The user has not granted permission
            Log.i(TAG, "The user did not grant location permission")

            // Add a default marker, because the user hasn't selected a place
            mMap.addMarker(MarkerOptions().title(getString(R.string.default_info_title))
                .position(mDefaultLocation)
                .snippet(getString(R.string.default_info_snippet)))

            // Prompt the user for permission
            getLocationPermission()
        }
    }

    // private val listClickedHandler = object : AdapterView.OnItemClickListener {
    //      override fun onItemClick(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
    private val listClickedHandler =
        AdapterView.OnItemClickListener { parent, v, position, id ->
            // position will give us the index of which place was selected in the array
            val markerLatLng = mLikelyPlaceLatLng[position]
            var markerSnippet = mLikelyPlaceAddress[position]
            if(mLikePlaceAttributions[position] != null) {
                markerSnippet = "$markerSnippet , ${mLikePlaceAttributions[position]}"
            }

            // Add a marker for the selected place, with an info window
            // showing information about that place
            mMap.addMarker(MarkerOptions().title(mLikelyPlaceNames[position])
                .position(markerLatLng!!)
                .snippet(markerSnippet))

            // Position the map's camera at the location of the marker
            mMap.moveCamera(CameraUpdateFactory.newLatLng(markerLatLng))
        }

    private fun fillPlacesList() {
        Log.i(TAG, mLikelyPlaceNames.size.toString())
        // Set up an ArrayAdapter to convert likely places into TextViews to populate the ListView
        val placesAdapter = ArrayAdapter<String>(this,
            android.R.layout.simple_list_item_1, mLikelyPlaceNames)
        lstPlaces.apply {
            onItemClickListener = listClickedHandler
            adapter = placesAdapter
        }

        // placesAdapter.notifyDataSetChanged()
        // lstPlaces.adapter = placesAdapter
        // lstPlaces.setOnItemClickListener(listClickedHandler)
    }
}
