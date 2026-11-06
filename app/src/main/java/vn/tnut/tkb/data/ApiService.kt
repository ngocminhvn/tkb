package vn.tnut.tkb.data

import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface PortalApi {
    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(
        @Field("grant_type") grantType: String = "password",
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("uri") uri: String = "https://portal.tnut.edu.vn/#/home",
        @Field("vaitro") vaitro: String = "",
        @Field("mgr") mgr: String = "0",
    ): LoginResponse

    @FormUrlEncoded
    @POST("auth/login")
    suspend fun refreshToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
    ): LoginResponse

    @POST("sch/w-locdshockytkbuser")
    suspend fun semesters(
        @Body body: Map<String, String> = emptyMap(),
    ): ApiWrapper<SemesterListData>

    @POST("sch/w-locdstkbtuanusertheohocky")
    suspend fun weeklyTimetable(
        @Body body: WeeklyRequest,
    ): ApiWrapper<TkbData>
}
