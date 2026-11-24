package cl.duoc.foromoderacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix="contenido")
public record ContenidoProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {
    public ContenidoProperties { new UsuariosProperties(baseUrl, connectTimeout, readTimeout); }
}
