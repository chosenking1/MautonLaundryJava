package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDashboard_Success() throws Exception {
        when(userRepository.count()).thenReturn(100L);
        when(userRepository.countByDeletedFalse()).thenReturn(95L);
        when(userRepository.countByEmailVerifiedTrue()).thenReturn(80L);

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(100))
                .andExpect(jsonPath("$.activeUsers").value(95))
                .andExpect(jsonPath("$.verifiedUsers").value(80));
    }

    @Test
    void getDashboard_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getDashboard_InsufficientRole() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDashboard_DatabaseError() throws Exception {
        when(userRepository.count()).thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDashboard_NullValues() throws Exception {
        when(userRepository.count()).thenReturn(null);
        when(userRepository.countByDeletedFalse()).thenReturn(0L);
        when(userRepository.countByEmailVerifiedTrue()).thenReturn(0L);

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isEmpty());
    }
}