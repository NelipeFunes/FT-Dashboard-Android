package br.dev.ftdash.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Velocidade pelo GPS do próprio Android.
 *
 * Usa o **`LocationManager` do framework, não o FusedLocationProvider**: boa
 * parte das multimídias chinesas não traz Google Play Services, e nesses
 * aparelhos o Fused simplesmente não inicializa. `LocationManager` é framework
 * puro e funciona em qualquer Android.
 *
 * Duas defesas contra o comportamento real do GPS:
 *  - nem toda fixação traz velocidade Doppler (`hasSpeed()`); quando não vem,
 *    deriva de distância/tempo entre fixações;
 *  - fixação parada há mais de [STALE_MS] vira `kmh = null`, para o painel
 *    mostrar `--` em vez de um "0 km/h" mentiroso quando o sinal cai num túnel.
 */
class GpsSpeedSource(private val context: Context) : SpeedSource {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override fun stream(): Flow<SpeedFix> = callbackFlow {
        if (!hasPermission()) {
            trySend(SpeedFix(System.currentTimeMillis(), null, hasGpsFix = false))
            awaitClose { }
            return@callbackFlow
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        var lastLocation: Location? = null
        var filtered: Float? = null

        val listener = LocationListener { location ->
            val raw = if (location.hasSpeed()) {
                location.speed * 3.6f
            } else {
                val prev = lastLocation
                val dtMs = prev?.let { location.time - it.time } ?: 0L
                if (prev != null && dtMs in 1..10_000) {
                    location.distanceTo(prev) / (dtMs / 1000f) * 3.6f
                } else {
                    null
                }
            }
            lastLocation = location

            if (raw != null && raw.isFinite() && raw >= 0f) {
                // filtro exponencial: o suficiente para o mostrador não tremer,
                // leve o bastante para não atrasar uma frenagem
                filtered = filtered?.let { it + SMOOTHING * (raw - it) } ?: raw
                trySend(SpeedFix(System.currentTimeMillis(), filtered, hasGpsFix = true))
            }
        }

        try {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            trySend(SpeedFix(System.currentTimeMillis(), null, hasGpsFix = false))
        } catch (e: IllegalArgumentException) {
            // provedor GPS ausente no aparelho
            trySend(SpeedFix(System.currentTimeMillis(), null, hasGpsFix = false))
        }

        awaitClose { manager.removeUpdates(listener) }
    }

    companion object {
        /** Pedimos 200 ms; a maioria das multimídias entrega 1 Hz mesmo assim. */
        const val MIN_INTERVAL_MS = 200L
        const val SMOOTHING = 0.4f

        /** Sem fixação por mais que isso, a velocidade vira desconhecida. */
        const val STALE_MS = 2_000L
    }
}
