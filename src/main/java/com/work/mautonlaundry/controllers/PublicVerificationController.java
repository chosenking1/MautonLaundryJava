package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PublicVerificationController {

    private static final Logger log = LoggerFactory.getLogger(PublicVerificationController.class);
    
    private final UserService userService;

    public PublicVerificationController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/verify")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        log.info("Email verification attempt via /verify endpoint with token");
        try {
            boolean verified = userService.verifyEmail(token);
            if (verified) {
                log.info("Email verification successful");
                return ResponseEntity.ok()
                        .header("Content-Type", "text/html; charset=UTF-8")
                        .body(buildSuccessHtml());
            }
            log.warn("Email verification failed - invalid or expired token");
            return ResponseEntity.badRequest()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(buildErrorHtml("Invalid or expired token. Please request a new verification email."));
        } catch (Exception e) {
            log.error("Error during email verification", e);
            return ResponseEntity.badRequest()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(buildErrorHtml("Error verifying email: " + e.getMessage()));
        }
    }

    @GetMapping("/reset-password")
    public ResponseEntity<String> resetPasswordPage(@RequestParam String token) {
        log.info("Password reset page accessed via /reset-password endpoint");
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(buildResetPasswordHtml());
    }

    private String buildSuccessHtml() {
        return "<!DOCTYPE html>" +
                "<html lang=\"en\">" +
                "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<title>Email Verified - Imototo Clean</title>" +
                "<style>" +
                "* { margin: 0; padding: 0; box-sizing: border-box; }" +
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px; }" +
                ".container { background: white; border-radius: 20px; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25); padding: 60px 40px; text-align: center; max-width: 450px; width: 100%; }" +
                ".icon { width: 80px; height: 80px; background: #10B981; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 30px; }" +
                ".icon svg { width: 40px; height: 40px; fill: white; }" +
                "h1 { color: #1F2937; font-size: 28px; font-weight: 700; margin-bottom: 16px; }" +
                "p { color: #6B7280; font-size: 16px; line-height: 1.6; margin-bottom: 30px; }" +
                ".btn { display: inline-block; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 14px 32px; border-radius: 12px; text-decoration: none; font-weight: 600; font-size: 16px; transition: transform 0.2s, box-shadow 0.2s; }" +
                ".btn:hover { transform: translateY(-2px); box-shadow: 0 10px 20px rgba(102, 126, 234, 0.4); }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class=\"container\">" +
                "<div class=\"icon\">" +
                "<svg viewBox=\"0 0 20 20\" xmlns=\"http://www.w3.org/2000/svg\">" +
                "<path fill-rule=\"evenodd\" d=\"M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z\" clip-rule=\"evenodd\"/>" +
                "</svg>" +
                "</div>" +
                "<h1>Email Verified!</h1>" +
                "<p>Your email has been successfully verified. You can now log in to your Imototo Clean account and start using our services.</p>" +
                "<a href=\"https://imototo-clean.vercel.app/login\" class=\"btn\">Go to Login</a>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    private String buildErrorHtml(String message) {
        return "<!DOCTYPE html>" +
                "<html lang=\"en\">" +
                "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<title>Verification Failed - Imototo Clean</title>" +
                "<style>" +
                "* { margin: 0; padding: 0; box-sizing: border-box; }" +
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px; }" +
                ".container { background: white; border-radius: 20px; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25); padding: 60px 40px; text-align: center; max-width: 450px; width: 100%; }" +
                ".icon { width: 80px; height: 80px; background: #EF4444; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 30px; }" +
                ".icon svg { width: 40px; height: 40px; fill: white; }" +
                "h1 { color: #1F2937; font-size: 28px; font-weight: 700; margin-bottom: 16px; }" +
                "p { color: #6B7280; font-size: 16px; line-height: 1.6; margin-bottom: 30px; }" +
                ".btn { display: inline-block; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: white; padding: 14px 32px; border-radius: 12px; text-decoration: none; font-weight: 600; font-size: 16px; transition: transform 0.2s, box-shadow 0.2s; }" +
                ".btn:hover { transform: translateY(-2px); box-shadow: 0 10px 20px rgba(245, 87, 108, 0.4); }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class=\"container\">" +
                "<div class=\"icon\">" +
                "<svg viewBox=\"0 0 20 20\" xmlns=\"http://www.w3.org/2000/svg\">" +
                "<path fill-rule=\"evenodd\" d=\"M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z\" clip-rule=\"evenodd\"/>" +
                "</svg>" +
                "</div>" +
                "<h1>Verification Failed</h1>" +
                "<p>" + message + "</p>" +
                "<a href=\"https://imototo-clean.vercel.app/resend-verification\" class=\"btn\">Request New Email</a>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    private String buildResetPasswordHtml() {
        return "<!DOCTYPE html>" +
                "<html lang=\"en\">" +
                "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<title>Reset Password - Imototo Clean</title>" +
                "<style>" +
                "* { margin: 0; padding: 0; box-sizing: border-box; }" +
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #fa709a 0%, #fee140 100%); min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px; }" +
                ".container { background: white; border-radius: 20px; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25); padding: 60px 40px; text-align: center; max-width: 450px; width: 100%; }" +
                ".icon { width: 80px; height: 80px; background: linear-gradient(135deg, #fa709a 0%, #fee140 100%); border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 30px; }" +
                ".icon svg { width: 40px; height: 40px; fill: white; }" +
                "h1 { color: #1F2937; font-size: 28px; font-weight: 700; margin-bottom: 16px; }" +
                "p { color: #6B7280; font-size: 16px; line-height: 1.6; margin-bottom: 30px; }" +
                ".btn { display: inline-block; background: linear-gradient(135deg, #fa709a 0%, #fee140 100%); color: white; padding: 14px 32px; border-radius: 12px; text-decoration: none; font-weight: 600; font-size: 16px; transition: transform 0.2s, box-shadow 0.2s; }" +
                ".btn:hover { transform: translateY(-2px); box-shadow: 0 10px 20px rgba(250, 112, 154, 0.4); }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class=\"container\">" +
                "<div class=\"icon\">" +
                "<svg viewBox=\"0 0 20 20\" xmlns=\"http://www.w3.org/2000/svg\">" +
                "<path fill-rule=\"evenodd\" d=\"M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z\" clip-rule=\"evenodd\"/>" +
                "</svg>" +
                "</div>" +
                "<h1>Reset Password</h1>" +
                "<p>Please use the Imototo Clean mobile app to reset your password.</p>" +
                "<a href=\"https://imototo-clean.vercel.app/reset-password\" class=\"btn\">Open App to Reset</a>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}
