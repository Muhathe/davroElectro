package univer.university.security;

import univer.university.entity.enums.Role;

public record UserPrincipal(Long id, String username, Role role) {
}
