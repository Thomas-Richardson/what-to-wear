package com.richie.whattowear.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.richie.whattowear.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object LocationHelper {

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Where the phone is now, roughly, with a town name. Null if it can't tell. */
    suspend fun here(context: Context): Place? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOfNotNull(
            if (Build.VERSION.SDK_INT >= 31) LocationManager.FUSED_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        // Try each source in turn: Wi-Fi and phone masts are quick, GPS is the fallback (slow indoors).
        var fresh: Location? = null
        for (provider in providers) {
            fresh = withTimeoutOrNull(10_000) { currentLocation(context, manager, provider) }
            if (fresh != null) break
        }
        val location = fresh ?: lastKnown(manager, providers) ?: return null
        return Place(townName(context, location), location.latitude, location.longitude)
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context, manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(
                manager, provider, signal, ContextCompat.getMainExecutor(context),
            ) { location -> if (continuation.isActive) continuation.resume(location) }
        }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }

    @Suppress("DEPRECATION")
    private suspend fun townName(context: Context, location: Location): String = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            Geocoder(context, Locale.UK).getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull() ?: String.format(Locale.US, "%.2f, %.2f", location.latitude, location.longitude)
    }
}
