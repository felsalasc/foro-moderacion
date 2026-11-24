package cl.duoc.foromoderacion.support;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ContenidoStub implements AutoCloseable {
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
    public volatile int status = 200;
    public volatile String body;
    public volatile String tipo = "application/json";
    public volatile long demora;
    public volatile String authorization;
    public volatile String cookie;
    public final AtomicInteger llamadas = new AtomicInteger();
    public ContenidoStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            llamadas.incrementAndGet();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            cookie = exchange.getRequestHeaders().getFirst("Cookie");
            String path = exchange.getRequestURI().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1);
            String respuesta = body != null ? body : "{\"id\":" + id + ",\"temaId\":"
                    + (path.contains("comentarios") ? "10" : "null") + "}";
            try {
                if (demora > 0) Thread.sleep(demora);
                exchange.getResponseHeaders().set("Content-Type", tipo);
                if (status == 302) exchange.getResponseHeaders().set("Location", baseUrl() + "/redirect");
                byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            catch (IOException ignored) { /* timeout intencionado del cliente */ }
            finally { exchange.close(); }
        });
        server.start();
    }
    public String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    public void reset() { status=200; body=null; tipo="application/json"; demora=0; authorization=null; cookie=null; llamadas.set(0); }
    public void close() { server.stop(0); executor.shutdownNow(); }
}
