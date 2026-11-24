package cl.duoc.foromoderacion.service;

import cl.duoc.foromoderacion.dto.*;
import cl.duoc.foromoderacion.model.TipoRecurso;
import cl.duoc.foromoderacion.repository.DecisionRepository;
import cl.duoc.foromoderacion.security.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class DecisionService {
    private final DecisionWriter writer;
    private final DecisionRepository repository;
    private final ContenidoClient contenido;
    private final TokenPeticion token;
    public DecisionService(DecisionWriter writer,DecisionRepository repository,ContenidoClient contenido,TokenPeticion token) {
        this.writer=writer; this.repository=repository; this.contenido=contenido; this.token=token;
    }
    public DecisionResponse decidir(TipoRecurso tipo,Long id,DecisionRequest request,Identidad actor) {
        if(!"ADMINISTRADOR".equals(actor.rol())) throw new AccessDeniedException("Permisos insuficientes");
        if(id==null || id<=0) throw new IllegalArgumentException("ID inválido");
        contenido.exigirExiste(tipo,id,token.obtener());
        try { return writer.escribir(tipo,id,request,actor.id()); }
        catch(DataIntegrityViolationException carrera) {
            // El insert concurrente ganador ya terminó. Releer y bloquear en OTRA transacción.
            return writer.escribir(tipo,id,request,actor.id());
        }
    }
    @Transactional(readOnly=true)
    public List<DecisionResponse> listar(TipoRecurso tipo,Boolean baneado) {
        return repository.listar(tipo,baneado).stream().map(DecisionResponse::de).toList();
    }
    @Transactional(readOnly=true)
    public VisibilidadResponse visibilidad(VisibilidadRequest request) {
        return new VisibilidadResponse(request.temaIds().isEmpty()?List.of():repository.baneados(TipoRecurso.TEMA,request.temaIds()),
                request.comentarioIds().isEmpty()?List.of():repository.baneados(TipoRecurso.COMENTARIO,request.comentarioIds()));
    }
}
