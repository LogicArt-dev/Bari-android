package com.logicart.bari

import android.app.Application
import com.logicart.bari.core.storage.AndroidStorageRepository

class BariApplication : Application() {
    val storageRepository: AndroidStorageRepository by lazy {
        AndroidStorageRepository(this)
    }
}

