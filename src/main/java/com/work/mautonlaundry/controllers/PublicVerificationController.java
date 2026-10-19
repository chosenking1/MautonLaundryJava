package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.services.EmailVerificationOutcome;
import com.work.mautonlaundry.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@RestController
public class PublicVerificationController {

    private static final Logger log = LoggerFactory.getLogger(PublicVerificationController.class);

    private final UserService userService;
    private final String appUrl;
    private final String supportEmail;
    private final int expiryHours;
    private final String logoDataUri;

    public PublicVerificationController(
            UserService userService,
            @Value("${app.customer-app-url:imototo://login}") String appUrl,
            @Value("${app.admin.team-email:admin@imototo.com}") String supportEmail,
            @Value("${app.token.verification.expiry-hours:24}") int expiryHours) {
        this.userService = userService;
        this.appUrl = appUrl;
        this.supportEmail = supportEmail;
        this.expiryHours = expiryHours;
        this.logoDataUri = loadLogo();
    }

    /**
     * Where the verification email lands, in a browser. One branded page per
     * outcome -- the link used to hit a JSON endpoint and show a bare line of
     * text, which read like the app was broken (tester feedback, Oct 2026).
     */
    @GetMapping(value = "/verify", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> verifyEmail(@RequestParam(required = false) String token) {
        EmailVerificationOutcome outcome;
        try {
            outcome = userService.verifyEmail(token);
        } catch (Exception e) {
            // Never echo the exception: it is internal, and this page is public.
            log.error("Error during email verification", e);
            outcome = EmailVerificationOutcome.INVALID;
        }
        log.info("Email verification via /verify: {}", outcome);

        HttpStatus status = outcome.isConfirmed() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(switch (outcome) {
            case VERIFIED -> verifiedPage();
            case ALREADY_VERIFIED -> alreadyVerifiedPage();
            case EXPIRED -> expiredPage(token);
            case INVALID -> invalidPage();
        });
    }

    @GetMapping("/reset-password")
    public ResponseEntity<String> resetPasswordPage(@RequestParam String token) {
        log.info("Password reset page accessed via /reset-password endpoint");
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(buildResetPasswordHtml(token));
    }

    // ---- verification pages ----

    private String verifiedPage() {
        return page("Email confirmed", Icon.SUCCESS,
                "Your email has been confirmed",
                "Thanks for confirming. Your Imototo account is ready, so you can log in and book your first pickup.",
                primaryLink("Log in to Imototo", appUrl)
                        + secondary("On a computer? Open the Imototo app on your phone and log in there."),
                "");
    }

    private String alreadyVerifiedPage() {
        return page("Already confirmed", Icon.ALREADY,
                "Your email is already confirmed",
                "There's nothing more to do. You can log in and carry on where you left off.",
                primaryLink("Log in to Imototo", appUrl)
                        + secondary("On a computer? Open the Imototo app on your phone and log in there."),
                "");
    }

    private String expiredPage(String token) {
        return page("Link expired", Icon.EXPIRED,
                "This link has expired",
                "Verification links only last " + expiryHours + " hours, to keep your account safe. "
                        + "We can send you a fresh one right away.",
                "<button type=\"button\" class=\"btn\" id=\"resend\">Resend verification email</button>"
                        + "<p class=\"note\" id=\"note\" role=\"status\"></p>"
                        + secondary("or <a href=\"" + attr(appUrl) + "\">log in to your account</a>"),
                "var btn=document.getElementById('resend'),note=document.getElementById('note');"
                        + "btn.addEventListener('click',function(){"
                        + "btn.disabled=true;btn.textContent='Sending\u2026';note.className='note';note.textContent='';"
                        + "fetch('/api/auth/resend-verification-link',{method:'POST',"
                        + "headers:{'Content-Type':'application/json'},"
                        + "body:JSON.stringify({token:'" + jsToken(token) + "'})})"
                        + ".then(function(r){if(r.ok){done();return;}"
                        + "return r.json().then(function(j){fail(j&&j.message);},function(){fail();});})"
                        + ".catch(function(){fail();});});"
                        + "function done(){btn.style.display='none';note.className='note ok';"
                        + "note.textContent='Check your inbox. We\\'ve sent a new link to your email.';}"
                        + "function fail(m){btn.disabled=false;btn.textContent='Resend verification email';"
                        + "note.className='note err';note.textContent=m||'We couldn\\'t send it just now. Please try again.';}");
    }

    private String invalidPage() {
        return page("Link not valid", Icon.INVALID,
                "This link isn't valid",
                "It may have been copied incompletely, or replaced by a newer email. "
                        + "Enter your email and we'll send you a new link.",
                "<form id=\"f\" novalidate>"
                        + "<label for=\"email\" class=\"sr\">Email address</label>"
                        + "<input type=\"email\" id=\"email\" placeholder=\"you@example.com\" autocomplete=\"email\" required>"
                        + "<button type=\"submit\" class=\"btn\" id=\"resend\">Resend verification email</button>"
                        + "</form>"
                        + "<p class=\"note\" id=\"note\" role=\"status\"></p>"
                        + secondary("Need help? <a href=\"mailto:" + attr(supportEmail) + "\">Contact support</a>"),
                "var f=document.getElementById('f'),btn=document.getElementById('resend'),note=document.getElementById('note');"
                        + "f.addEventListener('submit',function(e){e.preventDefault();"
                        + "var email=document.getElementById('email').value.trim();"
                        + "if(!/^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$/.test(email)){note.className='note err';"
                        + "note.textContent='Enter a valid email address.';return;}"
                        + "btn.disabled=true;btn.textContent='Sending\u2026';note.className='note';note.textContent='';"
                        + "fetch('/api/auth/send-verification?email='+encodeURIComponent(email),{method:'POST'})"
                        // Same message whether or not the account exists, so the
                        // page cannot be used to probe which emails are registered.
                        + ".then(function(r){if(r.status>=500){fail();return;}"
                        + "f.style.display='none';note.className='note ok';"
                        + "note.textContent='If an account uses that email and isn\\'t confirmed yet, a new link is on its way.';})"
                        + ".catch(function(){fail();});});"
                        + "function fail(){btn.disabled=false;btn.textContent='Resend verification email';"
                        + "note.className='note err';note.textContent='We couldn\\'t send it just now. Please try again.';}");
    }

    private enum Icon {
        // Fills and strokes are the app's own tokens (AppTheme): successLight /
        // imototoSuccess, lightBlue / imototoNavy, warningLight / warningIcon.
        SUCCESS("#E7F7EE", "#16A34A", "<path d=\"M5 12.5l4.5 4.5L19 7.5\"/>"),
        ALREADY("#E8EEF7", "#1A3A6B",
                "<circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M8.5 12.2l2.4 2.4 4.6-4.8\"/>"),
        EXPIRED("#FFF7E6", "#FF8B00", "<circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M12 7.5V12l3 2\"/>"),
        INVALID("#FDECEA", "#DE350B",
                "<path d=\"M12 4l9 16H3z\"/><path d=\"M12 10v4\"/><path d=\"M12 17.2v.1\"/>");

        final String fill;
        final String stroke;
        final String paths;

        Icon(String fill, String stroke, String paths) {
            this.fill = fill;
            this.stroke = stroke;
            this.paths = paths;
        }

        String svg() {
            return "<div class=\"icon\" style=\"background:" + fill + "\">"
                    + "<svg viewBox=\"0 0 24 24\" width=\"36\" height=\"36\" fill=\"none\" stroke=\"" + stroke
                    + "\" stroke-width=\"2.2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\">"
                    + paths + "</svg></div>";
        }
    }

    /**
     * The shared card: logo, status icon, heading, message, actions. Colours,
     * radii, the 52px control height and the Outfit typeface all mirror the
     * customer app's AppTheme, so the page reads as part of the app.
     */
    private String page(String title, Icon icon, String heading, String message, String actions, String script) {
        return "<!DOCTYPE html><html lang=\"en\"><head>"
                + "<meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<meta name=\"robots\" content=\"noindex\">"
                + "<title>" + title + " - Imototo</title>"
                + "<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">"
                + "<link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700&display=swap\">"
                + "<style>"
                + "*{margin:0;padding:0;box-sizing:border-box}"
                + "body{font-family:'Outfit',-apple-system,'Segoe UI',Roboto,sans-serif;background:#F5F7FA;color:#1A1A2E;"
                + "min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px 16px}"
                + ".card{background:#FFFFFF;border:1px solid #E5E7EB;border-radius:16px;box-shadow:0 2px 8px rgba(0,0,0,.05);"
                + "max-width:420px;width:100%;padding:32px 24px 28px;text-align:center}"
                + ".logo{width:56px;height:56px;border-radius:14px;display:block;margin:0 auto 28px}"
                + ".icon{width:72px;height:72px;border-radius:50%;display:flex;align-items:center;justify-content:center;margin:0 auto 20px}"
                + "h1{font-size:22px;font-weight:700;line-height:1.3;margin-bottom:10px}"
                + ".msg{font-size:15px;line-height:1.55;color:#6B7280;margin-bottom:24px}"
                + ".btn{display:flex;align-items:center;justify-content:center;width:100%;height:52px;border:0;border-radius:12px;"
                + "background:#1A3A6B;color:#FFFFFF;font:600 16px 'Outfit',-apple-system,'Segoe UI',Roboto,sans-serif;"
                + "text-decoration:none;cursor:pointer}"
                + ".btn:hover{background:#15305A}.btn:disabled{opacity:.6;cursor:default}"
                + "input{width:100%;height:52px;padding:0 14px;margin-bottom:12px;border:1px solid #E5E7EB;border-radius:12px;"
                + "font:400 16px 'Outfit',-apple-system,'Segoe UI',Roboto,sans-serif;color:#1A1A2E}"
                + "input:focus{outline:none;border-color:#1A3A6B;box-shadow:0 0 0 3px #E8EEF7}"
                + ".note{font-size:14px;line-height:1.5;margin-top:12px;min-height:0}"
                + ".note.ok{color:#16A34A}.note.err{color:#DE350B}"
                + ".secondary{font-size:14px;line-height:1.5;color:#6B7280;margin-top:16px}"
                + ".secondary a{color:#1A3A6B;font-weight:600;text-decoration:none}"
                + ".secondary a:hover{text-decoration:underline}"
                + ".sr{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}"
                + "</style></head><body><main class=\"card\">"
                + (logoDataUri.isEmpty() ? "" : "<img class=\"logo\" src=\"" + logoDataUri + "\" alt=\"Imototo\">")
                + icon.svg()
                + "<h1>" + heading + "</h1>"
                + "<p class=\"msg\">" + message + "</p>"
                + actions
                + "</main>"
                + (script.isEmpty() ? "" : "<script>" + script + "</script>")
                + "</body></html>";
    }

    private static String primaryLink(String label, String href) {
        return "<a class=\"btn\" href=\"" + attr(href) + "\">" + label + "</a>";
    }

    private static String secondary(String html) {
        return "<p class=\"secondary\">" + html + "</p>";
    }

    private static String attr(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    /** Tokens are generated alphanumerics; anything else is dropped rather than escaped into a script. */
    private static String jsToken(String token) {
        return token == null ? "" : token.replaceAll("[^A-Za-z0-9_-]", "");
    }

    /** The app icon, inlined so the page needs no static hosting or security rule. */
    private static String loadLogo() {
        try (InputStream in = new ClassPathResource("brand/logo.jpg").getInputStream()) {
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (IOException e) {
            log.warn("Brand logo missing from classpath; verification pages render without it");
            return "";
        }
    }

    private String buildResetPasswordHtml(String token) {
        // Token is a server-generated secure token (alphanumeric); embed as a JS string.
        String safeToken = token == null ? "" : token.replace("\\", "").replace("\"", "").replace("<", "").replace(">", "");
        return "<!DOCTYPE html>" +
                "<html lang=\"en\">" +
                "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<title>Reset Password - Imototo Clean</title>" +
                "<style>" +
                "* { margin: 0; padding: 0; box-sizing: border-box; }" +
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #fa709a 0%, #fee140 100%); min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px; }" +
                ".container { background: white; border-radius: 20px; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25); padding: 48px 40px; max-width: 440px; width: 100%; }" +
                "h1 { color: #1F2937; font-size: 26px; font-weight: 700; margin-bottom: 8px; text-align: center; }" +
                "p.sub { color: #6B7280; font-size: 15px; line-height: 1.5; margin-bottom: 24px; text-align: center; }" +
                "label { display: block; font-size: 14px; color: #374151; font-weight: 600; margin-bottom: 6px; }" +
                "input { width: 100%; padding: 12px 14px; border: 1px solid #D1D5DB; border-radius: 10px; font-size: 15px; margin-bottom: 16px; }" +
                "input:focus { outline: none; border-color: #fa709a; box-shadow: 0 0 0 3px rgba(250,112,154,0.2); }" +
                "button { width: 100%; background: linear-gradient(135deg, #fa709a 0%, #fee140 100%); color: white; padding: 14px; border: none; border-radius: 12px; font-weight: 600; font-size: 16px; cursor: pointer; }" +
                "button:disabled { opacity: 0.6; cursor: not-allowed; }" +
                "#msg { margin-top: 14px; font-size: 14px; text-align: center; min-height: 18px; }" +
                ".ok { color: #047857; } .err { color: #B91C1C; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class=\"container\" id=\"card\">" +
                "<h1>Reset your password</h1>" +
                "<p class=\"sub\">Enter a new password for your Imototo account.</p>" +
                "<form id=\"f\">" +
                "<label for=\"pw\">New password</label>" +
                "<input type=\"password\" id=\"pw\" autocomplete=\"new-password\" minlength=\"6\" required>" +
                "<label for=\"cp\">Confirm password</label>" +
                "<input type=\"password\" id=\"cp\" autocomplete=\"new-password\" minlength=\"6\" required>" +
                "<button type=\"submit\" id=\"btn\">Reset password</button>" +
                "<div id=\"msg\"></div>" +
                "</form>" +
                "</div>" +
                "<script>" +
                "var token = \"" + safeToken + "\";" +
                "var f = document.getElementById('f');" +
                "var msg = document.getElementById('msg');" +
                "f.addEventListener('submit', function(e){" +
                "  e.preventDefault();" +
                "  var pw = document.getElementById('pw').value;" +
                "  var cp = document.getElementById('cp').value;" +
                "  msg.className=''; msg.textContent='';" +
                "  if (pw.length < 6) { msg.className='err'; msg.textContent='Password must be at least 6 characters.'; return; }" +
                "  if (pw !== cp) { msg.className='err'; msg.textContent='Passwords do not match.'; return; }" +
                "  var btn = document.getElementById('btn'); btn.disabled = true;" +
                "  fetch('/api/auth/reset-password', { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({ token: token, newPassword: pw }) })" +
                "    .then(function(res){ return res.text().then(function(t){ return { ok: res.ok, text: t }; }); })" +
                "    .then(function(r){" +
                "      if (r.ok) { document.getElementById('card').innerHTML = '<h1>Password reset</h1><p class=\\\"sub\\\">Your password has been updated. You can now log in.</p>'; }" +
                "      else { btn.disabled=false; msg.className='err'; msg.textContent = r.text || 'Reset failed. The link may have expired.'; }" +
                "    })" +
                "    .catch(function(){ btn.disabled=false; msg.className='err'; msg.textContent='Something went wrong. Please try again.'; });" +
                "});" +
                "</script>" +
                "</body>" +
                "</html>";
    }
}
