package com.sanket.citypulse.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sanket.citypulse.R
import com.sanket.citypulse.data.models.User
import com.sanket.citypulse.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Already logged in — skip to dashboard
        if (auth.currentUser != null) {
            navigateByRole()
            return
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                showStatus("Please enter email and password")
                return@setOnClickListener
            }
            showLoading()
            loginUser(email, password)
        }

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                showStatus("Please enter email and password")
                return@setOnClickListener
            }
            if (password.length < 6) {
                showStatus("Password must be at least 6 characters")
                return@setOnClickListener
            }
            showLoading()
            registerUser(email, password)
        }
    }

    private fun loginUser(email: String, password: String) {
        lifecycleScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                navigateByRole()
            } catch (e: Exception) {
                showStatus("Login failed: ${e.message}")
                hideLoading()
            }
        }
    }

    private fun registerUser(email: String, password: String) {
        lifecycleScope.launch {
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
                val uid = auth.currentUser!!.uid
                val newUser = User(uid = uid, name = email, email = email, role = "citizen")
                db.collection("users").document(uid).set(newUser).await()
                Toast.makeText(requireContext(), "Account created!", Toast.LENGTH_SHORT).show()
                navigateByRole()
            } catch (e: Exception) {
                showStatus("Register failed: ${e.message}")
                hideLoading()
            }
        }
    }

    private fun navigateByRole() {
        lifecycleScope.launch {
            try {
                val uid = auth.currentUser!!.uid
                val snapshot = db.collection("users").document(uid).get().await()
                val role = snapshot.getString("role") ?: "citizen"
                hideLoading()
                findNavController().navigate(R.id.action_login_to_dashboard)
            } catch (e: Exception) {
                showStatus("Error: ${e.message}")
                hideLoading()
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false
        binding.btnRegister.isEnabled = false
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.btnLogin.isEnabled = true
        binding.btnRegister.isEnabled = true
    }

    private fun showStatus(msg: String) {
        binding.tvStatus.text = msg
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}