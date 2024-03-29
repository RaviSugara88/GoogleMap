package com.ravisugara.googlemapexample

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ravisugara.googlemapexample.util.AnimationUtils
import com.ravisugara.googlemapexample.util.MapUtils
import java.io.IOException


class MapsActivity : AppCompatActivity(), OnMapReadyCallback,
    GoogleMap.OnMarkerClickListener {

  private lateinit var googleMap: GoogleMap
  private lateinit var defaultLocation: LatLng
  private var originMarker: Marker? = null
  private var destinationMarker: Marker? = null
  private var grayPolyline: Polyline? = null
  private var blackPolyline: Polyline? = null
  private var movingCabMarker: Marker? = null
  private var previousLatLng: LatLng? = null
  private var currentLatLng: LatLng? = null
  private lateinit var handler: Handler
  private lateinit var runnable: Runnable
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_maps)
    // Obtain the SupportMapFragment and get notified when the map is ready to be used.
    val mapFragment = supportFragmentManager
        .findFragmentById(R.id.map) as SupportMapFragment
    mapFragment.getMapAsync(this)


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
  private fun moveCamera(latLng: LatLng) {
    googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
  }

  private fun animateCamera(latLng: LatLng) {
    val cameraPosition = CameraPosition.Builder().target(latLng).zoom(15.5f).build()
    googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
  }

  private fun addCarMarkerAndGet(latLng: LatLng): Marker {
    val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getCarBitmap(this))
    return googleMap.addMarker(
      MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor)
    )
  }

  private fun addOriginDestinationMarkerAndGet(latLng: LatLng): Marker {
    val bitmapDescriptor =
      BitmapDescriptorFactory.fromBitmap(MapUtils.getOriginDestinationMarkerBitmap())
    return googleMap.addMarker(
      MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor)
    )
  }

  private fun showDefaultLocationOnMap(latLng: LatLng) {
    moveCamera(latLng)
    animateCamera(latLng)
  }

  /**
   * This function is used to draw the path between the Origin and Destination.
   */
  private fun showPath(latLngList: ArrayList<LatLng>) {
    val builder = LatLngBounds.Builder()
    for (latLng in latLngList) {
      builder.include(latLng)
    }
    val bounds = builder.build()
    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 2))

    val polylineOptions = PolylineOptions()
    polylineOptions.color(Color.GRAY)
    polylineOptions.width(5f)
    polylineOptions.addAll(latLngList)
    grayPolyline = googleMap.addPolyline(polylineOptions)

    val blackPolylineOptions = PolylineOptions()
    blackPolylineOptions.color(Color.BLACK)
    blackPolylineOptions.width(5f)
    blackPolyline = googleMap.addPolyline(blackPolylineOptions)

    originMarker = addOriginDestinationMarkerAndGet(latLngList[0])
    originMarker?.setAnchor(0.5f, 0.5f)
    destinationMarker = addOriginDestinationMarkerAndGet(latLngList[latLngList.size - 1])
    destinationMarker?.setAnchor(0.5f, 0.5f)

    val polylineAnimator = AnimationUtils.polylineAnimator()
    polylineAnimator.addUpdateListener { valueAnimator ->
      val percentValue = (valueAnimator.animatedValue as Int)
      val index = (grayPolyline?.points!!.size) * (percentValue / 100.0f).toInt()
      blackPolyline?.points = grayPolyline?.points!!.subList(0, index)
    }
    polylineAnimator.start()
  }

  /**
   * This function is used to update the location of the Cab while moving from Origin to Destination
   */
  private fun updateCarLocation(latLng: LatLng) {
    if (movingCabMarker == null) {
      movingCabMarker = addCarMarkerAndGet(latLng)
    }
    if (previousLatLng == null) {
      currentLatLng = latLng
      previousLatLng = currentLatLng
      movingCabMarker?.position = currentLatLng
      movingCabMarker?.setAnchor(0.5f, 0.5f)
      animateCamera(currentLatLng!!)
    } else {
      previousLatLng = currentLatLng
      currentLatLng = latLng
      val valueAnimator = AnimationUtils.carAnimator()
      valueAnimator.addUpdateListener { va ->
        if (currentLatLng != null && previousLatLng != null) {
          val multiplier = va.animatedFraction
          val nextLocation = LatLng(
            multiplier * currentLatLng!!.latitude + (1 - multiplier) * previousLatLng!!.latitude,
            multiplier * currentLatLng!!.longitude + (1 - multiplier) * previousLatLng!!.longitude
          )
          movingCabMarker?.position = nextLocation
          val rotation = MapUtils.getRotation(previousLatLng!!, nextLocation)
          if (!rotation.isNaN()) {
            movingCabMarker?.rotation = rotation
          }
          movingCabMarker?.setAnchor(0.5f, 0.5f)
          animateCamera(nextLocation)
        }
      }
      valueAnimator.start()
    }
  }

  private fun showMovingCab(cabLatLngList: ArrayList<LatLng>) {
    handler = Handler()
    var index = 0
    runnable = Runnable {
      run {
        if (index < 10) {
          updateCarLocation(cabLatLngList[index])
          handler.postDelayed(runnable, 3000)
          ++index
        } else {
          handler.removeCallbacks(runnable)
          Toast.makeText(this@MapsActivity, "Trip Ends", Toast.LENGTH_LONG).show()
        }
      }
    }
    handler.postDelayed(runnable, 5000)
  }

  override fun onMapReady(googleMap: GoogleMap) {
    this.googleMap = googleMap
    defaultLocation = LatLng(28.435350000000003, 77.11368)
    showDefaultLocationOnMap(defaultLocation)

    Handler().postDelayed(Runnable {
      showPath(MapUtils.getListOfLocations())
      showMovingCab(MapUtils.getListOfLocations())
    }, 3000)
  }

  override fun onMarkerClick(p0: Marker?): Boolean {
    return false
  }

}
