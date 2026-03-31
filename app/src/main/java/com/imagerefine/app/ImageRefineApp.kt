package com.imagerefine.app

import android.app.Application
import com.imagerefine.app.data.db.AppDatabase

class ImageRefineApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
}
