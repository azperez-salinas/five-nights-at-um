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
import librosbuysan.auth.TokenRevocationService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String PWD_CHANGE = JwtAuthenticationFilter.PASSWORD_CHANGE_ROLE;

    private final JwtService jwtService;
    private final TokenRevocationService revocationService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtService jwtService,
                          TokenRevocationService revocationService,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          @Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtService = jwtService;
        this.revocationService = revocationService;
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
                        // R3: el logout exige sesion activa. Esta regla DEBE ir
                        // antes de "/api/auth/**": Spring usa la primera que
                        // coincide, y si quedara despues el logout seria publico
                        // y el principal llegaria null al controller.
                        // R18: el admin y el dueno con contrasena temporal
                        // tambien pueden cerrar sesion (revocar su token).
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout")
                                .hasAnyRole("COMPRADOR", "DUENO", "ADMIN", PWD_CHANGE)
                        .requestMatchers("/api/auth/**").permitAll()
                        // R18/F18: las dos unicas acciones del panel de admin.
                        // Se declaran por metodo y ruta exactos: cualquier
                        // otra cosa bajo /api/admin cae en el denyAll de abajo,
                        // incluso para el admin. El rol ADMIN no aparece en
                        // ninguna otra regla, asi que el resto de las
                        // operaciones protegidas le responden 403.
                        .requestMatchers(HttpMethod.POST, "/api/admin/owners").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/password").hasRole("ADMIN")
                        // Catalogo (R7), busqueda (RF9) y detalle de libro son
                        // publicos: un visitante puede explorar sin cuenta y
                        // solo necesita registrarse para usar favoritos. El
                        // filtro de RS34 (libreria deshabilitada) se aplica en
                        // la query, no en la autorizacion. Solo GET: cualquier
                        // otro verbo cae en el deny-by-default de abajo.
                        .requestMatchers(HttpMethod.POST, "/api/books/import").hasRole("DUENO")
                        // R17: importación masiva exclusiva de dueños.
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
                        // R18: con la contrasena temporal (PWD_CHANGE) el dueno
                        // solo puede ver su perfil y cambiarla.
                        .requestMatchers(HttpMethod.GET, "/api/users/me").hasAnyRole("COMPRADOR", "DUENO", PWD_CHANGE)
                        .requestMatchers(HttpMethod.PUT, "/api/users/me").hasAnyRole("COMPRADOR", "DUENO", PWD_CHANGE)
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
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, revocationService), UsernamePasswordAuthenticationFilter.class);
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