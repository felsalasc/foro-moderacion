package cl.duoc.foromoderacion.service;

import cl.duoc.foromoderacion.config.ContenidoProperties;
import cl.duoc.foromoderacion.exception.*;
import cl.duoc.foromoderacion.model.TipoRecurso;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.net.HttpURLConnection;
import java.io.IOException;

@Component
@EnableConfigurationProperties(ContenidoProperties.class)
public class ContenidoClient {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final Validator validator;
    public ContenidoClient(RestClient.Builder builder, ContenidoProperties properties, ObjectMapper mapper, Validator validator) {
        this.mapper=mapper; this.validator=validator;
        var factory=new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection,String method) throws IOException {
                super.prepareConnection(connection,method); connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(properties.connectTimeout()); factory.setReadTimeout(properties.readTimeout());
        client=builder.baseUrl(properties.baseUrl().toString()).requestFactory(factory).build();
    }
    public void exigirExiste(TipoRecurso tipo, Long id, String token) {
        String recurso=tipo==TipoRecurso.TEMA?"temas":"comentarios";
        try {
            client.get().uri("/api/interno/moderacion/"+recurso+"/{id}",id)
                    .accept(MediaType.APPLICATION_JSON).header("Authorization","Bearer "+token)
                    .exchange((req,res)->{
                        if(res.getStatusCode().value()==404) throw new RecursoNoEncontradoException("Recurso no encontrado");
                        MediaType media=res.getHeaders().getContentType();
                        if(res.getStatusCode().value()!=200 || media==null || !MediaType.APPLICATION_JSON.isCompatibleWith(media))
                            throw new DependenciaNoDisponibleException();
                        byte[] bytes=res.getBody().readNBytes(16385);
                        if(bytes.length>16384) throw new DependenciaNoDisponibleException();
                        Referencia referencia=mapper.readValue(bytes,Referencia.class);
                        if(referencia==null || !validator.validate(referencia).isEmpty() || !id.equals(referencia.id())
                                || (tipo==TipoRecurso.COMENTARIO && referencia.temaId()==null)
                                || (tipo==TipoRecurso.TEMA && referencia.temaId()!=null)) throw new DependenciaNoDisponibleException();
                        return referencia;
                    });
        } catch(RestClientException | IllegalArgumentException ex) { throw new DependenciaNoDisponibleException(); }
    }
    public record Referencia(@com.fasterxml.jackson.annotation.JsonProperty(value="id",required=true) @NotNull @Positive Long id,
                             @com.fasterxml.jackson.annotation.JsonProperty(value="temaId",required=true) @Positive Long temaId) { }
}
