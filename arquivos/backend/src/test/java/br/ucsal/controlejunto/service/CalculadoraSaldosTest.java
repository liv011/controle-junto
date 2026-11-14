package br.ucsal.controlejunto.service;

import br.ucsal.controlejunto.service.CalculadoraSaldos.DividaPendente;
import br.ucsal.controlejunto.service.CalculadoraSaldos.Saldo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculadoraSaldosTest {

    @Test
    void calculaQuemDeveEQuemRecebeESomaDosSaldosEZero() {
        // Ana (1) pagou 90 para 3 pessoas: Bruno (2) e Carla (3) devem 30 cada.
        List<DividaPendente> dividas = List.of(
                new DividaPendente(2L, 1L, new BigDecimal("30.00")),
                new DividaPendente(3L, 1L, new BigDecimal("30.00")));

        Map<Long, Saldo> saldos = CalculadoraSaldos.calcular(List.of(1L, 2L, 3L), dividas);

        assertEquals(new BigDecimal("60.00"), saldos.get(1L).saldo());
        assertEquals(new BigDecimal("-30.00"), saldos.get(2L).saldo());
        assertEquals(new BigDecimal("-30.00"), saldos.get(3L).saldo());

        BigDecimal soma = saldos.values().stream().map(Saldo::saldo).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, soma.signum());
    }

    @Test
    void semDividasTodosFicamZerados() {
        Map<Long, Saldo> saldos = CalculadoraSaldos.calcular(List.of(1L, 2L), List.of());
        assertEquals(0, saldos.get(1L).saldo().signum());
        assertEquals(0, saldos.get(2L).saldo().signum());
    }
}
