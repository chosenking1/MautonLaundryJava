package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, String> {
    Optional<VerificationToken> findByToken(String token);
    Optional<VerificationToken> findByUserIdAndTokenType(String userId, VerificationToken.TokenType tokenType);
    void deleteByUserIdAndTokenType(String userId, VerificationToken.TokenType tokenType);
}