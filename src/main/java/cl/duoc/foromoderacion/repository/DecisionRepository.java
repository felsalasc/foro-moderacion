package cl.duoc.foromoderacion.repository;

import cl.duoc.foromoderacion.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface DecisionRepository extends JpaRepository<Decision,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Decision d where d.tipo=:tipo and d.recursoId=:id")
    Optional<Decision> bloquear(@Param("tipo") TipoRecurso tipo, @Param("id") Long id);
    @Query("select d from Decision d where (:tipo is null or d.tipo=:tipo) and (:baneado is null or d.baneado=:baneado) order by d.id")
    List<Decision> listar(@Param("tipo") TipoRecurso tipo,@Param("baneado") Boolean baneado);
    @Query("select d.recursoId from Decision d where d.tipo=:tipo and d.baneado=true and d.recursoId in :ids order by d.recursoId")
    List<Long> baneados(@Param("tipo") TipoRecurso tipo,@Param("ids") List<Long> ids);
}
