package univer.university.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import univer.university.dto.request.UserRequests;
import univer.university.dto.response.AuthResponses.UserDto;
import univer.university.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @GetMapping
    @PreAuthorize("@perm.view('XODIMLAR')")
    public List<UserDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.view('XODIMLAR')")
    public UserDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.create('XODIMLAR')")
    public UserDto create(@Valid @RequestBody UserRequests.Create req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.edit('XODIMLAR')")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UserRequests.Update req) {
        return service.update(id, req);
    }
}
