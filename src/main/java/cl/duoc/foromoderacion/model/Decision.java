package cl.duoc.foromoderacion.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name="DECISIONES_MODERACION", uniqueConstraints=@UniqueConstraint(name="UK_DECISION_RECURSO",columnNames={"TIPO_RECURSO","RECURSO_ID"}))
public class Decision {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="ID") private Long id;
    @Enumerated(EnumType.STRING) @Column(name="TIPO_RECURSO",nullable=false,length=10) private TipoRecurso tipo;
    @Column(name="RECURSO_ID",nullable=false) private Long recursoId;
    @Convert(converter=org.hibernate.type.NumericBooleanConverter.class)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NUMERIC)
    @Column(name="BANEADO",nullable=false,precision=1,scale=0) private boolean baneado;
    @Column(name="MOTIVO",nullable=false,length=500) private String motivo;
    @Column(name="ADMINISTRADOR_ID",nullable=false) private Long administradorId;
    @Column(name="FECHA_DECISION",nullable=false) private OffsetDateTime fechaDecision;
    protected Decision() { }
    public Decision(TipoRecurso tipo, Long recursoId, boolean baneado, String motivo, Long administradorId) {
        this.tipo=tipo; this.recursoId=recursoId; actualizar(baneado,motivo,administradorId);
    }
    public void actualizar(boolean baneado, String motivo, Long administradorId) {
        if (fechaDecision != null && this.baneado==baneado && this.motivo.equals(motivo)) return;
        this.baneado=baneado; this.motivo=motivo; this.administradorId=administradorId;
        this.fechaDecision=OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
    public Long getId(){ return id; }
    public TipoRecurso getTipo(){ return tipo; }
    public Long getRecursoId(){ return recursoId; }
    public boolean isBaneado(){ return baneado; }
    public String getMotivo(){ return motivo; }
    public Long getAdministradorId(){ return administradorId; }
    public OffsetDateTime getFechaDecision(){ return fechaDecision; }
}
