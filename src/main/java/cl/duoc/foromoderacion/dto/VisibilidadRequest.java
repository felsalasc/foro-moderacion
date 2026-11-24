package cl.duoc.foromoderacion.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record VisibilidadRequest(@NotNull @Size(max=200) List<@NotNull @Positive Long> temaIds,
                                 @NotNull @Size(max=200) List<@NotNull @Positive Long> comentarioIds) { }
