package com.sv.tnu.update

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _downloadedBytes = MutableStateFlow(0L)
    val downloadedBytes: StateFlow<Long> = _downloadedBytes.asStateFlow()

    private val _totalBytes = MutableStateFlow<Long?>(null)
    val totalBytes: StateFlow<Long?> = _totalBytes.asStateFlow()

    private val _downloadResult = MutableStateFlow<AppUpdateDownloadResult?>(null)
    val downloadResult: StateFlow<AppUpdateDownloadResult?> = _downloadResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    val currentVersionCode: Long
    val currentVersionName: String

    init {
        val (code, name) = AppUpdateManager.getAppVersion(application)
        currentVersionCode = code
        currentVersionName = name
    }

    fun checkForUpdate(manual: Boolean = false) {
        if (_isChecking.value || _downloading.value) return
        _isChecking.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val result = AppUpdateManager.check(getApplication())
            withContext(Dispatchers.Main) {
                _isChecking.value = false
                if (result.update != null) {
                    _updateInfo.value = result.update
                    _downloadResult.value = null
                    _errorMessage.value = null
                    _showDialog.value = true
                } else if (manual) {
                    val msg = result.error ?: "Bạn đang sử dụng phiên bản mới nhất ($currentVersionName)."
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startDownload(info: AppUpdateInfo) {
        if (_downloading.value) return
        _downloading.value = true
        _downloadedBytes.value = 0L
        _totalBytes.value = info.sizeBytes
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val result = AppUpdateManager.download(
                context = getApplication(),
                update = info,
                onProgress = { downloaded, total ->
                    _downloadedBytes.value = downloaded
                    _totalBytes.value = total
                },
            )

            withContext(Dispatchers.Main) {
                _downloading.value = false
                _downloadResult.value = result
                if (result.error != null) {
                    _errorMessage.value = result.error
                } else {
                    // Tự động mở trình cài đặt khi tải và xác thực xong
                    installCurrentDownload()
                }
            }
        }
    }

    fun installCurrentDownload() {
        val res = _downloadResult.value ?: return
        val err = AppUpdateManager.installApk(getApplication(), res)
        if (err != null) {
            _errorMessage.value = err
        }
    }

    fun dismissDialog() {
        if (!_downloading.value) {
            _showDialog.value = false
        }
    }
}
