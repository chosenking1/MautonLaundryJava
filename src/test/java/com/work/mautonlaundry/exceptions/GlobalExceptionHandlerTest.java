package com.work.mautonlaundry.exceptions;

import com.work.mautonlaundry.exceptions.userexceptions.UserAlreadyExistsException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    void handleUserNotFound_ReturnsNotFound() {
        UserNotFoundException exception = new UserNotFoundException("User not found");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
            globalExceptionHandler.handleUserNotFound(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("USER_NOT_FOUND", response.getBody().getCode());
        assertEquals("User not found", response.getBody().getMessage());
    }

    @Test
    void handleUserAlreadyExists_ReturnsConflict() {
        UserAlreadyExistsException exception = new UserAlreadyExistsException("User already exists");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
            globalExceptionHandler.handleUserAlreadyExists(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("USER_ALREADY_EXISTS", response.getBody().getCode());
    }

    @Test
    void handleAccessDenied_ReturnsForbidden() {
        AccessDeniedException exception = new AccessDeniedException("Access denied");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
            globalExceptionHandler.handleAccessDenied(exception);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("ACCESS_DENIED", response.getBody().getCode());
    }

    @Test
    void handleValidationExceptions_ReturnsBadRequest() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("user", "email", "Email is required");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
            globalExceptionHandler.handleValidationExceptions(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("email"));
    }

    @Test
    void handleGeneral_ReturnsInternalServerError() {
        Exception exception = new RuntimeException("Unexpected error");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
            globalExceptionHandler.handleGeneral(exception);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().getCode());
    }
}