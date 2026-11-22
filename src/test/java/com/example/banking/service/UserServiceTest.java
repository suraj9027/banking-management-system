package com.example.banking.service;

import com.example.banking.dto.RegisterRequest;
import com.example.banking.dto.UpdateProfileRequest;
import com.example.banking.dto.UserDto;
import com.example.banking.entity.Role;
import com.example.banking.entity.User;
import com.example.banking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registrationAlwaysAssignsCustomerRole() {
        RegisterRequest request = new RegisterRequest(
                "demo-user",
                "demo@example.com",
                "password123",
                "Demo",
                "User",
                "ADMIN"
        );
        when(userRepository.existsByUsernameIgnoreCase(request.username())).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.register(request);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals(Role.CUSTOMER, savedUser.getValue().getRole());
    }

    @Test
    void registrationNormalizesUsernameAndEmailBeforeSaving() {
        RegisterRequest request = new RegisterRequest(
                " demo-user ",
                " Demo@Example.com ",
                "password123",
                " Demo ",
                " User ",
                null
        );
        when(userRepository.existsByUsernameIgnoreCase("demo-user")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("demo@example.com")).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto result = userService.register(request);

        assertEquals("demo-user", result.username());
        assertEquals("demo@example.com", result.email());
    }

    @Test
    void profileUpdateRejectsAnotherUsersEmail() {
        User user = new User();
        user.setId(7L);
        user.setUsername("demo-user");
        when(userRepository.findByUsernameIgnoreCase("demo-user")).thenReturn(java.util.Optional.of(user));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("other@example.com", 7L)).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.example.banking.exception.DuplicateResourceException.class,
                () -> userService.updateProfile("demo-user",
                        new UpdateProfileRequest("other@example.com", "Demo", "User"))
        );

        verify(userRepository, never()).save(any(User.class));
    }
}
