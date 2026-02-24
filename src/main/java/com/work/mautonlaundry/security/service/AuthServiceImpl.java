package com.work.mautonlaundry.security.service;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.requests.userrequests.UserLoginRequest;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@AllArgsConstructor
public class AuthServiceImpl implements AuthService {

    private AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    /**
     * @param loginDto
     * @return
     * @throws Exception
     */

    @Override
    @Transactional
    public String login(UserLoginRequest loginDto) {

        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                loginDto.getEmail(),
                loginDto.getPassword()
        ));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Update first login status
        AppUser user = userRepository.findUserByEmail(loginDto.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.getIsFirstLogin()) {
            user.setIsFirstLogin(false);
            userRepository.save(user);
        }

        String token = jwtTokenProvider.generateToken(authentication);

        return token;
    }

    public Optional<AppUser> getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        // Assuming your User implements UserDetails
        return Optional.ofNullable((AppUser) authentication.getPrincipal());
    }



}
