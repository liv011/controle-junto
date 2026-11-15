package br.ucsal.controlejunto.service;

import br.ucsal.controlejunto.domain.Orcamento;
import br.ucsal.controlejunto.dto.OrcamentoDTOs.*;
import br.ucsal.controlejunto.repository.DividaRepository;
import br.ucsal.controlejunto.repository.GastoRepository;
import br.ucsal.controlejunto.repository.NotificacaoRepository;
import br.ucsal.controlejunto.repository.OrcamentoRepository;
import br.ucsal.controlejunto.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Orçamento mensal (RF-013), alerta de 90% (RN-004) e dashboard (RF-011). */
@Service
public class OrcamentoService {

    /** RN-004: alerta quando o gasto passa de 90% do orçamento. */
    public static final double LIMITE_ALERTA = 90.0;

    private final OrcamentoRepository orcamentos;
    private final GastoRepository gastos;
    private final UsuarioRepository usuarios;
    private final DividaRepository dividas;
    private final NotificacaoRepository notificacoes;

    public OrcamentoService(OrcamentoRepository orcamentos, GastoRepository gastos, UsuarioRepository usuarios,
                            DividaRepository dividas, NotificacaoRepository notificacoes) {
        this.orcamentos = orcamentos;
        this.gastos = gastos;
        this.usuarios = usuarios;
        this.dividas = dividas;
        this.notificacoes = notificacoes;
    }

    @Transactional(readOnly = true)
    public OrcamentoResponse consultar(Long usuarioId, int mes, int ano) {
        YearMonth ym = YearMonth.of(ano, mes);
        BigDecimal total = gastos.somarNoPeriodo(usuarioId, ym.atDay(1), ym.atEndOfMonth());
        BigDecimal limite = orcamentos.findByUsuarioIdAndMesAndAno(usuarioId, mes, ano)
                .map(Orcamento::getValorLimite).orElse(null);
        double percentual = calcularPercentual(total, limite);
        return new OrcamentoResponse(mes, ano, limite, total, percentual, percentual > LIMITE_ALERTA);
    }

    @Transactional
    public OrcamentoResponse definir(Long usuarioId, OrcamentoRequest req) {
        CalculadoraDivisao.validarTotal(req.valorLimite());
        Orcamento orcamento = orcamentos.findByUsuarioIdAndMesAndAno(usuarioId, req.mes(), req.ano())
                .orElseGet(() -> new Orcamento(usuarios.getReferenceById(usuarioId), req.mes(), req.ano(), req.valorLimite()));
        orcamento.setValorLimite(req.valorLimite());
        orcamentos.save(orcamento);
        return consultar(usuarioId, req.mes(), req.ano());
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long usuarioId, int mes, int ano) {
        YearMonth ym = YearMonth.of(ano, mes);
        OrcamentoResponse orcamento = consultar(usuarioId, mes, ano);

        List<TotalCategoria> porCategoria = gastos.totalPorCategoria(usuarioId, ym.atDay(1), ym.atEndOfMonth())
                .stream()
                .map(linha -> new TotalCategoria((String) linha[0], (BigDecimal) linha[1]))
                .toList();

        List<TotalMes> ultimosMeses = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth m = ym.minusMonths(i);
            ultimosMeses.add(new TotalMes(m.getMonthValue(), m.getYear(),
                    gastos.somarNoPeriodo(usuarioId, m.atDay(1), m.atEndOfMonth())));
        }

        return new DashboardResponse(orcamento.totalGasto(), orcamento, porCategoria, ultimosMeses,
                dividas.totalAPagar(usuarioId), dividas.totalAReceber(usuarioId),
                notificacoes.countByUsuarioIdAndLidaFalse(usuarioId));
    }

    static double calcularPercentual(BigDecimal total, BigDecimal limite) {
        if (limite == null || limite.signum() == 0) {
            return 0.0;
        }
        return total.multiply(BigDecimal.valueOf(100))
                .divide(limite, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
