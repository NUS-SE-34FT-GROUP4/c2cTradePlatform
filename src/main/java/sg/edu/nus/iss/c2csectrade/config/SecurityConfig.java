package sg.edu.nus.iss.c2csectrade.config;

import sg.edu.nus.iss.c2csectrade.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 使用 BCrypt 加密，匹配数据库中的密码格式
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
           .cors(cors -> cors.configurationSource(corsConfigurationSource()))
           .csrf(csrf -> csrf.disable())
           .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
           .authorizeHttpRequests(auth -> auth
               .requestMatchers("/api/auth/**").permitAll()
               .requestMatchers("/api/captcha/**").permitAll()
               .requestMatchers("/api/products").permitAll() // 允许所有用户查看商品列表
               .requestMatchers("/api/products/*").permitAll() // 允许所有用户查看商品详情
               .requestMatchers("/actuator/health", "/actuator/info").permitAll()
               // 管理后台只有 ROLE_ADMIN 能进；其余业务接口登录即可
               .requestMatchers("/api/admin/**").hasRole("ADMIN")
               .requestMatchers("/api/recommendations/**").permitAll() // 详情页同类商品对游客可见
               .requestMatchers("/ws/**").permitAll() // STOMP handshake is open; messages are authenticated at the STOMP layer
               .anyRequest().authenticated()
            )
           .exceptionHandling(ex -> ex
               .authenticationEntryPoint((request, response, authException) -> {
                   response.setContentType("application/json;charset=UTF-8");
                   response.setStatus(401);
                   response.getWriter().write("{\"error\":\"未授权访问\"}");
               })
               .accessDeniedHandler((request, response, accessDeniedException) -> {
                   response.setContentType("application/json;charset=UTF-8");
                   response.setStatus(403);
                   response.getWriter().write("{\"error\":\"访问被拒绝\"}");
               })
           );
        // use the injected component instead of creating a new instance
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply CORS to all endpoints including /ws/**
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
