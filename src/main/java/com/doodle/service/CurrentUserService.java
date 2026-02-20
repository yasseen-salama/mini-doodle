package com.doodle.service;

import com.doodle.domain.User;
import com.doodle.exception.ForbiddenException;
import com.doodle.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UUID resolveUserId(String principalName) {
        String normalizedEmail = principalName.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ForbiddenException("Authenticated user not found"));
        return user.getId();
    }
}
