package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.User;
import com.work.mautonlaundry.data.model.VerificationToken;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.data.repository.VerificationTokenRepository;
import com.work.mautonlaundry.dtos.requests.userrequests.RegisterUserRequest;
import com.work.mautonlaundry.dtos.responses.userresponse.RegisterUserResponse;
import com.work.mautonlaundry.exceptions.userexceptions.UserAlreadyExistsException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import com.work.mautonlaundry.security.service.AuthService;
import com.work.mautonlaundry.util.TokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private VerificationTokenRepository tokenRepository;
    
    @Mock
    private EmailService emailService;
    
    @Mock
    private TokenGenerator tokenGenerator;
    
    @Mock
    private CacheManager cacheManager;
    
    @Mock
    private AuthService authService;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private ModelMapper mapper;
    
    @Mock
    private Cache cache;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private RegisterUserRequest registerRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("test-id");
        testUser.setEmail("test@example.com");
        testUser.setFull_name("Test User");
        testUser.setUserRole(UserRole.USER);
        testUser.setEmailVerified(false);

        registerRequest = new RegisterUserRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setFirstname("Test");
        registerRequest.setSecond_name("User");
        registerRequest.setPassword("password123");
        registerRequest.setAddress("Test Address");
        registerRequest.setPhone_number("1234567890");
    }

    @Test
    void registerUser_Success() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        RegisterUserResponse response = userService.registerUser(registerRequest);

        assertNotNull(response);
        assertEquals("test@example.com", response.getEmail());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerUser_EmailAlreadyExists() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, 
            () -> userService.registerUser(registerRequest));
    }

    @Test
    void findUserByEmail_Success() {
        when(userRepository.findUserByEmail(anyString())).thenReturn(Optional.of(testUser));

        userService.findUserByEmail("test@example.com");

        verify(userRepository).findUserByEmail("test@example.com");
        verify(mapper).map(eq(testUser), any());
    }

    @Test
    void findUserByEmail_NotFound() {
        when(userRepository.findUserByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, 
            () -> userService.findUserByEmail("test@example.com"));
    }

    @Test
    void sendEmailVerification_Success() {
        when(userRepository.findUserByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(tokenGenerator.generateSecureToken()).thenReturn("secure-token");

        userService.sendEmailVerification("test@example.com");

        verify(tokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendVerificationEmail("test@example.com", "secure-token");
    }

    @Test
    void verifyEmail_Success() {
        VerificationToken token = new VerificationToken("token", testUser, VerificationToken.TokenType.EMAIL_VERIFICATION);
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(token));
        when(cacheManager.getCache("users")).thenReturn(cache);

        boolean result = userService.verifyEmail("token");

        assertTrue(result);
        assertTrue(testUser.getEmailVerified());
        verify(userRepository).save(testUser);
        verify(cache).evict("test@example.com");
    }

    @Test
    void verifyEmail_InvalidToken() {
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

        boolean result = userService.verifyEmail("invalid-token");

        assertFalse(result);
    }

    @Test
    void resetPassword_Success() {
        VerificationToken token = new VerificationToken("token", testUser, VerificationToken.TokenType.PASSWORD_RESET);
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode(anyString())).thenReturn("new-encoded-password");
        when(cacheManager.getCache("users")).thenReturn(cache);

        boolean result = userService.resetPassword("token", "newPassword123");

        assertTrue(result);
        verify(userRepository).save(testUser);
        verify(cache).evict("test@example.com");
    }

    @Test
    void resetPassword_InvalidPassword() {
        VerificationToken token = new VerificationToken("token", testUser, VerificationToken.TokenType.PASSWORD_RESET);
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(token));

        assertThrows(IllegalArgumentException.class, 
            () -> userService.resetPassword("token", "123"));
    }

    @Test
    void findUserById_NotFound() {
        when(userRepository.findUserById(anyString())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, 
            () -> userService.findUserById("invalid-id"));
    }

    @Test
    void sendEmailVerification_UserNotFound() {
        when(userRepository.findUserByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, 
            () -> userService.sendEmailVerification("nonexistent@example.com"));
    }

    @Test
    void sendPasswordResetEmail_UserNotFound() {
        when(userRepository.findUserByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, 
            () -> userService.sendPasswordResetEmail("nonexistent@example.com"));
    }

    @Test
    void verifyEmail_ExpiredToken() {
        VerificationToken expiredToken = new VerificationToken("token", testUser, VerificationToken.TokenType.EMAIL_VERIFICATION);
        expiredToken.setExpiryDate(java.time.LocalDateTime.now().minusDays(1)); // Expired
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(expiredToken));

        boolean result = userService.verifyEmail("expired-token");

        assertFalse(result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_UsedToken() {
        VerificationToken usedToken = new VerificationToken("token", testUser, VerificationToken.TokenType.EMAIL_VERIFICATION);
        usedToken.setUsed(true);
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(usedToken));

        boolean result = userService.verifyEmail("used-token");

        assertFalse(result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_ExpiredToken() {
        VerificationToken expiredToken = new VerificationToken("token", testUser, VerificationToken.TokenType.PASSWORD_RESET);
        expiredToken.setExpiryDate(java.time.LocalDateTime.now().minusDays(1));
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(expiredToken));

        boolean result = userService.resetPassword("expired-token", "newPassword123");

        assertFalse(result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_NullPassword() {
        VerificationToken token = new VerificationToken("token", testUser, VerificationToken.TokenType.PASSWORD_RESET);
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(token));

        assertThrows(IllegalArgumentException.class, 
            () -> userService.resetPassword("token", null));
    }

    @Test
    void resetPassword_TooLongPassword() {
        VerificationToken token = new VerificationToken("token", testUser, VerificationToken.TokenType.PASSWORD_RESET);
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(token));

        assertThrows(IllegalArgumentException.class, 
            () -> userService.resetPassword("token", "thisPasswordIsTooLongForValidation"));
    }

    @Test
    void deleteUserById_UserNotFound() {
        when(userRepository.findUserById(anyString())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, 
            () -> userService.deleteUserById("invalid-id"));
    }

    @Test
    void deleteUserByEmail_UserNotFound() {
        when(userRepository.findUserByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, 
            () -> userService.deleteUserByEmail("nonexistent@example.com"));
    }
}