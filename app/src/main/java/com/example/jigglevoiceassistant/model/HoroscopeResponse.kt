package com.example.jigglevoiceassistant.model

data class HoroscopeResponse(
    val date: String,
    val horoscope: String,
    val icon: String,
    val id: Int,
    val sign: String
)