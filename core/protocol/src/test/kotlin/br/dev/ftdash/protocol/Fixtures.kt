package br.dev.ftdash.protocol

/**
 * Frames reais capturados da FT450 do carro, um por linha em hex.
 *
 * - `road-107`: 24.250 frames de estrada, config de 107 B (a atual do carro)
 * - `motor-111`: 6.982 frames com o motor ligado, config de 111 B
 * - `bench-111`: 2.467 frames de bancada, config de 111 B
 */
object Fixtures {

    const val ROAD_107 = "fixtures/ft-frames-road-107.txt"
    const val MOTOR_111 = "fixtures/ft-frames-motor-111.txt"
    const val BENCH_111 = "fixtures/ft-frames-111.txt"

    val all = listOf(ROAD_107, MOTOR_111, BENCH_111)

    fun load(name: String): List<ByteArray> {
        val stream = Fixtures::class.java.classLoader.getResourceAsStream(name)
            ?: error("fixture não encontrado no classpath: $name")
        return stream.bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { Ft450Protocol.hex(it) }
                .toList()
        }
    }
}
