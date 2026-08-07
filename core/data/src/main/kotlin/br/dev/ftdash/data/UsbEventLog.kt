package br.dev.ftdash.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Histórico do que aconteceu no USB, com hora.
 *
 * Existe porque o painel só mostra o **agora**, e o defeito que estamos
 * caçando é justamente uma sequência: conecta, transmite, cai. Quando o
 * usuário olha a tela, o momento que interessa já passou — e o logcat não
 * existe dentro do carro.
 *
 * Duas cópias, de propósito:
 *
 * - **memória** ([entries]) — a aba USB desenha as últimas linhas, para
 *   fotografar com o celular ali mesmo no carro;
 * - **arquivo** (`Android/data/br.dev.ftdash/files/usb-log.txt`) — sobrevive a
 *   fechar o app e à queda de energia da central, e é o que se lê depois com
 *   calma. Pasta pública do app: dá para pegar por cabo ou gerenciador de
 *   arquivos, sem root.
 *
 * O arquivo é cortado pela metade ao passar de [MAX_FILE_BYTES]. Um log que
 * enche o disco da central seria pior que não ter log.
 */
class UsbEventLog(context: Context) {

    data class Entry(val atMs: Long, val text: String) {
        val clock: String get() = TIME.format(Date(atMs))
        override fun toString() = "$clock  $text"
    }

    private val file: File? = runCatching {
        File(context.getExternalFilesDir(null) ?: context.filesDir, "usb-log.txt")
    }.getOrNull()

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())

    /** Mais recente primeiro — é o que se quer ver de relance. */
    val entries: StateFlow<List<Entry>> = _entries

    /** Caminho mostrado na tela, para o usuário saber onde procurar. */
    val path: String? get() = file?.absolutePath

    @Synchronized
    fun add(text: String) {
        val entry = Entry(System.currentTimeMillis(), text)
        _entries.value = (listOf(entry) + _entries.value).take(MAX_MEMORY_ENTRIES)
        appendToFile(entry)
    }

    private fun appendToFile(entry: Entry) {
        val f = file ?: return
        runCatching {
            if (f.length() > MAX_FILE_BYTES) {
                // Corta pela metade em vez de apagar: perder o começo de um
                // teste longo é aceitável, perder tudo não.
                val kept = f.readLines().let { it.drop(it.size / 2) }
                f.writeText(kept.joinToString("\n", postfix = "\n"))
            }
            f.appendText("${DAY.format(Date(entry.atMs))} ${entry}\n")
        }
    }

    /** Só o log em memória — o arquivo é o histórico e não se apaga daqui. */
    fun clearScreen() {
        _entries.value = emptyList()
    }

    companion object {
        const val MAX_MEMORY_ENTRIES = 60
        const val MAX_FILE_BYTES = 256L * 1024

        private val TIME = SimpleDateFormat("HH:mm:ss", Locale.US)
        private val DAY = SimpleDateFormat("dd/MM", Locale.US)
    }
}
