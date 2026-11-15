package br.ucsal.controlejunto.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** Orçamento mensal (RF-013, RN-004) e dashboard (RF-011). */
public final class OrcamentoDTOs {

    private OrcamentoDTOs() {
    }

    public record OrcamentoRequest(
            @Min(1) @Max(12) int mes,
            @Min(2000) int ano,
            @NotNull(message = "Informe o valor do orçamento.") BigDecimal valorLimite) {
    }

    /** alerta = true quando o gasto do mês passou de 90% do orçamento (RN-004). */
    public record OrcamentoResponse(int mes, int ano, BigDecimal valorLimite, BigDecimal totalGasto,
                                    double percentualUsado, boolean alerta) {
    }

    public record TotalCategoria(String categoria, BigDecimal total) {
    }

    public record TotalMes(int mes, int ano, BigDecimal total) {
    }

    public record DashboardResponse(BigDecimal totalMes,
                                    OrcamentoResponse orcamento,
                                    List<TotalCategoria> porCategoria,
                                    List<TotalMes> ultimosMeses,
                                    BigDecimal totalAPagarGrupos,
                                    BigDecimal totalAReceberGrupos,
                                    long notificacoesNaoLidas) {
    }
}
