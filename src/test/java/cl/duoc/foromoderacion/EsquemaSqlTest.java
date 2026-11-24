package cl.duoc.foromoderacion;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;

class EsquemaSqlTest {
    @Test void scriptCreaTablaYRestriccionesSinDependerDeOtrosEsquemas() {
        var datasource=new DriverManagerDataSource("jdbc:h2:mem:script_moderacion;MODE=Oracle;DB_CLOSE_DELAY=-1","sa","");
        var script=new ResourceDatabasePopulator(new FileSystemResource("database/01_estructura.sql"));
        script.setSqlScriptEncoding("UTF-8"); script.execute(datasource);
        var jdbc=new JdbcTemplate(datasource);
        String insert="INSERT INTO DECISIONES_MODERACION(TIPO_RECURSO,RECURSO_ID,BANEADO,MOTIVO,ADMINISTRADOR_ID,FECHA_DECISION) VALUES(?,?,?,?,?,?)";
        jdbc.update(insert,"TEMA",42,1,"Spam",9,OffsetDateTime.now());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM DECISIONES_MODERACION",Long.class)).isEqualTo(1);
        assertThatThrownBy(()->jdbc.update(insert,"TEMA",42,0,"Duplicado",9,OffsetDateTime.now())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update(insert,"OTRO",43,1,"Tipo",9,OffsetDateTime.now())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update(insert,"TEMA",-1,1,"ID",9,OffsetDateTime.now())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update(insert,"TEMA",43,2,"Estado",9,OffsetDateTime.now())).isInstanceOf(DataIntegrityViolationException.class);
    }
}
