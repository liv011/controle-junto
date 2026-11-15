package br.ucsal.controlejunto.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServicoCategorizacaoIATest {

    private static final List<String> CATEGORIAS = List.of("Alimentação", "Transporte", "Moradia", "Saúde",
            "Educação", "Lazer", "Compras", "Contas e Serviços", "Viagem", "Outros");

    @Test
    void semChaveUsaAsPalavrasChave() {
        ServicoCategorizacaoIA ia = new ServicoCategorizacaoIA("", "modelo", 3);

        assertEquals("Transporte", ia.sugerir("Uber aeroporto", CATEGORIAS).categoria());
        assertEquals("Alimentação", ia.sugerir("Mercado Atacadão", CATEGORIAS).categoria());
        assertEquals("Contas e Serviços", ia.sugerir("Conta de luz outubro", CATEGORIAS).categoria());
        assertEquals("Lazer", ia.sugerir("Ingresso cinema", CATEGORIAS).categoria());
        assertEquals("Saúde", ia.sugerir("Farmácia remédio gripe", CATEGORIAS).categoria());
        assertEquals("Viagem", ia.sugerir("Pousada Lençóis 2 diárias", CATEGORIAS).categoria());
        assertEquals("REGRAS", ia.sugerir("Uber aeroporto", CATEGORIAS).origem());
    }

    @Test
    void descricaoDesconhecidaViraOutros() {
        assertEquals("Outros", ServicoCategorizacaoIA.classificarPorPalavrasChave("xyz qualquer coisa"));
    }
}
