package cl.duoc.foromoderacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
public class ForoModeracionApplication {
    public static void main(String[] args) { SpringApplication.run(ForoModeracionApplication.class, args); }
}
