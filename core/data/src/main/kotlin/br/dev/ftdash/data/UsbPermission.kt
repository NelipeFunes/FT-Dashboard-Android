package br.dev.ftdash.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Pede ao usuário a permissão de acesso à FT450.
 *
 * Faltava por completo: a fonte USB só constatava "sem permissão" e desistia,
 * sem nunca perguntar. Numa central de carro o ideal é nem chegar aqui — o
 * `usb_device_filter.xml` do manifest concede permissão persistente quando o
 * aparelho é plugado com o app instalado. Este caminho cobre o resto: app
 * aberto depois do cabo já plugado, ou central que ignora o intent-filter.
 *
 * Detalhes que quebram silenciosamente se estiverem errados:
 * - o `PendingIntent` precisa ser **mutável**, porque o sistema anexa nele o
 *   device e o resultado antes de devolver;
 * - o receiver é registrado como **não exportado**: o broadcast chega através
 *   do nosso próprio `PendingIntent`, então a origem é o próprio app.
 */
object UsbPermission {

    private const val ACTION = "br.dev.ftdash.USB_PERMISSION"

    suspend fun request(context: Context, device: UsbDevice): Boolean =
        suspendCancellableCoroutine { cont ->
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager

            if (manager.hasPermission(device)) {
                cont.resume(true)
                return@suspendCancellableCoroutine
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action != ACTION) return
                    runCatching { ctx.unregisterReceiver(this) }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (cont.isActive) cont.resume(granted)
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pending = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION).setPackage(context.packageName),
                flags,
            )

            cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
            manager.requestPermission(device, pending)
        }
}
