package univer.university.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import univer.university.entity.User;
import univer.university.repository.UserRepository;

import java.io.IOException;
import java.util.List;

/** SecurityConfig ichida qo'lda ro'yxatdan o'tadi (servlet filter sifatida o'chirilgan). */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Long uid = jwtService.parseUserId(header.substring(7));
            if (uid != null) {
                User u = userRepository.findById(uid).orElse(null);
                if (u != null && u.isActive()) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            new UserPrincipal(u.getId(), u.getUsername(), u.getRole()), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(request, response);
    }
}
