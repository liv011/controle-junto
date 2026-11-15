package br.ucsal.controlejunto.controller;

import br.ucsal.controlejunto.dto.OrcamentoDTOs.*;
import br.ucsal.controlejunto.security.UsuarioLogado;
import br.ucsal.controlejunto.service.OrcamentoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class OrcamentoController {

    private final OrcamentoService service;

    public OrcamentoController(OrcamentoService service) {
        this.service = service;
    }

    @GetMapping("/orcamento")
    public OrcamentoResponse consultar(@RequestParam(required = false) Integer mes,
                                       @RequestParam(required = false) Integer ano) {
        LocalDate hoje = LocalDate.now();
        return service.consultar(UsuarioLogado.id(),
                mes != null ? mes : hoje.getMonthValue(), ano != null ? ano : hoje.getYear());
    }

    @PutMapping("/orcamento")
    public OrcamentoResponse definir(@Valid @RequestBody OrcamentoRequest req) {
        return service.definir(UsuarioLogado.id(), req);
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@RequestParam(required = false) Integer mes,
                                       @RequestParam(required = false) Integer ano) {
        LocalDate hoje = LocalDate.now();
        return service.dashboard(UsuarioLogado.id(),
                mes != null ? mes : hoje.getMonthValue(), ano != null ? ano : hoje.getYear());
    }
}
