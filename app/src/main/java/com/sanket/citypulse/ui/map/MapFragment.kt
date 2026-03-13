package com.sanket.citypulse.ui.map


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.sanket.citypulse.R
import com.sanket.citypulse.databinding.FragmentMapBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private var googleMap: GoogleMap? = null

    private val sensorLocations = mapOf(
        "aqi"     to LatLng(21.1458, 79.0882),
        "traffic" to LatLng(21.1520, 79.0750),
        "energy"  to LatLng(21.1466, 79.0820),
        "water"   to LatLng(21.1380, 79.0950),
        "waste"   to LatLng(21.1600, 79.0700)
    )

    private val sensorLabels = mapOf(
        "aqi"     to "🌫️ Air Quality · Sector 4",
        "traffic" to "🚦 Traffic · Ring Road",
        "energy"  to "⚡ Energy · City Center",
        "water"   to "💧 Water · Reservoir",
        "waste"   to "🗑️ Waste · Sector 7"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.mapView) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val nagpur = LatLng(21.1458, 79.0882)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(nagpur, 13f))
        map.mapType = GoogleMap.MAP_TYPE_NORMAL

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val docs = db.collection("sensor_readings").get().await()
                for (doc in docs) {
                    val type = doc.getString("type") ?: continue
                    val value = doc.getDouble("value") ?: 0.0
                    val unit = doc.getString("unit") ?: ""
                    val status = doc.getString("status") ?: "good"
                    val location = sensorLocations[type] ?: continue
                    val label = sensorLabels[type] ?: type

                    val markerColor = when (status) {
                        "good"     -> BitmapDescriptorFactory.HUE_GREEN
                        "moderate" -> BitmapDescriptorFactory.HUE_ORANGE
                        "danger"   -> BitmapDescriptorFactory.HUE_RED
                        else       -> BitmapDescriptorFactory.HUE_CYAN
                    }
                    val circleFill = when (status) {
                        "good"     -> 0x301D9E75.toInt()
                        "moderate" -> 0x30FF9800.toInt()
                        "danger"   -> 0x30E53935.toInt()
                        else       -> 0x302196F3.toInt()
                    }
                    val circleStroke = when (status) {
                        "good"     -> 0xFF1D9E75.toInt()
                        "moderate" -> 0xFFFF9800.toInt()
                        "danger"   -> 0xFFE53935.toInt()
                        else       -> 0xFF2196F3.toInt()
                    }

                    map.addCircle(
                        CircleOptions()
                            .center(location)
                            .radius(400.0)
                            .fillColor(circleFill)
                            .strokeColor(circleStroke)
                            .strokeWidth(2f)
                    )
                    map.addMarker(
                        MarkerOptions()
                            .position(location)
                            .title(label)
                            .snippet("Value: $value $unit · Status: $status")
                            .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                    )
                }
            } catch (e: Exception) {
                // silent fail
            }
        }
        map.setOnMarkerClickListener { marker ->
            marker.showInfoWindow()
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}