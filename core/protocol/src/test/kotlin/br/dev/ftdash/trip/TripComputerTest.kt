package br.dev.ftdash.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripComputerTest {

    /** 4 bicos de 320 cc/min e tanque de 45 L — um Civic aspirado típico. */
    private val setup = FuelSetup(tankLiters = 45.0, injectorFlowCcMin = 320.0, injectorCount = 4)

    @Test
    fun `100 km por hora durante uma hora anda 100 km`() {
        val trip = TripComputer()
        var t = 0L
        // fixações de 1 Hz por uma hora
        repeat(3601) {
            trip.onSpeedFix(t, 100f)
            t += 1000
        }
        assertEquals(100.0, trip.totalKm, 0.2)
        assertEquals(100.0, trip.tripKm, 0.2)
    }

    @Test
    fun `parado nao anda`() {
        val trip = TripComputer()
        var t = 0L
        repeat(100) {
            trip.onSpeedFix(t, 0f)
            t += 1000
        }
        assertEquals(0.0, trip.totalKm, 0.0)
    }

    @Test
    fun `buraco no GPS nao inventa distancia`() {
        val trip = TripComputer()
        trip.onSpeedFix(0, 100f)
        trip.onSpeedFix(1000, 100f)
        val afterTwoFixes = trip.totalKm

        // túnel de 10 minutos: o intervalo é grande demais para ser integrado
        trip.onSpeedFix(1000 + 600_000, 100f)
        assertEquals("não pode extrapolar o buraco", afterTwoFixes, trip.totalKm, 1e-9)
    }

    @Test
    fun `parcial zera sem mexer no total`() {
        val trip = TripComputer()
        var t = 0L
        repeat(3601) { trip.onSpeedFix(t, 60f); t += 1000 }
        val total = trip.totalKm
        assertTrue(total > 59.0)

        trip.resetTrip()
        assertEquals(0.0, trip.tripKm, 0.0)
        assertEquals(total, trip.totalKm, 1e-9)
    }

    @Test
    fun `consumo a duty constante bate com a conta de vazao`() {
        val trip = TripComputer()
        // 4 bicos × 320 cc/min × 25% = 320 cc/min = 19,2 L/h.
        // Em uma hora, 19,2 litros.
        var t = 0L
        repeat(3601) {
            trip.onInjection(t, 25f, setup)
            t += 1000
        }
        assertEquals(19.2, trip.tankUsedLiters, 0.05)
    }

    @Test
    fun `tanque conta para tras e nao passa de zero`() {
        val trip = TripComputer()
        assertEquals(45.0, trip.remainingLiters(setup)!!, 1e-9)
        assertEquals(1.0f, trip.remainingFraction(setup)!!, 1e-6f)

        // meia hora a duty alto queima bastante
        var t = 0L
        repeat(1801) { trip.onInjection(t, 50f, setup); t += 1000 }
        val remaining = trip.remainingLiters(setup)!!
        assertEquals(45.0 - 19.2, remaining, 0.1)

        // esvaziando de vez, o mostrador para em zero em vez de ficar negativo
        repeat(20_000) { trip.onInjection(t, 80f, setup); t += 1000 }
        assertEquals(0.0, trip.remainingLiters(setup)!!, 0.0)
        assertEquals(0.0f, trip.remainingFraction(setup)!!, 0.0f)
    }

    @Test
    fun `enchi o tanque zera o consumo`() {
        val trip = TripComputer()
        var t = 0L
        repeat(1801) { trip.onInjection(t, 50f, setup); t += 1000 }
        assertTrue(trip.tankUsedLiters > 10)

        trip.fillTank()
        assertEquals(0.0, trip.tankUsedLiters, 0.0)
        assertEquals(45.0, trip.remainingLiters(setup)!!, 1e-9)
    }

    @Test
    fun `sem configuracao nao inventa numero de tanque`() {
        val trip = TripComputer()
        val incomplete = FuelSetup(tankLiters = 45.0)
        assertNull(trip.remainingLiters(incomplete))
        assertNull(trip.remainingFraction(incomplete))

        // e também não acumula consumo sem saber a vazão
        var t = 0L
        repeat(100) { trip.onInjection(t, 50f, incomplete); t += 1000 }
        assertEquals(0.0, trip.tankUsedLiters, 0.0)
    }

    @Test
    fun `estado sobrevive a ida e volta`() {
        val trip = TripComputer()
        var t = 0L
        repeat(600) { trip.onSpeedFix(t, 80f); trip.onInjection(t, 30f, setup); t += 1000 }

        val saved = trip.state
        val restored = TripComputer(saved)
        assertEquals(trip.totalKm, restored.totalKm, 1e-9)
        assertEquals(trip.tripKm, restored.tripKm, 1e-9)
        assertEquals(trip.tankUsedLiters, restored.tankUsedLiters, 1e-9)
        assertEquals(trip.tripFuelLiters, restored.tripFuelLiters, 1e-9)
    }

    @Test
    fun `media bate com a conta de km por litro`() {
        val trip = TripComputer()
        // 60 km/h com duty de 12,5%: 4 × 320 × 0,125 = 160 cc/min = 9,6 L/h.
        // 60 km/h ÷ 9,6 L/h = 6,25 km/L.
        var t = 0L
        repeat(3601) {
            trip.onSpeedFix(t, 60f)
            trip.onInjection(t, 12.5f, setup)
            t += 1000
        }
        assertEquals(6.25, trip.averageKmPerLiter!!, 0.05)
    }

    @Test
    fun `media so aparece depois de meio litro`() {
        val trip = TripComputer()
        var t = 0L
        // poucos segundos: distância e consumo mínimos, a divisão ainda é ruído
        repeat(20) {
            trip.onSpeedFix(t, 60f)
            trip.onInjection(t, 12.5f, setup)
            t += 1000
        }
        assertNull("não pode publicar média com consumo desprezível", trip.averageKmPerLiter)
    }

    @Test
    fun `encher o tanque so mexe no nivel`() {
        val trip = TripComputer()
        var t = 0L
        repeat(3601) {
            trip.onSpeedFix(t, 60f)
            trip.onInjection(t, 12.5f, setup)
            t += 1000
        }
        val totalBefore = trip.totalKm
        val tripBefore = trip.tripKm
        trip.fillTank()

        assertEquals(45.0, trip.remainingLiters(setup)!!, 1e-9)
        assertEquals("o total não é afetado", totalBefore, trip.totalKm, 1e-9)
        assertEquals("o parcial não é afetado", tripBefore, trip.tripKm, 1e-9)
    }

    @Test
    fun `instantanea converge para o consumo do momento`() {
        val trip = TripComputer()
        var t = 0L
        // mesma condição do teste da média: deveria dar os mesmos 6,25 km/L
        repeat(400) {
            trip.onSpeedFix(t, 60f)
            trip.onInjection(t, 12.5f, setup)
            t += 100
        }
        assertEquals(6.25, trip.instantKmPerLiter!!, 0.2)
    }

    @Test
    fun `instantanea satura em corte de combustivel`() {
        val trip = TripComputer()
        var t = 0L
        // descendo a serra: andando rápido com o bico fechado
        repeat(400) {
            trip.onSpeedFix(t, 80f)
            trip.onInjection(t, 0f, setup)
            t += 100
        }
        assertEquals(
            "consumo zero daria infinito; satura no teto",
            TripComputer.MAX_INSTANT_KM_L,
            trip.instantKmPerLiter!!,
            0.001,
        )
    }

    @Test
    fun `instantanea nao e numero com o carro parado`() {
        val trip = TripComputer()
        var t = 0L
        repeat(400) {
            trip.onSpeedFix(t, 0f)
            trip.onInjection(t, 8f, setup)
            t += 100
        }
        assertNull("km/L parado não significa nada", trip.instantKmPerLiter)
    }

    @Test
    fun `instantanea acompanha mudanca de regime`() {
        val trip = TripComputer()
        var t = 0L
        // cruzeiro econômico
        repeat(400) { trip.onSpeedFix(t, 90f); trip.onInjection(t, 10f, setup); t += 100 }
        val cruising = trip.instantKmPerLiter!!

        // pé no fundo: mesma velocidade, muito mais combustível
        repeat(400) { trip.onSpeedFix(t, 90f); trip.onInjection(t, 60f, setup); t += 100 }
        val flooring = trip.instantKmPerLiter!!

        assertTrue("acelerando tem que consumir mais: $cruising -> $flooring", flooring < cruising / 3)
    }

    @Test
    fun `estado de versao antiga nao produz media absurda`() {
        // Como ficava o disco antes de existir o combustível da viagem: parcial
        // acumulado, denominador da média zerado. Sem tratamento, os primeiros
        // 0,5 L dariam 36,5 / 0,5 = 73 km/L, e ficaria assim até zerar o parcial.
        val legacy = TripState(totalKm = 1200.0, tripKm = 36.5, tankUsedLiters = 6.7)
        val trip = TripComputer()
        trip.restore(legacy)

        assertEquals("o odômetro total não pode ser perdido", 1200.0, trip.totalKm, 1e-9)
        assertEquals("o nível do tanque não é afetado", 6.7, trip.tankUsedLiters, 1e-9)
        assertEquals("o parcial recomeça", 0.0, trip.tripKm, 0.0)
        assertNull(trip.averageKmPerLiter)
    }

    @Test
    fun `estado coerente e restaurado inteiro`() {
        val saved = TripState(
            totalKm = 1200.0,
            tripKm = 55.0,
            tripFuelLiters = 6.7,
            tankUsedLiters = 6.7,
        )
        val trip = TripComputer()
        trip.restore(saved)

        assertEquals(6.7, trip.tankUsedLiters, 1e-9)
        assertEquals(55.0, trip.tripKm, 1e-9)
        assertEquals(55.0 / 6.7, trip.averageKmPerLiter!!, 1e-9)
    }

    @Test
    fun `abastecimento parcial soma ao nivel sem encher`() {
        val trip = TripComputer()
        var t = 0L
        // queima ~19,2 L: sobram ~25,8 de 45
        repeat(1801) { trip.onInjection(t, 50f, setup); t += 1000 }
        val before = trip.remainingLiters(setup)!!
        assertEquals(45.0 - 19.2, before, 0.1)

        trip.addFuel(10.0, setup)
        assertEquals(before + 10.0, trip.remainingLiters(setup)!!, 0.1)
    }

    @Test
    fun `abastecimento parcial nao passa da capacidade`() {
        val trip = TripComputer()
        var t = 0L
        repeat(600) { trip.onInjection(t, 50f, setup); t += 1000 }  // ~6,4 L
        // 20 litros num tanque com espaço para ~6,4: dá cheio, e não sobra
        // "crédito" escondido que faria o mostrador mentir depois
        trip.addFuel(20.0, setup)
        assertEquals(45.0, trip.remainingLiters(setup)!!, 1e-9)
        assertEquals(0.0, trip.tankUsedLiters, 1e-9)

        // e a partir daqui o consumo volta a descer do cheio
        repeat(600) { trip.onInjection(t, 50f, setup); t += 1000 }
        assertTrue(trip.remainingLiters(setup)!! < 45.0)
    }

    @Test
    fun `abastecer nao mexe na media`() {
        val trip = TripComputer()
        var t = 0L
        repeat(3601) {
            trip.onSpeedFix(t, 60f)
            trip.onInjection(t, 12.5f, setup)
            t += 1000
        }
        val average = trip.averageKmPerLiter!!

        // Este é o ponto do contador duplo: se o nível do tanque fosse também o
        // denominador da média, somar 10 litros faria a média saltar sozinha.
        trip.addFuel(10.0, setup)
        assertEquals("combustível no tanque não muda o que o carro já fez", average, trip.averageKmPerLiter!!, 1e-9)

        trip.fillTank()
        assertEquals("encher também não", average, trip.averageKmPerLiter!!, 1e-9)
    }

    @Test
    fun `zerar o parcial zera a media junto`() {
        val trip = TripComputer()
        var t = 0L
        repeat(3601) {
            trip.onSpeedFix(t, 60f)
            trip.onInjection(t, 12.5f, setup)
            t += 1000
        }
        assertTrue(trip.averageKmPerLiter != null)
        val tankBefore = trip.remainingLiters(setup)!!

        trip.resetTrip()
        assertNull("média e parcial são a mesma viagem", trip.averageKmPerLiter)
        assertEquals(0.0, trip.tripKm, 0.0)
        assertEquals("o tanque não é afetado", tankBefore, trip.remainingLiters(setup)!!, 1e-9)
    }

    @Test
    fun `abastecer valor invalido nao faz nada`() {
        val trip = TripComputer()
        var t = 0L
        repeat(600) { trip.onInjection(t, 50f, setup); t += 1000 }
        val before = trip.remainingLiters(setup)!!
        trip.addFuel(0.0, setup)
        trip.addFuel(-5.0, setup)
        assertEquals(before, trip.remainingLiters(setup)!!, 1e-9)
    }

    @Test
    fun `vazao e corrigida pela pressao diferencial`() {
        // Escoamento por orifício vai com a raiz da diferença de pressão:
        // a 4 bar de diferencial, um bico de 3 bar entrega √(4/3) = 1,155x.
        val nominal = TripComputer()
        val corrigido = TripComputer()
        var t = 0L
        repeat(601) {
            nominal.onInjection(t, 30f, setup)
            corrigido.onInjection(t, 30f, setup, differentialBar = 4.0)
            t += 1000
        }
        val razao = corrigido.tankUsedLiters / nominal.tankUsedLiters
        assertEquals(kotlin.math.sqrt(4.0 / 3.0), razao, 0.005)
    }

    @Test
    fun `no diferencial nominal nada muda`() {
        val a = TripComputer()
        val b = TripComputer()
        var t = 0L
        repeat(601) {
            a.onInjection(t, 30f, setup)
            b.onInjection(t, 30f, setup, differentialBar = 3.0)
            t += 1000
        }
        assertEquals(a.tankUsedLiters, b.tankUsedLiters, 1e-9)
    }

    @Test
    fun `leitura de pressao absurda nao corrige`() {
        // Sensor desconectado ou canal não configurado: melhor usar a vazão
        // nominal do que multiplicar por um número sem sentido.
        val nominal = TripComputer()
        val comLixo = TripComputer()
        var t = 0L
        repeat(601) {
            nominal.onInjection(t, 30f, setup)
            comLixo.onInjection(t, 30f, setup, differentialBar = 0.0)
            t += 1000
        }
        assertEquals(nominal.tankUsedLiters, comLixo.tankUsedLiters, 1e-9)
    }

    @Test
    fun `vacuo alto consome mais que carga para o mesmo duty`() {
        // Regulador de pressão fixa: em marcha lenta o diferencial é maior
        // (linha 3,3 bar contra coletor em -0,9), então o mesmo tempo de bico
        // aberto entrega mais combustível do que a plena carga.
        val lenta = TripComputer()
        val carga = TripComputer()
        var t = 0L
        repeat(601) {
            lenta.onInjection(t, 20f, setup, differentialBar = 3.30 - (-0.90))
            carga.onInjection(t, 20f, setup, differentialBar = 3.30 - (-0.01))
            t += 1000
        }
        assertTrue(
            "mais vácuo tem que dar mais vazão: ${lenta.tankUsedLiters} vs ${carga.tankUsedLiters}",
            lenta.tankUsedLiters > carga.tankUsedLiters,
        )
        // √(4,20/3,31) = 1,127 — ou seja, 13% a mais de vazão em marcha lenta
        assertEquals(1.127, lenta.tankUsedLiters / carga.tankUsedLiters, 0.01)
    }

    @Test
    fun `duty invalido e ignorado`() {
        val trip = TripComputer()
        var t = 0L
        repeat(100) { trip.onInjection(t, 150f, setup); t += 1000 }
        repeat(100) { trip.onInjection(t, -5f, setup); t += 1000 }
        assertEquals(0.0, trip.tankUsedLiters, 0.0)
    }
}
