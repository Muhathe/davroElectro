package univer.university.component;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import univer.university.exception.BusinessException;
import univer.university.security.UserPrincipal;
import org.springframework.http.HttpStatus;

/** Joriy foydalanuvchi (JWT dan). Sotuvchi frontenddan tanlanmaydi. */
@Component
public class CurrentUserProvider {

    public UserPrincipal current() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof UserPrincipal p)) {
            throw new BusinessException("Foydalanuvchi aniqlanmadi", HttpStatus.UNAUTHORIZED);
        }
        return p;
    }

    public Long id() {
        return current().id();
    }
}
