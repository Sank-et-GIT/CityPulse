package com.sanket.citypulse.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sanket.citypulse.R
import com.sanket.citypulse.databinding.FragmentDashboardBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val geminiApiKey = "AIzaSyApoXnmftB9vmegpco7d215DsKRZr90i3A"
// val url = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$geminiApiKey")
    private val sensorValues = mutableMapOf<String, Double>()
    private val sensorStatuses = mutableMapOf<String, String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        loadSensorData()
        binding.btnRefreshAi.setOnClickListener { generateAiInsight() }
        binding.btnReportIssue.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_report)
        }
        binding.btnViewMap.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_map)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_logout) {
                auth.signOut()
                findNavController().navigate(R.id.action_dashboard_to_login)
                true
            } else false
        }
    }

    private fun loadSensorData() {
        lifecycleScope.launch {
            try {
                val docs = db.collection("sensor_readings").get().await()
                for (doc in docs) {
                    val type = doc.getString("type") ?: continue
                    val value = doc.getDouble("value") ?: 0.0
                    val unit = doc.getString("unit") ?: ""
                    val status = doc.getString("status") ?: "good"
                    sensorValues[type] = value
                    sensorStatuses[type] = status
                    when (type) {
                        "aqi" -> {
                            binding.tvAqiValue.text = "$value $unit"
                            styleStatus(binding.tvAqiStatus, status)
                        }
                        "traffic" -> {
                            binding.tvTrafficValue.text = "$value$unit"
                            styleStatus(binding.tvTrafficStatus, status)
                        }
                        "energy" -> {
                            binding.tvEnergyValue.text = "$value $unit"
                            styleStatus(binding.tvEnergyStatus, status)
                        }
                        "water" -> {
                            binding.tvWaterValue.text = "$value$unit"
                            styleStatus(binding.tvWaterStatus, status)
                        }
                        "waste" -> {
                            binding.tvWasteValue.text = "$value %"
                            styleStatus(binding.tvWasteStatus, status)
                        }
                    }
                }
                setupChart()
                generateAiInsight()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun styleStatus(tv: TextView, status: String) {
        when (status) {
            "good"     -> { tv.text = "● Good";     tv.setTextColor(Color.parseColor("#1D9E75")) }
            "moderate" -> { tv.text = "● Moderate"; tv.setTextColor(Color.parseColor("#FF9800")) }
            "danger"   -> { tv.text = "● Critical"; tv.setTextColor(Color.parseColor("#E53935")) }
            else       -> { tv.text = status;       tv.setTextColor(Color.GRAY) }
        }
    }

    private fun setupChart() {
        val labels = listOf("AQI", "Traffic", "Energy×10", "Water", "Waste")
        val entries = listOf(
            BarEntry(0f, sensorValues["aqi"]?.toFloat() ?: 0f),
            BarEntry(1f, sensorValues["traffic"]?.toFloat() ?: 0f),
            BarEntry(2f, (sensorValues["energy"]?.toFloat() ?: 0f) * 10),
            BarEntry(3f, sensorValues["water"]?.toFloat() ?: 0f),
            BarEntry(4f, sensorValues["waste"]?.toFloat() ?: 0f)
        )
        val colors = listOf(
            Color.parseColor("#FF9800"),
            Color.parseColor("#E53935"),
            Color.parseColor("#1D9E75"),
            Color.parseColor("#2196F3"),
            Color.parseColor("#9C27B0")
        )
        val dataSet = BarDataSet(entries, "Sensor Levels").apply {
            setColors(colors)
            valueTextColor = Color.parseColor("#1A1A1A")
            valueTextSize = 10f
        }
        binding.barChart.apply {
            data = BarData(dataSet)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textColor = Color.parseColor("#444444")
            }
            axisLeft.textColor = Color.parseColor("#444444")
            axisRight.isEnabled = false
            legend.isEnabled = false
            description.isEnabled = false
            setFitBars(true)
            animateY(800)
            invalidate()
        }
    }

    private fun generateAiInsight() {
        binding.btnRefreshAi.isEnabled = false
        binding.tvAiInsight.text = "🤖 Analyzing city data..."

        // Smart rule-based insight from live sensor data
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1500) // fake loading for effect
            val aqi = sensorValues["aqi"] ?: 0.0
            val traffic = sensorValues["traffic"] ?: 0.0
            val water = sensorValues["water"] ?: 0.0
            val waste = sensorValues["waste"] ?: 0.0

            val insight = buildString {
                // AQI insight
                when {
                    aqi > 150 -> append("⚠️ Air quality is dangerously high at ${aqi.toInt()} AQI in Sector 4 — citizens should avoid outdoor activity and wear masks. ")
                    aqi > 100 -> append("🌫️ Moderate air pollution detected (${aqi.toInt()} AQI) in Sector 4 — sensitive groups should limit outdoor exposure. ")
                    else -> append("✅ Air quality is healthy at ${aqi.toInt()} AQI today. ")
                }
                // Traffic insight
                when {
                    traffic > 70 -> append("🚦 Critical traffic congestion (${traffic.toInt()}%) on Ring Road — commuters advised to use alternate routes. ")
                    traffic > 40 -> append("🚗 Moderate traffic on Ring Road (${traffic.toInt()}%) — expect minor delays. ")
                    else -> append("✅ Traffic flowing smoothly across the city. ")
                }
                // Waste insight
                when {
                    waste > 80 -> append("🗑️ Waste levels critical at ${waste.toInt()}% in Sector 7 — immediate collection required to prevent overflow.")
                    waste > 50 -> append("🗑️ Waste collection needed soon in Sector 7 (${waste.toInt()}%).")
                    else -> append("✅ Waste collection is on schedule.")
                }
            }

            binding.tvAiInsight.text = insight
            binding.btnRefreshAi.isEnabled = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}