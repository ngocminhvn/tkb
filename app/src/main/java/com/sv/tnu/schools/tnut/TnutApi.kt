package com.sv.tnu.schools.tnut

import com.sv.tnu.rust.RustPortalCore
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface TnutApi {
    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(
        @Field("grant_type") grantType: String = "password",
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("uri") uri: String = "https://portal.tnut.edu.vn/#/home",
        @Field("vaitro") vaitro: String = "",
        @Field("mgr") mgr: String = "0",
    ): ResponseBody

    @FormUrlEncoded
    @POST("auth/login")
    suspend fun refreshToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
    ): ResponseBody

    @POST("sch/w-locdstkbtuanusertheohocky")
    suspend fun weeklyTimetable(
        @Body body: RequestBody,
    ): ResponseBody

    @POST("sms/w-locthongtinimagesinhvien")
    suspend fun avatar(
        @retrofit2.http.Query("MaSV") maSv: String,
        @Body body: RequestBody,
    ): ResponseBody

    @POST("epm/w-locdslichthisvtheohocky")
    suspend fun exams(
        @Body body: RequestBody,
    ): ResponseBody

    @POST("srm/w-locsettingxemdiemdg")
    suspend fun gradeSetting(
        @Body body: RequestBody,
    ): ResponseBody

    @POST("srm/w-locdsdiemsinhvien")
    suspend fun studentGrades(
        @retrofit2.http.Query("hien_thi_mon_theo_hkdk") hienThiMonTheoHkdk: Boolean = false,
        @Body body: RequestBody,
    ): ResponseBody

    @POST("srm/w-dskyhieudiem")
    suspend fun gradeSymbols(
        @Body body: RequestBody,
    ): ResponseBody
}

internal suspend fun TnutApi.loginSession(username: String, password: String) =
    login(username = username, password = password).use { RustPortalCore.mapLogin(it.bytes()) }

internal suspend fun TnutApi.refreshSession(refreshToken: String) =
    refreshToken(refreshToken = refreshToken).use { RustPortalCore.mapLogin(it.bytes()) }
