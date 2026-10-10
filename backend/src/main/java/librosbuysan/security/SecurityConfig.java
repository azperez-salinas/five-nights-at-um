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
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtService jwtService,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          @Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtService = jwtService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
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
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // Spring reenvia internamente a /error para armar
                        // cualquier respuesta de error del servlet container
                        // (404, 405, 400 por JSON mal formado, etc.). Ese
                        // reenvio interno vuelve a pasar por esta cadena de
                        // filtros, pero JwtAuthenticationFilter (OncePerRequestFilter)
                        // no corre en dispatches de tipo ERROR por diseño de
                        // Spring, asi que si /error no esta permitAll,
                        // anyRequest().authenticated() lo rechaza con 401 y
                        // tapa el codigo de error real (ej. un 405 real
                        // terminaba devolviendo 401 "No autenticado").
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        // Catalogo (R7), busqueda (RF9) y detalle de libro son
                        // publicos: un visitante puede explorar sin cuenta y
                        // solo necesita registrarse para usar favoritos. El
                        // filtro de RS34 (libreria deshabilitada) se aplica en
                        // la query, no en la autorizacion. Solo GET: cualquier
                        // otro verbo cae en el deny-by-default de abajo.
                        .requestMatchers(HttpMethod.GET, "/api/books/**").permitAll()
                        // R10: el listado de librerias habilitadas alimenta el
                        // filtro del catalogo, que es publico como el catalogo.
                        .requestMatchers(HttpMethod.GET, "/api/librerias").permitAll()
                        // RF12/RS1/RS5/RS9/RS19: favoritos es una
                        // funcionalidad exclusiva del COMPRADOR en sus tres
                        // operaciones (listar, agregar, quitar) — no hay
                        // metodo publico ni accesible para DUENO, por eso se
                        // restringe el prefijo completo sin distinguir por
                        // verbo HTTP.
                        .requestMatchers("/api/favorites/**").hasRole("COMPRADOR")
                        // RS5: el perfil propio es la unica funcionalidad
                        // compartida por ambos roles; se declara con los
                        // roles explicitos en lugar de "cualquier autenticado".
                        .requestMatchers(HttpMethod.GET, "/api/users/me").hasAnyRole("COMPRADOR", "DUENO")
                        .requestMatchers(HttpMethod.PUT, "/api/users/me").hasAnyRole("COMPRADOR", "DUENO")
                        // R14/F14: ver y dar de baja la libreria propia es
                        // exclusivo del DUENO. Que la libreria sea suya lo
                        // valida LibraryService, no esta regla.
                        .requestMatchers(HttpMethod.GET, "/api/users/libreria").hasRole("DUENO")
                        .requestMatchers(HttpMethod.DELETE, "/api/users/libreria/*").hasRole("DUENO")
                        // RS5/RS19/RS27: deny-by-default real. Cualquier ruta
                        // o metodo sin regla explicita arriba se rechaza,
                        // aunque el usuario este autenticado: un endpoint
                        // nuevo queda inaccesible hasta que se le asigne un
                        // rol aca. Sin token responde 401; con token, 403.
                        .anyRequest().denyAll())
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