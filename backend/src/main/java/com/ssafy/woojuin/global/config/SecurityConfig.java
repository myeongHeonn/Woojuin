package com.ssafy.woojuin.global.config;

import com.ssafy.woojuin.domain.auth.jwt.JwtAuthenticationFilter;
import com.ssafy.woojuin.domain.auth.jwt.JwtTokenProvider;
import com.ssafy.woojuin.domain.auth.oauth.CustomOidcUserService;
import com.ssafy.woojuin.domain.auth.oauth.OAuth2LoginFailureHandler;
import com.ssafy.woojuin.domain.auth.oauth.OAuth2LoginSuccessHandler;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.auth.security.CustomUserDetailsService;
import com.ssafy.woojuin.global.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 인가 규칙은 아직 전체 허용 상태.
 * TODO(FR-001): 도메인 컨트롤러가 추가되면서 authorizeHttpRequests에 인증 필요 경로 반영
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final CustomOidcUserService customOidcUserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final UserRepository userRepository;

    // 기본값은 application.yml 한 곳에만 둔다(${CORS_ALLOWED_ORIGIN:...}).
    // 여기에도 기본값을 적으면 yml 쪽이 항상 이겨서 죽은 값이 되는데, 코드만 읽은 사람은
    // 그게 유효하다고 믿게 된다. 선언이 사라지면 기동 시점에 바로 실패하는 편이 낫다.
    @Value("${woojuin.cors.allowed-origins}")
    private String allowedOriginsValue;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider, CustomUserDetailsService userDetailsService,
                           CustomOidcUserService customOidcUserService,
                           OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
                           OAuth2LoginFailureHandler oAuth2LoginFailureHandler,
                           RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                           UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.customOidcUserService = customOidcUserService;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.oAuth2LoginFailureHandler = oAuth2LoginFailureHandler;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.userRepository = userRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder());
        provider.setUserDetailsService(userDetailsService);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * 프론트(5173)와 백엔드(8080)가 다른 오리진이라 명시적으로 열어줘야 한다.
     * 안 열면 axios가 baseURL을 절대경로로 쓰는 순간(=Vite 프록시를 안 타는 순간)
     * 모든 요청이 브라우저 단에서 CORS로 막힌다 — 응답 자체를 못 받아서 네트워크
     * 탭에 상태코드도 안 찍히고 콘솔에도 별다른 에러가 안 남는 게 특징.
     *
     * <p>허용 목록은 {@code woojuin.cors.allowed-origins} 하나만 본다. 여기에 localhost를
     * 무조건 더하지 않는 이유: 그러면 운영에서도 개발 주소가 항상 허용된다(사용자 PC의 5173에서
     * 도는 아무 프로세스가 운영 API를 인증된 상태로 호출할 수 있고 allowCredentials도 켜져 있다).
     * 로컬 개발은 application.yml의 기본값이 이미 5173을 넣어 주므로 손실이 없다 — 단, 확장
     * 테스트처럼 변수를 직접 줄 때는 5173도 **함께** 적어야 한다(.env.example 참고).
     *
     * <p>참고: 목록에 없는 Origin은 브라우저가 막기 전에 Spring이 403 "Invalid CORS request"로
     * 먼저 자른다. 크롬 확장은 GET엔 Origin을 안 보내고 POST엔 보내므로, 등록이 빠져 있으면
     * "조회는 되는데 저장만 403"이라는 헷갈리는 증상이 된다(2026-08-02 실측).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        LinkedHashSet<String> allowedOrigins = new LinkedHashSet<>();
        Arrays.stream(allowedOriginsValue.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .forEach(allowedOrigins::add);
        config.setAllowedOrigins(List.copyOf(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // OAuth2 로그인은 인가 요청을 세션에 잠깐 저장했다가 콜백에서 대조하는 방식이라
                // STATELESS로 두면 매번 "authorization_request_not_found"로 실패한다.
                // JWT 인증 자체는 세션이 필요 없지만, 필요한 쪽(oauth2Login)이 있으니 IF_REQUIRED로 둔다.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(restAuthenticationEntryPoint))
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(customOidcUserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler))
                .build();
    }
}
