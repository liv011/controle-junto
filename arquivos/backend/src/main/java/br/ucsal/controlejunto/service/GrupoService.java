package br.ucsal.controlejunto.service;

import br.ucsal.controlejunto.domain.*;
import br.ucsal.controlejunto.dto.GrupoDTOs.*;
import br.ucsal.controlejunto.exception.AcessoNegadoException;
import br.ucsal.controlejunto.exception.NaoEncontradoException;
import br.ucsal.controlejunto.exception.RegraNegocioException;
import br.ucsal.controlejunto.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;

/**
 * Grupos de despesas compartilhadas:
 * criação (RF-005), participantes (RF-006), despesas (RF-007),
 * saldos (RF-008), quitação (RF-009) e encerramento (RN-003).
 */
@Service
public class GrupoService {

    private static final NumberFormat REAIS = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

    private final GrupoRepository grupos;
    private final ParticipacaoRepository participacoes;
    private final DespesaGrupoRepository despesas;
    private final DividaRepository dividas;
    private final UsuarioRepository usuarios;
    private final CategoriaRepository categorias;
    private final NotificacaoService notificacoes;

    public GrupoService(GrupoRepository grupos, ParticipacaoRepository participacoes,
                        DespesaGrupoRepository despesas, DividaRepository dividas,
                        UsuarioRepository usuarios, CategoriaRepository categorias,
                        NotificacaoService notificacoes) {
        this.grupos = grupos;
        this.participacoes = participacoes;
        this.despesas = despesas;
        this.dividas = dividas;
        this.usuarios = usuarios;
        this.categorias = categorias;
        this.notificacoes = notificacoes;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<GrupoResumoResponse> listarMeusGrupos(Long usuarioId) {
        return participacoes.findByUsuarioIdOrderByGrupoDataCriacaoDesc(usuarioId).stream()
                .map(p -> {
                    Grupo g = p.getGrupo();
                    BigDecimal meuSaldo = calcularSaldos(g.getId()).getOrDefault(usuarioId,
                            new CalculadoraSaldos.Saldo(BigDecimal.ZERO, BigDecimal.ZERO)).saldo();
                    return new GrupoResumoResponse(g.getId(), g.getNome(), g.getStatus().name(),
                            g.getDataCriacao(), (int) participacoes.countByGrupoId(g.getId()),
                            p.isAdministrador(), meuSaldo);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public GrupoDetalheResponse detalhar(Long usuarioId, Long grupoId) {
        Participacao minha = participacaoObrigatoria(grupoId, usuarioId);
        Grupo grupo = minha.getGrupo();
        List<Participacao> membros = participacoes.findByGrupoIdOrderByIdAsc(grupoId);

        List<ParticipanteResponse> participantes = membros.stream()
                .map(p -> new ParticipanteResponse(p.getUsuario().getId(), p.getUsuario().getNome(),
                        p.getUsuario().getEmail(), p.getPapel().name()))
                .toList();

        List<DespesaGrupo> listaDespesas = despesas.findByGrupoIdOrderByDataDescIdDesc(grupoId);
        List<DespesaResponse> despesasResp = listaDespesas.stream().map(GrupoService::paraResponse).toList();
        BigDecimal totalGasto = listaDespesas.stream().map(DespesaGrupo::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, CalculadoraSaldos.Saldo> saldos = calcularSaldos(grupoId);
        List<SaldoResponse> saldosResp = membros.stream()
                .map(p -> {
                    var s = saldos.getOrDefault(p.getUsuario().getId(),
                            new CalculadoraSaldos.Saldo(BigDecimal.ZERO, BigDecimal.ZERO));
                    return new SaldoResponse(p.getUsuario().getId(), p.getUsuario().getNome(),
                            s.aReceber(), s.aPagar(), s.saldo());
                })
                .toList();

        List<DividaResponse> dividasResp = dividas.findByGrupoIdOrderByIdAsc(grupoId).stream()
                .map(GrupoService::paraResponse).toList();

        return new GrupoDetalheResponse(grupo.getId(), grupo.getNome(), grupo.getStatus().name(),
                grupo.getDataCriacao(), minha.isAdministrador(), usuarioId, participantes,
                despesasResp, saldosResp, dividasResp, totalGasto);
    }

    // ------------------------------------------------------------------
    // Grupo e participantes
    // ------------------------------------------------------------------

    /** RF-005: quem cria o grupo vira o administrador. */
    @Transactional
    public GrupoDetalheResponse criar(Long usuarioId, GrupoRequest req) {
        Grupo grupo = grupos.save(new Grupo(req.nome().trim()));
        participacoes.save(new Participacao(grupo, usuarios.getReferenceById(usuarioId), PapelGrupo.ADMINISTRADOR));
        return detalhar(usuarioId, grupo.getId());
    }

    /** RF-006 e RN-006 (máximo de 20 participantes). */
    @Transactional
    public GrupoDetalheResponse adicionarParticipante(Long usuarioId, Long grupoId, ParticipanteRequest req) {
        Grupo grupo = administrador(grupoId, usuarioId).getGrupo();
        exigirAtivo(grupo);
        Usuario novo = usuarios.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new NaoEncontradoException(
                        "Nenhum usuário com este e-mail. A pessoa precisa criar uma conta primeiro."));
        if (participacoes.findByGrupoIdAndUsuarioId(grupoId, novo.getId()).isPresent()) {
            throw new RegraNegocioException("Esta pessoa já participa do grupo.");
        }
        if (participacoes.countByGrupoId(grupoId) >= Grupo.LIMITE_PARTICIPANTES) {
            throw new RegraNegocioException("O grupo já tem o máximo de " + Grupo.LIMITE_PARTICIPANTES + " participantes.");
        }
        participacoes.save(new Participacao(grupo, novo, PapelGrupo.MEMBRO));
        notificacoes.notificar(novo, "Você foi adicionado ao grupo \"" + grupo.getNome() + "\".");
        return detalhar(usuarioId, grupoId);
    }

    /** RF-006: remove um participante que ainda não tem dívidas pendentes no grupo. */
    @Transactional
    public GrupoDetalheResponse removerParticipante(Long usuarioId, Long grupoId, Long participanteId) {
        Grupo grupo = administrador(grupoId, usuarioId).getGrupo();
        exigirAtivo(grupo);
        if (participanteId.equals(usuarioId)) {
            throw new RegraNegocioException("O administrador não pode se remover do grupo.");
        }
        Participacao alvo = participacaoObrigatoria(grupoId, participanteId);
        var saldo = calcularSaldos(grupoId).get(participanteId);
        if (saldo != null && (saldo.aPagar().signum() != 0 || saldo.aReceber().signum() != 0)) {
            throw new RegraNegocioException("Não é possível remover: o participante ainda tem valores pendentes no grupo.");
        }
        participacoes.delete(alvo);
        return detalhar(usuarioId, grupoId);
    }

    /** RN-007: grupo sem despesas pode ser excluído pelo administrador. */
    @Transactional
    public void excluir(Long usuarioId, Long grupoId) {
        Grupo grupo = administrador(grupoId, usuarioId).getGrupo();
        if (despesas.countByGrupoId(grupoId) > 0) {
            throw new RegraNegocioException("Só é possível excluir um grupo que não tem despesas. Encerre o grupo quando tudo estiver quitado.");
        }
        participacoes.deleteByGrupoId(grupoId);
        grupos.delete(grupo);
    }

    /** RN-003: o administrador encerra o grupo quando não há dívidas pendentes. */
    @Transactional
    public GrupoDetalheResponse encerrar(Long usuarioId, Long grupoId) {
        Grupo grupo = administrador(grupoId, usuarioId).getGrupo();
        exigirAtivo(grupo);
        long pendentes = dividas.countByGrupoIdAndStatus(grupoId, StatusDivida.PENDENTE);
        if (pendentes > 0) {
            throw new RegraNegocioException("Ainda há " + pendentes + " dívida(s) pendente(s). O grupo só pode ser encerrado com tudo quitado.");
        }
        grupo.encerrar();
        return detalhar(usuarioId, grupoId);
    }

    // ------------------------------------------------------------------
    // Despesas e dívidas
    // ------------------------------------------------------------------

    /**
     * RF-007: registra a despesa, gera as divisões e as dívidas de quem não pagou,
     * e notifica os devedores (RF-012).
     */
    @Transactional
    public GrupoDetalheResponse registrarDespesa(Long usuarioId, Long grupoId, DespesaRequest req) {
        Grupo grupo = participacaoObrigatoria(grupoId, usuarioId).getGrupo();
        exigirAtivo(grupo);

        Map<Long, Usuario> membros = new HashMap<>();
        participacoes.findByGrupoIdOrderByIdAsc(grupoId)
                .forEach(p -> membros.put(p.getUsuario().getId(), p.getUsuario()));

        Usuario pagador = Optional.ofNullable(membros.get(req.pagadorId()))
                .orElseThrow(() -> new RegraNegocioException("Quem pagou precisa ser participante do grupo."));

        List<Long> selecionados = req.participantesIds().stream().distinct().toList();
        for (Long id : selecionados) {
            if (!membros.containsKey(id)) {
                throw new RegraNegocioException("Todos os participantes da divisão precisam fazer parte do grupo.");
            }
        }

        boolean personalizada = req.valores() != null && !req.valores().isEmpty();
        Map<Long, BigDecimal> partes;
        if (personalizada) {
            if (!new HashSet<>(selecionados).equals(req.valores().keySet())) {
                throw new RegraNegocioException("Informe um valor para cada participante selecionado.");
            }
            partes = CalculadoraDivisao.personalizada(req.valor(), req.valores()); // RN-008
        } else {
            partes = CalculadoraDivisao.igualitaria(req.valor(), selecionados);    // RN-001
        }

        Categoria categoria = req.categoriaId() == null ? null
                : categorias.findById(req.categoriaId()).orElse(null);

        DespesaGrupo despesa = new DespesaGrupo(grupo, pagador, membros.get(usuarioId), categoria,
                req.descricao().trim(), req.valor(), req.data());
        partes.forEach((id, valor) -> despesa.adicionarDivisao(membros.get(id), valor));
        despesas.save(despesa);

        partes.forEach((id, valor) -> {
            if (!id.equals(pagador.getId()) && valor.signum() > 0) {
                Usuario devedor = membros.get(id);
                dividas.save(new Divida(grupo, despesa, devedor, pagador, valor));
                notificacoes.notificar(devedor, "Você deve " + REAIS.format(valor) + " a " + pagador.getNome()
                        + " pela despesa \"" + despesa.getDescricao() + "\" no grupo \"" + grupo.getNome() + "\".");
            }
        });
        return detalhar(usuarioId, grupoId);
    }

    /** RF-009: o devedor (ou quem recebe) marca a dívida como paga. */
    @Transactional
    public GrupoDetalheResponse quitarDivida(Long usuarioId, Long dividaId) {
        Divida divida = dividas.findById(dividaId)
                .orElseThrow(() -> new NaoEncontradoException("Dívida não encontrada."));
        boolean envolvido = divida.getDevedor().getId().equals(usuarioId)
                || divida.getCredor().getId().equals(usuarioId);
        if (!envolvido) {
            throw new AcessoNegadoException("Só quem deve ou quem vai receber pode marcar esta dívida como paga.");
        }
        if (!divida.isPendente()) {
            throw new RegraNegocioException("Esta dívida já está quitada.");
        }
        divida.quitar();
        Usuario outro = divida.getDevedor().getId().equals(usuarioId) ? divida.getCredor() : divida.getDevedor();
        notificacoes.notificar(outro, "A dívida de " + REAIS.format(divida.getValor()) + " de "
                + divida.getDevedor().getNome() + " para " + divida.getCredor().getNome()
                + " foi marcada como paga no grupo \"" + divida.getGrupo().getNome() + "\".");
        return detalhar(usuarioId, divida.getGrupo().getId());
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private Map<Long, CalculadoraSaldos.Saldo> calcularSaldos(Long grupoId) {
        List<Long> ids = participacoes.findByGrupoIdOrderByIdAsc(grupoId).stream()
                .map(p -> p.getUsuario().getId()).toList();
        List<CalculadoraSaldos.DividaPendente> pendentes = dividas.findByGrupoIdOrderByIdAsc(grupoId).stream()
                .filter(Divida::isPendente)
                .map(d -> new CalculadoraSaldos.DividaPendente(d.getDevedor().getId(), d.getCredor().getId(), d.getValor()))
                .toList();
        return CalculadoraSaldos.calcular(ids, pendentes);
    }

    private Participacao participacaoObrigatoria(Long grupoId, Long usuarioId) {
        if (!grupos.existsById(grupoId)) {
            throw new NaoEncontradoException("Grupo não encontrado.");
        }
        return participacoes.findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .orElseThrow(() -> new AcessoNegadoException("Você não participa deste grupo."));
    }

    private Participacao administrador(Long grupoId, Long usuarioId) {
        Participacao p = participacaoObrigatoria(grupoId, usuarioId);
        if (!p.isAdministrador()) {
            throw new AcessoNegadoException("Apenas o administrador do grupo pode fazer isso.");
        }
        return p;
    }

    private static void exigirAtivo(Grupo grupo) {
        if (!grupo.isAtivo()) {
            throw new RegraNegocioException("Este grupo está encerrado e disponível apenas para consulta.");
        }
    }

    private static DespesaResponse paraResponse(DespesaGrupo d) {
        List<DivisaoResponse> divisoes = d.getDivisoes().stream()
                .map(v -> new DivisaoResponse(v.getUsuario().getId(), v.getUsuario().getNome(), v.getValorDevido()))
                .toList();
        return new DespesaResponse(d.getId(), d.getDescricao(), d.getValor(), d.getData(),
                d.getPagador().getId(), d.getPagador().getNome(),
                d.getCategoria() == null ? null : d.getCategoria().getNome(), divisoes);
    }

    private static DividaResponse paraResponse(Divida d) {
        return new DividaResponse(d.getId(), d.getDevedor().getId(), d.getDevedor().getNome(),
                d.getCredor().getId(), d.getCredor().getNome(), d.getValor(), d.getStatus().name(),
                d.getDespesa().getDescricao(), d.getDataQuitacao());
    }
}
