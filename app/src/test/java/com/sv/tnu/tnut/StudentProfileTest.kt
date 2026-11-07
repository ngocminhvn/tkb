package com.sv.tnu.tnut

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudentProfileTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun studentNameIsOptionalInPortalAvatarPayload() {
        val legacy = json.decodeFromString(
            AvatarResponse.serializer(),
            """{"code":200,"data":{"thong_tin_sinh_vien":{"image":"ABC"}}}"""
        )
        assertNull(legacy.data?.thongTinSinhVien?.hoTen)
        assertEquals("ABC", legacy.data?.thongTinSinhVien?.image)
    }

    @Test
    fun studentNameIsReadFromProfileWhenProvided() {
        val response = json.decodeFromString(
            AvatarResponse.serializer(),
            """{"code":200,"data":{"thong_tin_sinh_vien":{"ho_ten":"Nguyễn Văn A","image":null}}}"""
        )
        assertEquals("Nguyễn Văn A", response.data?.thongTinSinhVien?.hoTen)
    }
}
