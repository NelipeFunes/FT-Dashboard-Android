package br.dev.ftdash.data

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import br.dev.ftdash.protocol.Ft450Protocol

data class UsbEndpointInfo(
    val address: Int,
    val direction: String,
    val type: String,
    val maxPacketSize: Int,
) {
    override fun toString() = "0x%02x %s %s %dB".format(address, direction, type, maxPacketSize)
}

data class UsbInterfaceInfo(
    val id: Int,
    val interfaceClass: Int,
    val endpoints: List<UsbEndpointInfo>,
)

data class UsbDeviceInfo(
    val vendorId: Int,
    val productId: Int,
    val deviceName: String,
    val manufacturerName: String?,
    val productName: String?,
    val deviceClass: Int,
    val interfaces: List<UsbInterfaceInfo>,
    val hasPermission: Boolean,
) {
    val isFt450: Boolean
        get() = vendorId == Ft450Protocol.VID && productId == Ft450Protocol.PID

    val idHex: String get() = "%04x:%04x".format(vendorId, productId)

    val label: String
        get() = productName ?: manufacturerName ?: deviceName
}

/**
 * O que a multimídia enxerga no barramento USB.
 *
 * Existe por um motivo prático: a maior incógnita do projeto é se a porta USB
 * da central faz host de verdade ou só monta pendrive. Sem isto, uma ida ao
 * carro que falha volta sem informação nenhuma — "não funcionou". Com isto,
 * volta sabendo se a FT sequer aparece, se aparece com outro VID/PID, se falta
 * permissão, ou se o barramento está vazio (que é o caso sem solução por
 * software).
 */
data class UsbBusReport(
    val hostFeatureDeclared: Boolean,
    val devices: List<UsbDeviceInfo>,
) {
    val ft450: UsbDeviceInfo? get() = devices.firstOrNull { it.isFt450 }

    /** Uma linha, para caber na barra de status. */
    val summary: String
        get() = when {
            !hostFeatureDeclared -> "aparelho não declara USB host"
            devices.isEmpty() -> "barramento vazio"
            ft450 == null -> "${devices.size} device(s), nenhum é a FT450"
            ft450?.hasPermission == false -> "FT450 encontrada, sem permissão"
            else -> "FT450 pronta"
        }
}

object UsbDiagnostics {

    fun scan(context: Context): UsbBusReport {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return UsbBusReport(hostFeatureDeclared = false, devices = emptyList())

        val hostFeature = context.packageManager
            .hasSystemFeature(PackageManager.FEATURE_USB_HOST)

        val devices = manager.deviceList.values.map { describe(it, manager) }
        return UsbBusReport(hostFeature, devices)
    }

    private fun describe(device: UsbDevice, manager: UsbManager) = UsbDeviceInfo(
        vendorId = device.vendorId,
        productId = device.productId,
        deviceName = device.deviceName,
        manufacturerName = runCatching { device.manufacturerName }.getOrNull(),
        productName = runCatching { device.productName }.getOrNull(),
        deviceClass = device.deviceClass,
        interfaces = (0 until device.interfaceCount).map { i ->
            val iface = device.getInterface(i)
            UsbInterfaceInfo(
                id = iface.id,
                interfaceClass = iface.interfaceClass,
                endpoints = (0 until iface.endpointCount).map { e ->
                    val ep = iface.getEndpoint(e)
                    UsbEndpointInfo(
                        address = ep.address,
                        direction = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT",
                        type = when (ep.type) {
                            UsbConstants.USB_ENDPOINT_XFER_BULK -> "bulk"
                            UsbConstants.USB_ENDPOINT_XFER_INT -> "int"
                            UsbConstants.USB_ENDPOINT_XFER_ISOC -> "isoc"
                            UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "ctrl"
                            else -> "?"
                        },
                        maxPacketSize = ep.maxPacketSize,
                    )
                },
            )
        },
        hasPermission = manager.hasPermission(device),
    )
}
