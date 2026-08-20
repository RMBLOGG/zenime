package com.example.data.api

import com.example.data.model.PremiumPackagesResponse
import com.example.data.model.ZenimeCodeResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ZenimeSupabaseApi {

    @GET("functions/v1/zenime-list-packages")
    suspend fun getPremiumPackages(): PremiumPackagesResponse

    @POST("functions/v1/zenime-get-code")
    suspend fun getZenimeCode(@Body body: Map<String, String>): ZenimeCodeResponse
}
