package com.sanket.citypulse.ui.map

import android.graphics.Color
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
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.sanket.citypulse.R
import com.sanket.citypulse.databinding.FragmentMapBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SensorZone(
    val type: String,
    val label: String,
    val emoji: String,
    val location: String,
    val latLng: LatLng,
    val value: Double,
    val unit: String,
    val status: String,
    val advice: String
)

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private var googleMap: GoogleMap? = null

    private val zones = mutableListOf<SensorZone>()
    private val markers = mutableListOf<Marker>()
    private val circles = mutableListOf<Circle>()
    private var activeFilter = "all"

    private val sensorLocations = mapOf(
        "aqi"     to LatLng(21.1458, 79.0882),
        "traffic" to LatLng(21.1520, 79.0750),
        "energy"  to LatLng(21.1466, 79.0820),
        "water"   to LatLng(21.1380, 79.0950),
        "waste"   to LatLng(21.1600, 79.0700)
    )

    private val sensorEmoji = mapOf(
        "aqi" to "🌫️", "traffic" to "🚦",
        "energy" to "⚡", "water" to "💧", "waste" to "🗑️"
    )

    private val sensorLocationNames = mapOf(
        "aqi"     to "Sector 4, Nagpur",
        "traffic" to "Ring Road, Nagpur",
        "energy"  to "City Center, Nagpur",
        "water"   to "Reservoir, Nagpur",
        "waste"   to "Sector 7, Nagpur"
    )

    private val sensorAdvice = mapOf(
        "aqi"     to mapOf("good" to "Air is clean ✅", "moderate" to "Wear mask outdoors", "danger" to "Stay indoors!"),
        "traffic" to mapOf("good" to "Roads are clear ✅", "moderate" to "Expect some delays", "danger" to "Avoid Ring Road!"),
        "energy"  to mapOf("good" to "Power stable ✅", "moderate" to "Minor fluctuations", "danger" to "Outage risk high!"),
        "water"   to mapOf("good" to "Supply normal ✅", "moderate" to "Save water today", "danger" to "Critical shortage!"),
        "waste"   to mapOf("good" to "Collection on time ✅", "moderate" to "Collection delayed", "danger" to "Overflow risk!")
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
        setupFilterButtons()
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.mapView) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupFilterButtons() {
        val filterMap = mapOf(
            binding.btnFilterAll     to "all",
            binding.btnFilterAqi     to "aqi",
            binding.btnFilterTraffic to "traffic",
            binding.btnFilterEnergy  to "energy",
            binding.btnFilterWater   to "water",
            binding.btnFilterWaste   to "waste"
        )
        filterMap.forEach { (btn, filter) ->
            btn.setOnClickListener {
                activeFilter = filter
                filterMap.keys.forEach {
                    it.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#555555"))
                }
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1D9E75"))
                applyFilter()
                binding.bottomCard.visibility = View.GONE
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val nagpur = LatLng(21.1458, 79.0882)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(nagpur, 11.8f))
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.setOnMapClickListener { binding.bottomCard.visibility = View.GONE }
        loadSensorData()
    }

    private fun loadSensorData() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val docs = db.collection("sensor_readings").get().await()
                zones.clear()
                for (doc in docs) {
                    val type = doc.getString("type") ?: continue
                    val value = doc.getDouble("value") ?: 0.0
                    val status = doc.getString("status") ?: "good"
                    val latLng = sensorLocations[type] ?: continue
                    zones.add(SensorZone(
                        type = type,
                        label = "${sensorEmoji[type]} ${type.replaceFirstChar { it.uppercase() }}",
                        emoji = sensorEmoji[type] ?: "",
                        location = sensorLocationNames[type] ?: "",
                        latLng = latLng,
                        value = value,
                        unit = unit,
                        status = status,
                        advice = sensorAdvice[type]?.get(status) ?: ""
                    ))
                }
                drawAllZones()
            } catch (e: Exception) { }
        }
    }

    private fun drawAllZones() {
        markers.forEach { it.remove() }
        circles.forEach { it.remove() }
        markers.clear()
        circles.clear()
        val map = googleMap ?: return
        zones.forEach { zone ->
            val markerColor = statusToHue(zone.status)
            val fillColor = statusToFillColor(zone.status)
            val strokeColor = statusToStrokeColor(zone.status)
            val radius = valueToRadius(zone.type, zone.value)
            val circle = map.addCircle(
                CircleOptions()
                    .center(zone.latLng)
                    .radius(radius)
                    .fillColor(fillColor)
                    .strokeColor(strokeColor)
                    .strokeWidth(2f)
            )
            val marker = map.addMarker(
                MarkerOptions()
                    .position(zone.latLng)
                    .title(zone.label)
                    .snippet("${zone.value} ${zone.unit} · ${zone.status}")
                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
            )
            marker?.tag = zone.type
            marker?.let { markers.add(it) }
            circles.add(circle)
        }
        googleMap?.setOnMarkerClickListener { marker ->
            val zone = zones.find { it.type == marker.tag }
            zone?.let { showBottomCard(it) }
            marker.showInfoWindow()
            true
        }
    }

    private fun applyFilter() {
        markers.forEachIndexed { i, marker ->
            val zone = zones.getOrNull(i) ?: return@forEachIndexed
            val visible = activeFilter == "all" || zone.type == activeFilter
            marker.isVisible = visible
            circles.getOrNull(i)?.isVisible = visible
        }
        if (activeFilter != "all") {
            val zone = zones.find { it.type == activeFilter }
            zone?.let {
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(it.latLng, 15f))
            }
        } else {
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(21.1458, 79.0882), 13f))
        }
    }

    private fun showBottomCard(zone: SensorZone) {
        binding.tvCardEmoji.text = zone.emoji
        binding.tvCardTitle.text = when(zone.type) {
            "aqi"     -> "Air Quality Index"
            "traffic" -> "Traffic Congestion"
            "energy"  -> "Energy Consumption"
            "water"   -> "Water Supply"
            "waste"   -> "Waste Collection"
            else      -> zone.type
        }
        binding.tvCardLocation.text = zone.location
        binding.tvCardValue.text = zone.value.toInt().toString()
        binding.tvCardUnit.text = zone.unit
        binding.tvCardAdvice.text = zone.advice
        val (statusText, statusColor) = when(zone.status) {
            "good"     -> "● Good"     to "#1D9E75"
            "moderate" -> "● Moderate" to "#FF9800"
            "danger"   -> "● Critical" to "#E53935"
            else       -> zone.status  to "#888888"
        }
        binding.tvCardStatus.text = statusText
        binding.tvCardStatus.setTextColor(Color.parseColor(statusColor))
        binding.bottomCard.visibility = View.VISIBLE
    }

    private fun valueToRadius(type: String, value: Double): Double {
        return when(type) {
            "aqi"     -> (value / 200.0).coerceIn(0.2, 1.0) * 600
            "traffic" -> (value / 100.0).coerceIn(0.2, 1.0) * 600
            "energy"  -> (value / 5.0).coerceIn(0.2, 1.0) * 500
            "water"   -> (value / 100.0).coerceIn(0.2, 1.0) * 500
            "waste"   -> (value / 100.0).coerceIn(0.2, 1.0) * 600
            else      -> 400.0
        }
    }

    private fun statusToHue(status: String) = when(status) {
        "good"     -> BitmapDescriptorFactory.HUE_GREEN
        "moderate" -> BitmapDescriptorFactory.HUE_ORANGE
        "danger"   -> BitmapDescriptorFactory.HUE_RED
        else       -> BitmapDescriptorFactory.HUE_CYAN
    }

    private fun statusToFillColor(status: String) = when(status) {
        "good"     -> 0x301D9E75.toInt()
        "moderate" -> 0x30FF9800.toInt()
        "danger"   -> 0x30E53935.toInt()
        else       -> 0x302196F3.toInt()
    }

    private fun statusToStrokeColor(status: String) = when(status) {
        "good"     -> 0xFF1D9E75.toInt()
        "moderate" -> 0xFFFF9800.toInt()
        "danger"   -> 0xFFE53935.toInt()
        else       -> 0xFF2196F3.toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}