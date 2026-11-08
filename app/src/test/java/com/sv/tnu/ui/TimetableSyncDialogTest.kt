package com.sv.tnu.ui

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableSyncDialogTest {
    @Test
    fun showsSpecificConnectionFailures() {
        assertTrue(describeTimetableSyncError(SocketTimeoutException("Read timed out")).contains("Hết thời gian chờ"))
        assertTrue(describeTimetableSyncError(UnknownHostException("portal.tnut.edu.vn")).contains("Wi-Fi/DNS"))
    }

    @Test
    fun preservesActionablePortalErrorDetails() {
        assertTrue(describeTimetableSyncError(IllegalStateException("JSON từ portal không hợp lệ")).contains("JSON từ portal không hợp lệ"))
    }

    @Test
    fun statusPreservesIndependentExamWarning() {
        val status = TimetableSyncStatus.Success("Lịch thi không tải được")
        assertTrue(status.examWarning!!.contains("Lịch thi"))
    }
}
