package cl.duoc.foromoderacion.service;

import cl.duoc.foromoderacion.dto.*;
import cl.duoc.foromoderacion.model.*;
import cl.duoc.foromoderacion.repository.DecisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionWriter {
    private final DecisionRepository repository;
    public DecisionWriter(DecisionRepository repository) { this.repository=repository; }
    @Transactional
    public DecisionResponse escribir(TipoRecurso tipo, Long id, DecisionRequest request, Long administradorId) {
        var decision=repository.bloquear(tipo,id).orElseGet(()->new Decision(tipo,id,request.baneado(),request.motivo(),administradorId));
        decision.actualizar(request.baneado(),request.motivo(),administradorId);
        return DecisionResponse.de(repository.saveAndFlush(decision));
    }
}
