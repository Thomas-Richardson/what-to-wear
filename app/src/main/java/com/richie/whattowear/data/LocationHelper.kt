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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.richie.whattowear.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** What happened when the app looked for where you are. */
sealed interface LocationResult {
    data class Found(val place: Place) : LocationResult
    data object NoPermission : LocationResult
    data object LocationOff : LocationResult

    /** Everything was allowed and switched on, but no reading came back. [detail] says what was tried. */
    data class NoFix(val detail: String) : LocationResult
}

object LocationHelper {

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Where the phone is now, roughly, with a town name. Null if it can't tell. */
    suspend fun here(context: Context): Place? = (find(context) as? LocationResult.Found)?.place

    /**
     * Finds where the phone is. Google's location service comes first: it combines Wi-Fi, phone masts
     * and GPS, so it works indoors. Android's own location sources are the fallback, for phones without it.
     */
    suspend fun find(context: Context): LocationResult {
        if (!hasPermission(context)) return LocationResult.NoPermission
        val manager = context.getSystemService(LocationManager::class.java)
            ?: return LocationResult.NoFix("Android's location service isn't available")
        if (!LocationManagerCompat.isLocationEnabled(manager)) return LocationResult.LocationOff

        val tried = mutableListOf<String>()
        val location = fromGoogle(context, tried) ?: fromAndroid(context, manager, tried)
            ?: return LocationResult.NoFix(tried.joinToString(" · "))
        return LocationResult.Found(Place(townName(context, location), location.latitude, location.longitude))
    }

    @SuppressLint("MissingPermission") // checked in find()
    private suspend fun fromGoogle(context: Context, tried: MutableList<String>): Location? {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            tried += "Google location service not available"
            return null
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            // Approximate is all the app asks for, and plenty for the weather.
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            // A reading from the last half hour is fine, which makes it instant most of the time.
            .setMaxUpdateAgeMillis(30 * 60_000L)
            .setDurationMillis(30_000L)
            .build()
        val tokens = CancellationTokenSource()
        val current = withTimeoutOrNull(35_000) { client.getCurrentLocation(request, tokens.token).awaitOrNull() }
        if (current != null) return current
        tokens.cancel()
        val last = withTimeoutOrNull(5_000) { client.lastLocation.awaitOrNull() }
        tried += if (last == null) "Google location service: no reading after 30 seconds" else "Google: used last known reading"
        return last
    }

    /** Android's own sources: Wi-Fi and phone masts first, GPS last (slow indoors). */
    private suspend fun fromAndroid(context: Context, manager: LocationManager, tried: MutableList<String>): Location? {
        val providers = listOfNotNull(
            if (Build.VERSION.SDK_INT >= 31) LocationManager.FUSED_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) tried += "Android's location sources all off"
        for (provider in providers) {
            val fresh = withTimeoutOrNull(10_000) { currentLocation(context, manager, provider) }
            if (fresh != null) return fresh
            tried += "Android $provider: no reading after 10 seconds"
        }
        return lastKnown(manager, providers)
    }

    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context, manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            runCatching {
                LocationManagerCompat.getCurrentLocation(
                    manager, provider, signal, ContextCompat.getMainExecutor(context),
                ) { location -> if (continuation.isActive) continuation.resume(location) }
            }.onFailure { if (continuation.isActive) continuation.resume(null) }
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
