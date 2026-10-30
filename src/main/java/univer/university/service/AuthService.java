package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.CurrentUserProvider;
import univer.university.dto.request.AuthRequests;
import univer.university.dto.response.AuthResponses.LoginResponse;
import univer.university.dto.response.AuthResponses.MeResponse;
import univer.university.dto.response.AuthResponses.UserDto;
import univer.university.dto.response.RoleResponses.PermissionRow;
import univer.university.entity.User;
import univer.university.exception.BusinessException;
import univer.university.exception.NotFoundException;
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
    private final PasswordEncoder encoder;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;

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
        return new LoginResponse(jwt.generate(u), jwt.getExpirationMs(), UserService.toDto(u), permissionsOf(u));
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        User u = currentEntity();
        return new MeResponse(UserService.toDto(u), permissionsOf(u));
    }

    /** O'z parolini almashtirish: eski parol to'g'ri bo'lishi shart. */
    @Transactional
    public void changePassword(AuthRequests.ChangePassword req) {
        User u = currentEntity();
        if (!encoder.matches(req.oldPassword(), u.getPasswordHash())) {
            throw new BusinessException("Eski parol noto'g'ri");
        }
        if (req.oldPassword().equals(req.newPassword())) {
            throw new BusinessException("Yangi parol eskisidan farq qilishi kerak");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        audit.log("PASSWORD", "User", u.getId(), "parol almashtirildi");
    }

    private User currentEntity() {
        return users.findById(currentUser.id()).orElseThrow(() -> new NotFoundException("Foydalanuvchi topilmadi"));
    }

    private List<PermissionRow> permissionsOf(User u) {
        return matrix.matrix().stream().filter(p -> p.role() == u.getRole()).toList();
    }
}
