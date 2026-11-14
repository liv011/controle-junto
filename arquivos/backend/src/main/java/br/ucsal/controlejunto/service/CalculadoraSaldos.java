package br.ucsal.controlejunto.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RF-008: calcula o saldo de cada participante a partir das dívidas pendentes.
 * saldo = quanto tem a receber - quanto deve.
 * Pela forma como as dívidas são criadas, a soma de todos os saldos é sempre zero.
 */
public final class CalculadoraSaldos {

    private CalculadoraSaldos() {
    }

    /** Uma dívida pendente simplificada: quem deve, para quem e quanto. */
    public record DividaPendente(Long devedorId, Long credorId, BigDecimal valor) {
    }

    /** Totais de um participante. */
    public record Saldo(BigDecimal aReceber, BigDecimal aPagar) {
        public BigDecimal saldo() {
            return aReceber.subtract(aPagar);
        }
    }

    public static Map<Long, Saldo> calcular(List<Long> participantes, List<DividaPendente> dividas) {
        Map<Long, BigDecimal> receber = new LinkedHashMap<>();
        Map<Long, BigDecimal> pagar = new LinkedHashMap<>();
        for (Long id : participantes) {
            receber.put(id, BigDecimal.ZERO);
            pagar.put(id, BigDecimal.ZERO);
        }
        for (DividaPendente d : dividas) {
            receber.merge(d.credorId(), d.valor(), BigDecimal::add);
            pagar.merge(d.devedorId(), d.valor(), BigDecimal::add);
        }
        Map<Long, Saldo> saldos = new LinkedHashMap<>();
        for (Long id : receber.keySet()) {
            saldos.put(id, new Saldo(receber.get(id), pagar.getOrDefault(id, BigDecimal.ZERO)));
        }
        return saldos;
    }
}
