package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.UserDto;
import com.iotplatform.iotcore.dto.UserResponse;
import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private UserEntity testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = new UserEntity(userId, "TestUser", "test@example.com", null, LocalDate.now(), new byte[48], com.iotplatform.iotcore.model.UserRole.GUEST);
        // По умолчанию пользователь не подтвержден
        testUser.setConfirmed(false);
    }

    @Test
    void confirm_SetsConfirmedToTrue() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        userService.confirm(userId);

        assertTrue(testUser.isConfirmed(), "Аккаунт должен стать подтвержденным");
        verify(userRepository, times(1)).save(testUser);
    }
}