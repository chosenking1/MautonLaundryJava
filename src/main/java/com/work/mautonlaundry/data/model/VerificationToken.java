package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@Table(name = "verification_tokens")
public class VerificationToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String token;
    
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    
    @Column(nullable = false)
    private LocalDateTime expiryDate;
    
    @Enumerated(EnumType.STRING)
    private TokenType tokenType;
    
    @Column(nullable = false)
    private boolean used = false;
    
    public enum TokenType {
        EMAIL_VERIFICATION,
        PASSWORD_RESET
    }
    
    public VerificationToken(String token, AppUser user, TokenType tokenType, int expiryHours) {
        this.token = token;
        this.user = user;
        this.tokenType = tokenType;
        this.expiryDate = LocalDateTime.now().plusHours(expiryHours);
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }
}