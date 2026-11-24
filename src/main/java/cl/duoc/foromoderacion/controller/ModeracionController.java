package cl.duoc.foromoderacion.controller;

import cl.duoc.foromoderacion.dto.*;
import cl.duoc.foromoderacion.model.TipoRecurso;
import cl.duoc.foromoderacion.security.Identidad;
import cl.duoc.foromoderacion.service.DecisionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/moderacion")
public class ModeracionController {
    private final DecisionService service;
    public ModeracionController(DecisionService service) { this.service=service; }
    @PutMapping("/temas/{id}")
    public DecisionResponse tema(@PathVariable Long id,@Valid @RequestBody DecisionRequest request,@AuthenticationPrincipal Identidad actor) {
        return service.decidir(TipoRecurso.TEMA,id,request,actor);
    }
    @PutMapping("/comentarios/{id}")
    public DecisionResponse comentario(@PathVariable Long id,@Valid @RequestBody DecisionRequest request,@AuthenticationPrincipal Identidad actor) {
        return service.decidir(TipoRecurso.COMENTARIO,id,request,actor);
    }
    @GetMapping("/decisiones")
    public List<DecisionResponse> listar(@RequestParam(required=false) TipoRecurso tipo,@RequestParam(required=false) Boolean baneado) {
        return service.listar(tipo,baneado);
    }
    @PostMapping("/visibilidad")
    public VisibilidadResponse visibilidad(@Valid @RequestBody VisibilidadRequest request) { return service.visibilidad(request); }
}
