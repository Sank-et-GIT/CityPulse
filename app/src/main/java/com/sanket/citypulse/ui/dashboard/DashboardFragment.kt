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
        binding.tvAiInsight.text = "🤖 Analyzing city data..."
        binding.btnRefreshAi.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prompt = "You are a smart city AI. Based on: AQI=${sensorValues["aqi"]}(${sensorStatuses["aqi"]}), Traffic=${sensorValues["traffic"]}%(${sensorStatuses["traffic"]}), Energy=${sensorValues["energy"]}GW, Water=${sensorValues["water"]}%, Waste=${sensorValues["waste"]}%. Give 2-3 sentence actionable insight for citizens."

                val url = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$geminiApiKey")
                val body = "{\"contents\":[{\"parts\":[{\"text\":\"$prompt\"}]}]}"

                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.write(body.toByteArray())
                conn.outputStream.flush()

                val responseCode = conn.responseCode
                val responseText = if (responseCode == 200) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream.bufferedReader().readText()
                }

                val json = org.json.JSONObject(responseText)
                val text = if (responseCode == 200) {
                    json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                } else {
                    "API Error: $responseText"
                }

                withContext(Dispatchers.Main) {
                    binding.tvAiInsight.text = text
                    binding.btnRefreshAi.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvAiInsight.text = "Error: ${e.javaClass.simpleName}: ${e.message}"
                    binding.btnRefreshAi.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}