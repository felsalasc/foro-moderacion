package cl.duoc.foromoderacion.dto;

import cl.duoc.foromoderacion.model.*;
import java.time.OffsetDateTime;

public record DecisionResponse(Long id, TipoRecurso tipo, Long recursoId, boolean baneado, String motivo,
                               Long administradorId, OffsetDateTime fechaDecision) {
    public static DecisionResponse de(Decision d) {
        return new DecisionResponse(d.getId(),d.getTipo(),d.getRecursoId(),d.isBaneado(),d.getMotivo(),d.getAdministradorId(),d.getFechaDecision());
    }
}
