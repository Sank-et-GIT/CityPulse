package com.sanket.citypulse.data.models

import com.google.firebase.Timestamp

// ── Sensor Reading ────────────────────────────────────────────────
// Firestore collection: sensor_readings/{id}
// One document per type: aqi, traffic, energy, water, waste
data class SensorReading(
    val id: String = "",
    val type: String = "",
    val value: Double = 0.0,
    val unit: String = "",
    val zone: String = "",
    val status: String = "good",  // good / moderate / danger
    val timestamp: Timestamp? = null
)

// ── City Report ───────────────────────────────────────────────────
// Firestore collection: city_reports/{id}
// Created by citizens, read by admins
data class CityReport(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val category: String = "",   // pothole / waste / flooding / other
    val description: String = "",
    val photoUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: String = "pending", // pending / in_review / resolved
    val timestamp: Timestamp? = null
)

// ── User ──────────────────────────────────────────────────────────
// Firestore collection: users/{uid}
// Created on first login via Google Sign-In
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "citizen"  // citizen / admin
)