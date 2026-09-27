package librosbuysan.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import librosbuysan.auth.JwtService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtService jwtService,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          @Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtService = jwtService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // RF9/RS1/RS5/RS9/RS19: la busqueda es la una
                        // operacion de books restringida por rol, y su regla
                        // tiene que evaluarse ANTES que el permitAll general
                        // de /api/books/** (Spring Security aplica la
                        // primera regla que matchea la ruta+metodo).
                        .requestMatchers(HttpMethod.GET, "/api/books/search").hasRole("COMPRADOR")
                        // Catalogo (R7) y detalle de libro siguen publicos;
                        // el filtro de RS34 (libreria deshabilitada) se
                        // aplica en la query, no en la autorizacion.
                        .requestMatchers(HttpMethod.GET, "/api/books/**").permitAll()
                        // RS9/RS19: deny-by-default. Cualquier otra ruta o
                        // metodo (incluyendo POST/PUT/DELETE sobre /api/books,
                        // que ademas ya reciben 405 de Spring MVC por no
                        // tener handler mapeado) exige estar autenticado como
                        // minimo; no hay reglas implicitas de permiso.
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}