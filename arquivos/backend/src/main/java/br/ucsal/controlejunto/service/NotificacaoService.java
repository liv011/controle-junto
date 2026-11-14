package br.ucsal.controlejunto.service;

import br.ucsal.controlejunto.domain.Notificacao;
import br.ucsal.controlejunto.domain.Usuario;
import br.ucsal.controlejunto.dto.GrupoDTOs.NotificacaoResponse;
import br.ucsal.controlejunto.exception.AcessoNegadoException;
import br.ucsal.controlejunto.exception.NaoEncontradoException;
import br.ucsal.controlejunto.repository.NotificacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Notificações dentro do aplicativo (RF-012). */
@Service
public class NotificacaoService {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final NotificacaoRepository notificacoes;

    public NotificacaoService(NotificacaoRepository notificacoes) {
        this.notificacoes = notificacoes;
    }

    @Transactional
    public void notificar(Usuario usuario, String mensagem) {
        notificacoes.save(new Notificacao(usuario, mensagem));
    }

    @Transactional(readOnly = true)
    public List<NotificacaoResponse> listar(Long usuarioId) {
        return notificacoes.findTop20ByUsuarioIdOrderByDataHoraDesc(usuarioId).stream()
                .map(n -> new NotificacaoResponse(n.getId(), n.getMensagem(), n.isLida(),
                        n.getDataHora().format(FORMATO)))
                .toList();
    }

    @Transactional
    public void marcarComoLida(Long usuarioId, Long notificacaoId) {
        Notificacao n = notificacoes.findById(notificacaoId)
                .orElseThrow(() -> new NaoEncontradoException("Notificação não encontrada."));
        if (!n.getUsuario().getId().equals(usuarioId)) {
            throw new AcessoNegadoException("Esta notificação não é sua.");
        }
        n.marcarComoLida();
    }
}
