package univer.university.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univer.university.component.CurrentUserProvider;
import univer.university.component.DateUtils;
import univer.university.dto.request.UserRequests;
import univer.university.dto.response.AuthResponses.UserDto;
import univer.university.entity.User;
import univer.university.entity.enums.Role;
import univer.university.exception.BusinessException;
import univer.university.exception.NotFoundException;
import univer.university.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;

    public static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isActive(), u.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return users.findAll().stream().map(UserService::toDto).toList();
    }

    @Transactional
    public UserDto create(UserRequests.Create req) {
        guardEgasi(req.role());
        if (users.existsByUsername(req.username().trim())) {
            throw BusinessException.conflict("Bu login band");
        }
        User u = users.save(User.builder().username(req.username().trim()).passwordHash(encoder.encode(req.password()))
                .fullName(req.fullName().trim()).role(req.role()).active(true).createdAt(DateUtils.now()).build());
        audit.log("CREATE", "User", u.getId(), "login=" + u.getUsername() + ", rol=" + u.getRole());
        return toDto(u);
    }

    @Transactional
    public UserDto update(Long id, UserRequests.Update req) {
        User u = users.findById(id).orElseThrow(() -> new NotFoundException("Xodim topilmadi"));
        if (u.getRole() == Role.EGASI) {
            guardEgasi(Role.EGASI);
        }
        if (req.role() != null) {
            guardEgasi(req.role());
            u.setRole(req.role());
        }
        if (req.fullName() != null && !req.fullName().isBlank()) {
            u.setFullName(req.fullName().trim());
        }
        if (req.active() != null) {
            if (!req.active() && u.getId().equals(currentUser.id())) {
                throw new BusinessException("O'zingizni o'chira olmaysiz");
            }
            u.setActive(req.active());
        }
        if (req.password() != null && !req.password().isBlank()) {
            u.setPasswordHash(encoder.encode(req.password()));
        }
        audit.log("UPDATE", "User", u.getId(), "login=" + u.getUsername() + ", rol=" + u.getRole() + ", faol=" + u.isActive());
        return toDto(u);
    }

    /** EGASI rolini faqat EGASI bera/o'zgartira oladi. */
    private void guardEgasi(Role target) {
        if (target == Role.EGASI && currentUser.current().role() != Role.EGASI) {
            throw new BusinessException("EGASI rolini faqat egasi boshqara oladi", HttpStatus.FORBIDDEN);
        }
    }
}
