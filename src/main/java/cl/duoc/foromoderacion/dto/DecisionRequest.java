package cl.duoc.foromoderacion.dto;

import jakarta.validation.constraints.*;

public record DecisionRequest(@NotNull Boolean baneado, @NotBlank @Size(max=500) String motivo) {
    public DecisionRequest { motivo=motivo==null?null:motivo.strip(); }
}
