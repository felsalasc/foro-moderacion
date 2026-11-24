import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/** Diagnostico de solo lectura. Usa las mismas credenciales por entorno del servicio. */
public class ProbarOracle {
    public static void main(String[] args) throws Exception {
        String url=System.getenv("ORACLE_URL");
        String usuario=System.getenv("ORACLE_USERNAME");
        String clave=System.getenv("ORACLE_PASSWORD");
        if(url==null || usuario==null || clave==null || clave.isEmpty()) {
            System.err.println("Faltan variables de conexion; usar Iniciar-Oracle.ps1 -ProbarConexion.");
            System.exit(2);
        }
        var tablas=Map.of("FORO_USUARIOS","USUARIOS","FORO_CONTENIDO","TEMAS","FORO_MODERACION","DECISIONES_MODERACION");
        String tabla=tablas.get(usuario);
        if(tabla==null) throw new IllegalArgumentException("Esquema no previsto para esta prueba");
        Properties properties=new Properties();
        properties.setProperty("user",usuario);
        properties.setProperty("password",clave);
        properties.setProperty("oracle.net.CONNECT_TIMEOUT","10000");
        properties.setProperty("oracle.jdbc.ReadTimeout","10000");
        DriverManager.setLoginTimeout(20);
        System.out.println("Java: "+System.getProperty("java.version")+" | Esquema: "+usuario);
        System.out.println("Prueba JDBC directa; sin Spring, sin pool y sin escrituras.");
        int errores=0;
        // Conexiones seriales independientes: distinguen logon de conexion ociosa.
        for(int intento=1;intento<=3;intento++) {
            try(Connection connection=DriverManager.getConnection(url,properties)) {
                consultar(connection,tabla,"Conexion nueva "+intento);
            } catch(SQLException ex) { errores++; informar("Conexion nueva "+intento,ex); }
        }
        // Mantener una conexion y comprobar acceso a la tabla durante dos minutos.
        try(Connection connection=DriverManager.getConnection(url,properties)) {
            consultar(connection,tabla,"Conexion sostenida, inicio");
            for(int ronda=1;ronda<=6;ronda++) {
                Thread.sleep(20_000);
                consultar(connection,tabla,"Conexion sostenida, segundo "+(ronda*20));
            }
        } catch(SQLException ex) { errores++; informar("Conexion sostenida",ex); }
        try(Connection connection=DriverManager.getConnection(url,properties)) {
            consultar(connection,tabla,"Conexion nueva final");
        } catch(SQLException ex) { errores++; informar("Conexion nueva final",ex); }
        System.out.println(errores==0?"PASS: conexiones nuevas y consultas sostenidas correctas.":"FAIL: "+errores+" fases con errores JDBC.");
        if(errores>0) System.exit(1);
    }
    private static void consultar(Connection connection,String tabla,String paso) throws SQLException {
        try(var sentencia=connection.createStatement()) {
            sentencia.setQueryTimeout(10);
            try(var resultado=sentencia.executeQuery("SELECT COUNT(*) FROM "+tabla)) {
                if(!resultado.next()) throw new SQLException("Consulta sin resultado");
                System.out.println(paso+": OK | filas="+resultado.getLong(1));
            }
        }
    }
    private static void informar(String paso,SQLException ex) {
        // Evitar trazas de controlador con datos de red, rutas y configuracion privada.
        System.out.println(paso+": ERROR | codigo="+ex.getErrorCode()+" | SQLState="+ex.getSQLState());
        for(Throwable causa=ex.getCause();causa!=null;causa=causa.getCause()) {
            if(causa instanceof SQLException sql) System.out.println("Causa SQL: codigo="+sql.getErrorCode()+" | SQLState="+sql.getSQLState());
        }
    }
}
