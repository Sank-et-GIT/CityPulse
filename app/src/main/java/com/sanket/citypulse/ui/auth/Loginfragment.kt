package com.sanket.citypulse.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.sanket.citypulse.data.models.User
import com.sanket.citypulse.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Web client ID from Firebase console — paste yours here
    private val webClientId = "7802375917834-04l3gb6o44ue4is1aoadmb1c8l7g74dp.apps.googleusercontent.com"

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            showStatus("Sign-in failed: ${e.message}")
            hideLoading()
        }
    }

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

        // Already logged in — skip login screen
        if (auth.currentUser != null) {
            navigateByRole()
            return
        }

        binding.btnGoogleSignIn.setOnClickListener {
            showLoading()
            startGoogleSignIn()
        }
    }

    private fun startGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(requireActivity(), gso)
        signInLauncher.launch(client.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        lifecycleScope.launch {
            try {
                auth.signInWithCredential(credential).await()
                val user = auth.currentUser!!
                saveUserToFirestore(user.uid, user.displayName ?: "", user.email ?: "")
            } catch (e: Exception) {
                showStatus("Auth failed: ${e.message}")
                hideLoading()
            }
        }
    }

    private suspend fun saveUserToFirestore(uid: String, name: String, email: String) {
        val userRef = db.collection("users").document(uid)
        val snapshot = userRef.get().await()
        if (!snapshot.exists()) {
            val newUser = User(uid = uid, name = name, email = email, role = "citizen")
            userRef.set(newUser).await()
        }
        navigateByRole()
    }

    private fun navigateByRole() {
        lifecycleScope.launch {
            try {
                val uid = auth.currentUser!!.uid
                val snapshot = db.collection("users").document(uid).get().await()
                val role = snapshot.getString("role") ?: "citizen"
                Toast.makeText(requireContext(), "Welcome! Logged in as $role", Toast.LENGTH_SHORT).show()
                hideLoading()
                // Navigation to dashboard will be wired after dashboard is built
            } catch (e: Exception) {
                showStatus("Error: ${e.message}")
                hideLoading()
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnGoogleSignIn.isEnabled = false
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.btnGoogleSignIn.isEnabled = true
    }

    private fun showStatus(msg: String) {
        binding.tvStatus.text = msg
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}