package cl.duoc.foromoderacion.dto;

import java.util.List;

public record VisibilidadResponse(List<Long> temaIds, List<Long> comentarioIds) { }
