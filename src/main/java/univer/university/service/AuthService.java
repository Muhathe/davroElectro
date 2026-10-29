package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.dto.request.AuthRequests;
import univer.university.dto.response.AuthResponses.LoginResponse;
import univer.university.dto.response.AuthResponses.UserDto;
import univer.university.dto.response.RoleResponses.PermissionRow;
import univer.university.entity.User;
import univer.university.exception.BusinessException;
import univer.university.repository.UserRepository;
import univer.university.security.JwtService;
import univer.university.security.PermissionMatrixService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwt;
    private final PermissionMatrixService matrix;

    @Transactional(readOnly = true)
    public LoginResponse login(AuthRequests.Login req) {
        String username = req.username().trim();
        try {
            // Parol va aktivlik CustomUserDetailsService + DaoAuthenticationProvider orqali tekshiriladi
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, req.password()));
        } catch (AuthenticationException e) {
            throw new BusinessException("Login yoki parol noto'g'ri", HttpStatus.UNAUTHORIZED);
        }
        User u = users.findByUsername(username)
                .orElseThrow(() -> new BusinessException("Login yoki parol noto'g'ri", HttpStatus.UNAUTHORIZED));
        List<PermissionRow> perms = matrix.matrix().stream().filter(p -> p.role() == u.getRole()).toList();
        return new LoginResponse(jwt.generate(u), jwt.getExpirationMs(), UserService.toDto(u), perms);
    }
}
