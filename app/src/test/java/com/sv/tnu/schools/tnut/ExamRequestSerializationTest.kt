package com.sv.tnu.schools.tnut

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

class ExamRequestSerializationTest {
    private val portalJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        // Same default behavior as TnutApiClient (encodeDefaults = false).
    }

    @Test
    fun finalExamRequestSendsEveryRequiredPortalField() {
        val actual = portalJson.encodeToJsonElement(
            ExamRequest.serializer(),
            ExamRequest.forSemester(20261, isGiuaKy = false),
        )
        val expected = portalJson.parseToJsonElement(
            """{"filter":{"hoc_ky":20261,"is_giua_ky":false},"additional":{"paging":{"limit":100,"page":1},"ordering":[{"name":null,"order_type":null}]}}"""
        )
        assertEquals(expected, actual)
    }

    @Test
    fun midtermRequestIncludesTrueAndRealPageNumber() {
        val actual = portalJson.encodeToJsonElement(
            ExamRequest.serializer(),
            ExamRequest.forSemester(20261, isGiuaKy = true, page = 3),
        )
        val expected = portalJson.parseToJsonElement(
            """{"filter":{"hoc_ky":20261,"is_giua_ky":true},"additional":{"paging":{"limit":100,"page":3},"ordering":[{"name":null,"order_type":null}]}}"""
        )
        assertEquals(expected, actual)
    }

    @Test
    fun portalExamResponseParsesStudentSchedule() {
        val payload = portalJson.parseToJsonElement(
            """{"code":200,"data":{"ds_lich_thi":[{"ma_mon":"B103BC1","ten_mon":"Bóng chuyền 1","ngay_thi":"03/11/2026","gio_bat_dau":"14:25","so_phut":90}],"ds_lich_hoan_thi":[]}}"""
        )
        val result = portalJson.decodeFromJsonElement(ExamResponse.serializer(), payload)
        assertEquals(200, result.code)
        assertEquals("03/11/2026", result.data?.dsLichThi?.single()?.ngayThi)
        assertEquals(90, result.data?.dsLichThi?.single()?.soPhutInt)
    }
}
