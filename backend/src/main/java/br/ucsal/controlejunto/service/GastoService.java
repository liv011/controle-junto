package br.ucsal.controlejunto.service;

import br.ucsal.controlejunto.domain.Categoria;
import br.ucsal.controlejunto.domain.Gasto;
import br.ucsal.controlejunto.domain.Usuario;
import br.ucsal.controlejunto.dto.GastoDTOs.*;
import br.ucsal.controlejunto.exception.AcessoNegadoException;
import br.ucsal.controlejunto.exception.NaoEncontradoException;
import br.ucsal.controlejunto.repository.CategoriaRepository;
import br.ucsal.controlejunto.repository.GastoRepository;
import br.ucsal.controlejunto.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

/** Gastos pessoais (RF-003, RF-004, RF-014, RF-016) e sugestão de categoria (RF-010). */
@Service
public class GastoService {

    private final GastoRepository gastos;
    private final CategoriaRepository categorias;
    private final UsuarioRepository usuarios;
    private final ServicoCategorizacaoIA ia;

    public GastoService(GastoRepository gastos, CategoriaRepository categorias,
                        UsuarioRepository usuarios, ServicoCategorizacaoIA ia) {
        this.gastos = gastos;
        this.categorias = categorias;
        this.usuarios = usuarios;
        this.ia = ia;
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listarCategorias() {
        return categorias.findAllByOrderByNomeAsc().stream().map(GastoService::paraResponse).toList();
    }

    /** RF-010 e RN-005: apenas sugere; o usuário confirma ou troca antes de salvar. */
    @Transactional(readOnly = true)
    public SugestaoResponse sugerirCategoria(String descricao) {
        List<Categoria> todas = categorias.findAllByOrderByNomeAsc();
        List<String> nomes = todas.stream().map(Categoria::getNome).toList();
        ServicoCategorizacaoIA.Sugestao sugestao = ia.sugerir(descricao, nomes);
        Categoria categoria = categorias.findByNomeIgnoreCase(sugestao.categoria())
                .or(() -> categorias.findByNomeIgnoreCase(ServicoCategorizacaoIA.OUTROS))
                .orElseThrow(() -> new NaoEncontradoException("Categoria não encontrada."));
        return new SugestaoResponse(paraResponse(categoria), sugestao.origem());
    }

    @Transactional(readOnly = true)
    public List<GastoResponse> listar(Long usuarioId, int mes, int ano, Long categoriaId) {
        YearMonth ym = YearMonth.of(ano, mes);
        List<Gasto> lista = categoriaId == null
                ? gastos.buscar(usuarioId, ym.atDay(1), ym.atEndOfMonth())
                : gastos.buscarPorCategoria(usuarioId, ym.atDay(1), ym.atEndOfMonth(), categoriaId);
        return lista.stream().map(GastoService::paraResponse).toList();
    }

    @Transactional
    public GastoResponse criar(Long usuarioId, GastoRequest req) {
        CalculadoraDivisao.validarTotal(req.valor()); // RN-009
        Usuario usuario = usuarios.getReferenceById(usuarioId);
        Gasto gasto = new Gasto(usuario, categoria(req.categoriaId()), req.valor(), req.data(),
                req.descricao().trim(), req.comentario());
        return paraResponse(gastos.save(gasto));
    }

    @Transactional
    public GastoResponse atualizar(Long usuarioId, Long gastoId, GastoRequest req) {
        CalculadoraDivisao.validarTotal(req.valor());
        Gasto gasto = doUsuario(usuarioId, gastoId);
        gasto.atualizar(categoria(req.categoriaId()), req.valor(), req.data(), req.descricao().trim(), req.comentario());
        return paraResponse(gasto);
    }

    @Transactional
    public void excluir(Long usuarioId, Long gastoId) {
        gastos.delete(doUsuario(usuarioId, gastoId));
    }

    /** RN-002: só o autor pode editar ou excluir o gasto. */
    private Gasto doUsuario(Long usuarioId, Long gastoId) {
        Gasto gasto = gastos.findById(gastoId)
                .orElseThrow(() -> new NaoEncontradoException("Gasto não encontrado."));
        if (!gasto.getUsuario().getId().equals(usuarioId)) {
            throw new AcessoNegadoException("Apenas quem registrou o gasto pode alterá-lo.");
        }
        return gasto;
    }

    private Categoria categoria(Long id) {
        return categorias.findById(id).orElseThrow(() -> new NaoEncontradoException("Categoria não encontrada."));
    }

    static CategoriaResponse paraResponse(Categoria c) {
        return new CategoriaResponse(c.getId(), c.getNome());
    }

    static GastoResponse paraResponse(Gasto g) {
        return new GastoResponse(g.getId(), g.getValor(), g.getData(), g.getDescricao(),
                paraResponse(g.getCategoria()), g.getComentario());
    }
}
