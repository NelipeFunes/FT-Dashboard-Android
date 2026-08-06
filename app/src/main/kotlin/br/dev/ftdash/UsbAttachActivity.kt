package br.dev.ftdash

import android.app.Activity
import android.os.Bundle

/**
 * Recebe o aviso de "FT450 plugada" e some.
 *
 * Existe por causa de um problema de segurança encontrado no primeiro teste de
 * campo: o `intent-filter` de `USB_DEVICE_ATTACHED` estava na MainActivity, e a
 * cada reconexão do barramento o Android trazia o painel para a frente — **por
 * cima da câmera de ré, com o carro andando de ré**. Um painel bonito não vale
 * o para-choque de ninguém.
 *
 * Esta activity é invisível e fecha imediatamente. O que importa não é o que ela
 * faz, e sim o que o sistema faz por ela: declarar o `device_filter` num
 * `intent-filter` é o que concede a permissão de USB de forma **persistente**,
 * sem diálogo. Perder esse efeito colateral seria voltar a ter o diálogo de
 * permissão aparecendo sozinho no meio do trânsito.
 *
 * O app em si não é aberto por aqui. Para o painel subir junto com a central,
 * marque o FT Dash como app de inicialização nas configurações dela.
 */
class UsbAttachActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
