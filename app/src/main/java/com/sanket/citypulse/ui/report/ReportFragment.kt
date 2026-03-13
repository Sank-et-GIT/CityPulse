package com.sanket.citypulse.ui.report


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sanket.citypulse.R
import com.sanket.citypulse.databinding.FragmentReportBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val categories = listOf(
        "🌫️ Air Quality", "🚦 Traffic", "💧 Water Supply",
        "⚡ Power Outage", "🗑️ Waste / Garbage", "🛣️ Road Damage",
        "🌳 Parks & Public Spaces", "🔊 Noise Pollution", "Other"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            categories
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = adapter

        binding.btnSubmitReport.setOnClickListener { submitReport() }
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
    }

    private fun submitReport() {
        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()
        val category = categories[binding.spinnerCategory.selectedItemPosition]

        if (title.isEmpty()) {
            binding.tvStatus.text = "Please enter a title"
            binding.tvStatus.setTextColor(android.graphics.Color.RED)
            return
        }
        if (description.isEmpty()) {
            binding.tvStatus.text = "Please enter a description"
            binding.tvStatus.setTextColor(android.graphics.Color.RED)
            return
        }

        showLoading()
        lifecycleScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: "anonymous"
                val reportId = UUID.randomUUID().toString()
                val report = hashMapOf(
                    "id" to reportId,
                    "userId" to uid,
                    "title" to title,
                    "description" to description,
                    "location" to location,
                    "category" to category,
                    "status" to "pending",
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
                db.collection("city_reports").document(reportId).set(report).await()
                hideLoading()
                binding.tvStatus.text = "✅ Report submitted successfully!"
                binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#1D9E75"))
                Toast.makeText(requireContext(), "Report submitted!", Toast.LENGTH_SHORT).show()
                // Go back to dashboard after 1.5s
                binding.root.postDelayed({
                    findNavController().popBackStack()
                }, 1500)
            } catch (e: Exception) {
                hideLoading()
                binding.tvStatus.text = "Failed: ${e.message}"
                binding.tvStatus.setTextColor(android.graphics.Color.RED)
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSubmitReport.isEnabled = false
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.btnSubmitReport.isEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}