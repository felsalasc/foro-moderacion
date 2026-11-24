package cl.duoc.foromoderacion;

import cl.duoc.foromoderacion.support.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ModeracionApiTest {
    static final UsuariosStub usuarios;
    static final ContenidoStub contenido;
    static { try { usuarios=new UsuariosStub(); contenido=new ContenidoStub(); } catch (Exception ex) { throw new ExceptionInInitializerError(ex); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("usuarios.base-url", usuarios::baseUrl);
        p.add("contenido.base-url", contenido::baseUrl);
        p.add("contenido.read-timeout", () -> "200ms");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @BeforeEach void reset() {
        usuarios.reset(); contenido.reset(); admin();
        if (tabla()) jdbc.update("DELETE FROM DECISIONES_MODERACION");
    }
    @AfterAll static void close() { usuarios.close(); contenido.close(); }
    void admin() { usuarios.respuesta(200,"{\"id\":9,\"nombre\":\"Admin\",\"correo\":\"admin@example.test\",\"rol\":\"ADMINISTRADOR\"}"); }
    boolean tabla() { return jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='DECISIONES_MODERACION'", Integer.class)>0; }
    long cantidad() { return tabla() ? jdbc.queryForObject("SELECT COUNT(*) FROM DECISIONES_MODERACION", Long.class) : 0; }
    MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) { return b.header("Authorization","Bearer admin-token"); }
    MockHttpServletRequestBuilder decision(String tipo, long id, boolean ban, String motivo) throws Exception {
        return auth(put("/api/moderacion/"+tipo+"/"+id)).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("baneado",ban,"motivo",motivo)));
    }
    @Test void administradorBaneaYRestauraConPersistenciaEIdempotencia() throws Exception {
        var primero=mvc.perform(decision("temas",10,true,"  Spam  ")).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("TEMA")).andExpect(jsonPath("$.recursoId").value(10))
                .andExpect(jsonPath("$.baneado").value(true)).andExpect(jsonPath("$.motivo").value("Spam"))
                .andExpect(jsonPath("$.administradorId").value(9)).andExpect(jsonPath("$.fechaDecision").isString())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(decision("temas",10,true,"Spam")).andExpect(content().json(primero));
        assertThat(cantidad()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT BANEADO FROM DECISIONES_MODERACION",Boolean.class)).isTrue();
        mvc.perform(decision("temas",10,false,"Revisión aceptada")).andExpect(status().isOk()).andExpect(jsonPath("$.baneado").value(false));
        assertThat(cantidad()).isEqualTo(1);
        assertThat(contenido.authorization).isEqualTo("Bearer admin-token");
        assertThat(contenido.cookie).isNull();
    }
    @Test void decisionesDistinguenTemaYComentarioYFiltran() throws Exception {
        mvc.perform(decision("temas",10,true,"Spam")).andExpect(status().isOk());
        mvc.perform(decision("comentarios",10,true,"Insulto")).andExpect(status().isOk());
        mvc.perform(decision("comentarios",11,false,"Restaurado")).andExpect(status().isOk());
        mvc.perform(auth(get("/api/moderacion/decisiones"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(auth(get("/api/moderacion/decisiones").param("tipo","COMENTARIO").param("baneado","true")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].recursoId").value(10));
    }
    @Test void usuarioConsultaSoloIdsBaneadosSinDatosAdministrativos() throws Exception {
        mvc.perform(decision("temas",10,true,"Privado")).andExpect(status().isOk());
        mvc.perform(decision("comentarios",20,true,"Privado")).andExpect(status().isOk());
        usuarios.respuesta(200,UsuariosStub.USUARIO);
        mvc.perform(auth(post("/api/moderacion/visibilidad")).contentType("application/json")
                .content("{\"temaIds\":[10,11],\"comentarioIds\":[20,21]}"))
                .andExpect(status().isOk()).andExpect(content().json("{\"temaIds\":[10],\"comentarioIds\":[20]}"));
        mvc.perform(auth(get("/api/moderacion/decisiones"))).andExpect(status().isForbidden());
        mvc.perform(decision("temas",10,false,"Intento usuario")).andExpect(status().isForbidden());
        assertThat(cantidad()).isEqualTo(2);
    }
    @Test void sinTokenNoHayLecturasNiEscrituras() throws Exception {
        mvc.perform(put("/api/moderacion/temas/1").contentType("application/json").content("{\"baneado\":true,\"motivo\":\"Spam\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/moderacion/decisiones")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/moderacion/visibilidad").contentType("application/json").content("{\"temaIds\":[],\"comentarioIds\":[]}"))
                .andExpect(status().isUnauthorized());
        assertThat(cantidad()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"baneado\":true}","{\"baneado\":null,\"motivo\":\"Spam\"}",
            "{\"baneado\":true,\"motivo\":\"  \"}","{\"baneado\":\"true\",\"motivo\":\"Spam\"}",
            "{\"baneado\":true,\"motivo\":123}","{\"baneado\":true,\"motivo\":\"Spam\",\"administradorId\":1}","[]","{"})
    void jsonInvalidoNoEscribe(String body) throws Exception {
        mvc.perform(auth(put("/api/moderacion/temas/1")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        assertThat(cantidad()).isZero();
    }
    @Test void motivoTieneLimite500() throws Exception {
        mvc.perform(decision("temas",1,true,"x".repeat(500))).andExpect(status().isOk());
        mvc.perform(decision("temas",2,true,"x".repeat(501))).andExpect(status().isBadRequest());
        assertThat(cantidad()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","abc","999999999999999999999999"})
    void idInvalidoNoConsultaContenido(String id) throws Exception {
        mvc.perform(auth(put("/api/moderacion/temas/"+id)).contentType("application/json").content("{\"baneado\":true,\"motivo\":\"Spam\"}"))
                .andExpect(status().isBadRequest());
        assertThat(contenido.llamadas.get()).isZero();
    }
    @Test void recursoInexistenteNoCreaDecision() throws Exception {
        contenido.status=404;
        mvc.perform(decision("temas",1,true,"Spam")).andExpect(status().isNotFound());
        assertThat(cantidad()).isZero();
    }
    @Test void respuestaContenidoInvalidaONoDisponibleNoEscribe() throws Exception {
        for (int status:List.of(500,401,403,302)) {
            contenido.status=status;
            mvc.perform(decision("temas",1,true,"Spam")).andExpect(status().isServiceUnavailable());
        }
        contenido.status=200;
        for (String body:List.of("{}","{\"id\":2,\"temaId\":null}","{\"id\":\"1\",\"temaId\":null}","[]","{\"id\":1,\"temaId\":null,\"extra\":1}")) {
            contenido.body=body;
            mvc.perform(decision("temas",1,true,"Spam")).andExpect(status().isServiceUnavailable());
        }
        contenido.body=null; contenido.tipo="text/html";
        mvc.perform(decision("temas",1,true,"Spam")).andExpect(status().isServiceUnavailable());
        contenido.tipo="application/json"; contenido.demora=1000;
        mvc.perform(decision("temas",1,true,"Spam")).andExpect(status().isServiceUnavailable());
        assertThat(cantidad()).isZero();
    }
    @Test void identidadNoDisponibleNoConsultaContenido() throws Exception {
        usuarios.respuesta(503,"dato privado");
        mvc.perform(decision("temas",1,true,"Spam")).andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("dato privado"))));
        assertThat(contenido.llamadas.get()).isZero();
    }
    @Test void visibilidadValidaListasYLimites() throws Exception {
        for (String body:List.of("{}","{\"temaIds\":null,\"comentarioIds\":[]}","{\"temaIds\":[0],\"comentarioIds\":[]}",
                "{\"temaIds\":[null],\"comentarioIds\":[]}","{\"temaIds\":[\"1\"],\"comentarioIds\":[]}",
                "{\"temaIds\":[],\"comentarioIds\":[],\"extra\":1}")) {
            mvc.perform(auth(post("/api/moderacion/visibilidad")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        var ids=java.util.stream.LongStream.rangeClosed(1,201).boxed().toList();
        mvc.perform(auth(post("/api/moderacion/visibilidad")).contentType("application/json").content(json.writeValueAsString(java.util.Map.of("temaIds",ids,"comentarioIds",List.of())))).andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/moderacion/visibilidad")).contentType("application/json").content("{\"temaIds\":[],\"comentarioIds\":[]}"))
                .andExpect(status().isOk()).andExpect(content().json("{\"temaIds\":[],\"comentarioIds\":[]}"));
    }
    @Test void decisionesConcurrentesCreanUnaSolaFila() throws Exception {
        var executor=Executors.newFixedThreadPool(4); var inicio=new CountDownLatch(1);
        try {
            var futures=new java.util.ArrayList<Future<Integer>>();
            for (int i=0;i<4;i++) futures.add(executor.submit(()->{ inicio.await(); return mvc.perform(decision("temas",50,true,"Spam")).andReturn().getResponse().getStatus(); }));
            inicio.countDown();
            for (var future:futures) assertThat(future.get(15,TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(cantidad()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
    @Test void referenciaSinCampoTemaIdNoCreaDecision() throws Exception {
        contenido.body="{\"id\":1}";
        mvc.perform(decision("temas",1,true,"Spam")).andExpect(status().isServiceUnavailable());
        assertThat(cantidad()).isZero();
    }
    @Test void estadoUsaNumeroConPrecisionCompatibleConScriptOracle() {
        var columna=jdbc.queryForMap("SELECT DATA_TYPE,NUMERIC_PRECISION,NUMERIC_SCALE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='DECISIONES_MODERACION' AND COLUMN_NAME='BANEADO'");
        assertThat(columna.get("DATA_TYPE")).isEqualTo("NUMERIC");
        assertThat(((Number)columna.get("NUMERIC_PRECISION")).intValue()).isEqualTo(1);
        assertThat(((Number)columna.get("NUMERIC_SCALE")).intValue()).isZero();
    }
}
