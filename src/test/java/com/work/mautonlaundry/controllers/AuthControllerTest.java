package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void sendEmailVerification_Success() throws Exception {
        doNothing().when(userService).sendEmailVerification(anyString());

        mockMvc.perform(post("/api/auth/send-verification")
                .param("email", "test@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("Verification email sent"));

        verify(userService).sendEmailVerification("test@example.com");
    }

    @Test
    void verifyEmail_Success() throws Exception {
        when(userService.verifyEmail(anyString())).thenReturn(true);

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"valid-token\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Email verified successfully"));
    }

    @Test
    void verifyEmail_InvalidToken() throws Exception {
        when(userService.verifyEmail(anyString())).thenReturn(false);

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"invalid-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid or expired token"));
    }

    @Test
    void forgotPassword_Success() throws Exception {
        doNothing().when(userService).sendPasswordResetEmail(anyString());

        mockMvc.perform(post("/api/auth/forgot-password")
                .param("email", "test@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset email sent"));
    }

    @Test
    void resetPassword_Success() throws Exception {
        when(userService.resetPassword(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"valid-token\",\"newPassword\":\"newPassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset successfully"));
    }

    @Test
    void sendEmailVerification_InvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/send-verification")
                .param("email", "invalid-email"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendEmailVerification_EmptyEmail() throws Exception {
        mockMvc.perform(post("/api/auth/send-verification")
                .param("email", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmail_EmptyToken() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmail_MissingToken() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPassword_InvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                .param("email", "not-an-email"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_InvalidToken() throws Exception {
        when(userService.resetPassword(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"invalid-token\",\"newPassword\":\"newPassword123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid or expired token"));
    }

    @Test
    void resetPassword_ShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"valid-token\",\"newPassword\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_LongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"valid-token\",\"newPassword\":\"thisPasswordIsTooLongForValidation\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_MissingPassword() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"valid-token\"}"))
                .andExpect(status().isBadRequest());
    }
}