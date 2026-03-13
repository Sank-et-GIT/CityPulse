package com.sanket.citypulse

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sanket.citypulse.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // NavHostFragment wired via XML — no code needed here
    }
}