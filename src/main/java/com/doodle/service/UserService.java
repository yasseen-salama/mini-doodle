package com.doodle.service;

import com.doodle.domain.Calendar;
import com.doodle.domain.User;
import com.doodle.dto.request.RegisterUserRequest;
import com.doodle.dto.response.UserResponse;
import com.doodle.exception.EmailAlreadyExistsException;
import com.doodle.mapper.UserMapper;
import com.doodle.repository.CalendarRepository;
import com.doodle.repository.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CalendarRepository calendarRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(
            UserRepository userRepository,
            CalendarRepository calendarRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper
    ) {
        this.userRepository = userRepository;
        this.calendarRepository = calendarRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        User savedUser = userRepository.save(user);

        Calendar calendar = new Calendar();
        calendar.setId(UUID.randomUUID());
        calendar.setUserId(savedUser.getId());
        calendarRepository.save(calendar);

        return userMapper.toUserResponse(savedUser);
    }
}
