package com.revline.tracker

import android.app.Application
import com.revline.tracker.service.AutoDetectManager
import com.revline.tracker.util.AppSettings
import org.osmdroid.config.Configuration
import java.io.File

/**
 * App entry point. Initializes OSMDroid's [Configuration] once, before any `MapView`
 * is ever constructed — this guarantees the OpenStreetMap-required HTTP user agent is
 * set in time (some devices fail tile loads / render a blank map if it's set late) and
 * pins the tile cache to app-private storage that's always writable.
 */
class RevlineApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val config = Configuration.getInstance()
        config.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))

        // Required by the OSM tile policy; must be set before the first tile request.
        config.userAgentValue = packageName

        // App-private, always-writable cache dirs (avoids OEM storage-path quirks).
        val basePath = File(cacheDir, "osmdroid").apply { mkdirs() }
        val tileCache = File(basePath, "tiles").apply { mkdirs() }
        config.osmdroidBasePath = basePath
        config.osmdroidTileCache = tileCache

        // Activity-transition subscriptions don't survive a reboot or a force-stop, so
        // re-register whenever the app starts and auto-detect is still switched on.
        if (AppSettings.isAutoDetectEnabled(this) && AutoDetectManager.hasPermissions(this)) {
            AutoDetectManager.start(this)
        }
    }
}
