package com.example.jigglevoiceassistant.util

import com.example.jigglevoiceassistant.model.HoroscopeResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.http.GET
import retrofit2.http.Query

interface HoroscopeService {
    @GET("libra")
    fun getLibraHoroscope(
        @Query("date") date: String,
        @Query("lang") language: String
    ): Call<HoroscopeResponse>
}
