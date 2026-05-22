package com.example.mobileaudiowhatsapp

import kotlinx.serialization.Serializable

@Serializable
object Dashboard

@Serializable
object History

@Serializable
data class CallDetails(val id: Int)

@Serializable
object Settings
