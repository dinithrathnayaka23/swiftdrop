package com.swiftdrop.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.notification.service.EmailService;
import java.net.URLDecoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** End-to-end authentication against a real PostgreSQL schema (Flyway V1). Emails are captured. */
class AuthFlowIT extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@test.swiftdrop.local"; // application-test.yml
    private static final String ADMIN_PASSWORD = "test-admin-password-1";

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void customerRegistersSignsInAndReadsProfile() throws Exception {
        String email = uniqueEmail("customer");

        HttpResponse<String> registered = post("/api/v1/auth/register/customer", """
                {"fullName":"Nimal Perera","email":"%s","phone":"+94712345678","password":"parcel2026"}
                """.formatted(email.toUpperCase()));
        assertThat(registered.statusCode()).isEqualTo(201);
        // No session yet: the email address must be confirmed first.
        assertThat(json.readTree(registered.body()).get("email").asString()).isEqualTo(email);
        assertThat(registered.body()).doesNotContain("token");
        assertThat(sessionSetCookie(registered)).isEmpty();

        // Stored lower-case, with a BCrypt hash (never the raw password).
        var row = jdbcTemplate.queryForMap("SELECT email, password_hash, role, account_status FROM users WHERE email = ?",
                email);
        assertThat(row.get("password_hash").toString()).startsWith("$2").doesNotContain("parcel2026");
        assertThat(row.get("account_status")).isEqualTo("ACTIVE");

        String loginBody = """
                {"email":"%s","password":"parcel2026"}""".formatted(email);
        HttpResponse<String> unconfirmed = post("/api/v1/auth/login", loginBody);
        assertThat(unconfirmed.statusCode()).isEqualTo(403);
        assertThat(json.readTree(unconfirmed.body()).get("code").asString()).isEqualTo("EMAIL_NOT_VERIFIED");

        // Open the emailed confirmation link.
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq(email), eq("Confirm your email for SwiftDrop"), text.capture(), anyString());
        Matcher link = Pattern.compile("/verify-email\\?token=(\\S+)").matcher(text.getValue());
        assertThat(link.find()).isTrue();
        String confirm = """
                {"token":"%s"}""".formatted(URLDecoder.decode(link.group(1), StandardCharsets.UTF_8));
        HttpResponse<String> confirmed = post("/api/v1/auth/verify-email/confirm", confirm);
        assertThat(confirmed.statusCode()).isEqualTo(200);
        assertThat(json.readTree(confirmed.body()).get("awaitingApproval").asBoolean()).isFalse();
        assertThat(post("/api/v1/auth/verify-email/confirm", confirm).statusCode()).isEqualTo(200); // twice is fine
        assertThat(post("/api/v1/auth/verify-email/confirm", """
                {"token":"forged.token.value"}""").statusCode()).isEqualTo(400);

        HttpResponse<String> login = post("/api/v1/auth/login", loginBody);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(json.readTree(login.body()).get("user").get("displayName").asString()).isEqualTo("Nimal Perera");
        String token = json.readTree(login.body()).get("token").asString();

        HttpResponse<String> me = get("/api/v1/users/me", token);
        assertThat(me.statusCode()).isEqualTo(200);
        JsonNode profile = json.readTree(me.body());
        assertThat(profile.get("email").asString()).isEqualTo(email);
        assertThat(profile.get("phone").asString()).isEqualTo("+94712345678");
        assertThat(me.body()).doesNotContain("password");
    }

    @Test
    void duplicateEmailIsReportedOnTheEmailField() throws Exception {
        String email = uniqueEmail("dup");
        String body = """
                {"fullName":"Jane Doe","email":"%s","phone":"+94712345678","password":"parcel2026"}""".formatted(email);
        assertThat(post("/api/v1/auth/register/customer", body).statusCode()).isEqualTo(201);

        HttpResponse<String> again = post("/api/v1/auth/register/customer", body);
        assertThat(again.statusCode()).isEqualTo(409);
        JsonNode error = json.readTree(again.body());
        assertThat(error.get("code").asString()).isEqualTo("CONFLICT");
        assertThat(error.get("fieldErrors").get(0).get("field").asString()).isEqualTo("email");
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/register/customer", """
                {"fullName":"J","email":"not-an-email","phone":"0712345678","password":"short"}""");
        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode error = json.readTree(response.body());
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(error.get("fieldErrors").findValuesAsString("field"))
                .contains("fullName", "email", "phone", "password");
    }

    @Test
    void merchantRegistrationNumberMustBeSriLankanCompanyFormat() throws Exception {
        for (String invalid : new String[] {"PV0023456", "PV002345678", "FC00234567", "PX00234567", "00234567",
                "PV#00234567", "PV 0023 4567"}) {
            HttpResponse<String> response = post("/api/v1/auth/register/merchant", """
                    {"businessName":"BrightMart","registrationNo":"%s","contactPersonName":"Sunil Fernando",
                     "email":"%s","phone":"+94771234567","businessAddress":"12 Galle Road, Colombo 03",
                     "password":"merchant2026"}""".formatted(invalid, uniqueEmail("regno")));
            assertThat(response.statusCode()).as(invalid).isEqualTo(400);
            JsonNode error = json.readTree(response.body()).get("fieldErrors").get(0);
            assertThat(error.get("field").asString()).isEqualTo("registrationNo");
            assertThat(error.get("message").asString()).contains("PV, PB or GA followed by 8 digits");
        }
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameError() throws Exception {
        String email = uniqueEmail("login");
        post("/api/v1/auth/register/customer", """
                {"fullName":"Kamal Silva","email":"%s","phone":"+94712345678","password":"parcel2026"}"""
                .formatted(email));
        verifyEmail(email);

        HttpResponse<String> wrongPassword = post("/api/v1/auth/login", """
                {"email":"%s","password":"wrong-password1"}""".formatted(email));
        HttpResponse<String> unknownEmail = post("/api/v1/auth/login", """
                {"email":"%s","password":"wrong-password1"}""".formatted(uniqueEmail("nobody")));

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownEmail.statusCode()).isEqualTo(401);
        assertThat(json.readTree(wrongPassword.body()).get("message").asString())
                .isEqualTo(json.readTree(unknownEmail.body()).get("message").asString())
                .isEqualTo("Invalid email or password");
    }

    @Test
    void merchantCanSignInOnlyAfterAdminApproval() throws Exception {
        String email = uniqueEmail("merchant");
        String digits = "%08d".formatted(ThreadLocalRandom.current().nextInt(100_000_000));

        // Lower case with a space is accepted and stored in the standard form.
        HttpResponse<String> registered = post("/api/v1/auth/register/merchant", """
                {"businessName":"BrightMart","registrationNo":"pv %s","contactPersonName":"Sunil Fernando",
                 "email":"%s","phone":"+94771234567","businessAddress":"12 Galle Road, Colombo 03",
                 "password":"merchant2026"}""".formatted(digits, email));
        assertThat(registered.statusCode()).isEqualTo(201);
        JsonNode submission = json.readTree(registered.body());
        assertThat(submission.get("approvalStatus").asString()).isEqualTo("PENDING");
        assertThat(submission.get("registrationNo").asString()).isEqualTo("PV" + digits);
        assertThat(submission.has("token")).isFalse();
        String merchantId = submission.get("merchantId").asString();

        // Same number written differently is still a duplicate -> field error on registrationNo.
        HttpResponse<String> duplicate = post("/api/v1/auth/register/merchant", """
                {"businessName":"Other","registrationNo":"PV-%s","contactPersonName":"Other Person",
                 "email":"%s","phone":"+94771234567","businessAddress":"34 Kandy Road, Kandy",
                 "password":"merchant2026"}""".formatted(digits, uniqueEmail("other")));
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(json.readTree(duplicate.body()).get("fieldErrors").get(0).get("field").asString())
                .isEqualTo("registrationNo");

        String merchantLogin = """
                {"email":"%s","password":"merchant2026"}""".formatted(email);
        HttpResponse<String> unconfirmed = post("/api/v1/auth/login", merchantLogin);
        assertThat(json.readTree(unconfirmed.body()).get("code").asString()).isEqualTo("EMAIL_NOT_VERIFIED");
        verify(emailService).send(eq(email), eq("Confirm your email for SwiftDrop"), anyString(), anyString());
        verifyEmail(email);

        HttpResponse<String> pending = post("/api/v1/auth/login", merchantLogin);
        assertThat(pending.statusCode()).isEqualTo(403);
        assertThat(json.readTree(pending.body()).get("code").asString()).isEqualTo("ACCOUNT_PENDING_APPROVAL");

        // A customer can't approve merchants.
        String curious = uniqueEmail("curious");
        post("/api/v1/auth/register/customer", """
                {"fullName":"Curious Customer","email":"%s","phone":"+94712345678","password":"parcel2026"}"""
                .formatted(curious));
        verifyEmail(curious);
        String customerToken = json.readTree(post("/api/v1/auth/login", """
                {"email":"%s","password":"parcel2026"}""".formatted(curious)).body()).get("token").asString();
        assertThat(post("/api/v1/admin/merchants/" + merchantId + "/approve", "{}", customerToken).statusCode())
                .isEqualTo(403);

        // The bootstrap admin approves the merchant.
        // Not on the main sign-in page: the admin has their own.
        assertThat(post("/api/v1/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(ADMIN_EMAIL, ADMIN_PASSWORD)).statusCode())
                .isEqualTo(403);
        String adminToken = json.readTree(post("/api/v1/auth/login", """
                {"email":"%s","password":"%s","portal":"ADMIN"}""".formatted(ADMIN_EMAIL, ADMIN_PASSWORD)).body())
                .get("token").asString();
        assertThat(get("/api/v1/admin/merchants", adminToken).body()).contains(merchantId);
        HttpResponse<String> approved = post("/api/v1/admin/merchants/" + merchantId + "/approve", "{}", adminToken);
        assertThat(approved.statusCode()).isEqualTo(200);
        assertThat(json.readTree(approved.body()).get("approvalStatus").asString()).isEqualTo("APPROVED");
        verify(emailService).send(eq(email), eq("Your SwiftDrop merchant account is approved"), anyString());

        HttpResponse<String> active = post("/api/v1/auth/login", merchantLogin);
        assertThat(active.statusCode()).isEqualTo(200);
        assertThat(json.readTree(active.body()).get("user").get("role").asString()).isEqualTo("MERCHANT");
    }

    @Test
    void passwordResetLinkWorksOnce() throws Exception {
        String email = uniqueEmail("reset");
        post("/api/v1/auth/register/customer", """
                {"fullName":"Forgetful User","email":"%s","phone":"+94712345678","password":"oldpass2026"}"""
                .formatted(email));
        verifyEmail(email);

        // Unknown emails get the same 202 and no email.
        assertThat(post("/api/v1/auth/password-reset", """
                {"email":"%s"}""".formatted(uniqueEmail("ghost"))).statusCode()).isEqualTo(202);

        assertThat(post("/api/v1/auth/password-reset", """
                {"email":"%s"}""".formatted(email)).statusCode()).isEqualTo(202);
        // Asking again straight away still answers 202, but no second email is sent (60 s cooldown).
        assertThat(post("/api/v1/auth/password-reset", """
                {"email":"%s"}""".formatted(email)).statusCode()).isEqualTo(202);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).send(eq(email), eq("Reset your SwiftDrop password"), body.capture(),
                html.capture());
        Matcher link = Pattern.compile("/reset-password\\?token=(\\S+)").matcher(body.getValue());
        assertThat(link.find()).isTrue();
        String token = URLDecoder.decode(link.group(1), StandardCharsets.UTF_8);
        assertThat(html.getValue()).contains("Reset password").contains("Forgetful User");

        // The reset token is not an access token.
        assertThat(get("/api/v1/users/me", token).statusCode()).isEqualTo(401);

        String confirm = """
                {"token":"%s","newPassword":"newpass2026"}""".formatted(token);
        assertThat(post("/api/v1/auth/password-reset/confirm", confirm).statusCode()).isEqualTo(204);
        assertThat(post("/api/v1/auth/password-reset/confirm", confirm).statusCode()).isEqualTo(400); // single use
        verify(emailService).send(eq(email), eq("Your SwiftDrop password was changed"), anyString(), anyString());

        assertThat(post("/api/v1/auth/login", """
                {"email":"%s","password":"oldpass2026"}""".formatted(email)).statusCode()).isEqualTo(401);
        assertThat(post("/api/v1/auth/login", """
                {"email":"%s","password":"newpass2026"}""".formatted(email)).statusCode()).isEqualTo(200);
    }

    @Test
    void rememberMeControlsHowLongTheSessionCookieLasts() throws Exception {
        String email = uniqueEmail("remember");
        post("/api/v1/auth/register/customer", """
                {"fullName":"Remembered User","email":"%s","phone":"+94712345678","password":"parcel2026"}"""
                .formatted(email));
        verifyEmail(email);

        HttpResponse<String> sessionOnly = post("/api/v1/auth/login", """
                {"email":"%s","password":"parcel2026"}""".formatted(email));
        assertThat(json.readTree(sessionOnly.body()).get("rememberMe").asBoolean()).isFalse();
        assertThat(sessionSetCookie(sessionOnly)).hasValueSatisfying(cookie -> assertThat(cookie)
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth")
                .doesNotContain("Max-Age")); // browser session cookie

        HttpResponse<String> remembered = post("/api/v1/auth/login", """
                {"email":"%s","password":"parcel2026","rememberMe":true}""".formatted(email));
        JsonNode body = json.readTree(remembered.body());
        assertThat(body.get("rememberMe").asBoolean()).isTrue();
        assertThat(body.get("expiresIn").asLong()).isEqualTo(600); // access token stays short
        assertThat(body.has("refreshToken")).isFalse();             // only in the httpOnly cookie
        assertThat(sessionSetCookie(remembered)).hasValueSatisfying(cookie ->
                assertThat(cookie).contains("Max-Age=2592000"));   // 30 days
        String refreshToken = sessionCookie(remembered);

        // Refreshing gives a new working access token and a new cookie, and keeps "remember me".
        HttpResponse<String> refreshed = postWithSession("/api/v1/auth/refresh", refreshToken);
        assertThat(refreshed.statusCode()).isEqualTo(200);
        JsonNode next = json.readTree(refreshed.body());
        assertThat(next.get("rememberMe").asBoolean()).isTrue();
        assertThat(next.get("user").get("displayName").asString()).isEqualTo("Remembered User");
        assertThat(sessionCookie(refreshed)).isNotEqualTo(refreshToken);
        assertThat(get("/api/v1/users/me", next.get("token").asString()).statusCode()).isEqualTo(200);

        // Tokens can't be swapped: a refresh token is not a bearer token, an access token can't refresh.
        assertThat(get("/api/v1/users/me", refreshToken).statusCode()).isEqualTo(401);
        assertThat(postWithSession("/api/v1/auth/refresh", body.get("token").asString()).statusCode())
                .isEqualTo(401);
        HttpResponse<String> forged = postWithSession("/api/v1/auth/refresh", "forged.token.value");
        assertThat(forged.statusCode()).isEqualTo(401);
        assertThat(sessionSetCookie(forged)).hasValueSatisfying(cookie -> assertThat(cookie).contains("Max-Age=0"));
        assertThat(postWithSession("/api/v1/auth/refresh", null).statusCode()).isEqualTo(401);
    }

    @Test
    void signingOutEndsTheSessionOnTheServer() throws Exception {
        String email = uniqueEmail("logout");
        post("/api/v1/auth/register/customer", """
                {"fullName":"Leaving User","email":"%s","phone":"+94712345678","password":"parcel2026"}"""
                .formatted(email));
        verifyEmail(email);
        String laptop = sessionCookie(post("/api/v1/auth/login", """
                {"email":"%s","password":"parcel2026","rememberMe":true}""".formatted(email)));
        String phone = sessionCookie(post("/api/v1/auth/login", """
                {"email":"%s","password":"parcel2026","rememberMe":true}""".formatted(email)));

        HttpResponse<String> logout = postWithSession("/api/v1/auth/logout", laptop);
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(sessionSetCookie(logout)).hasValueSatisfying(cookie -> assertThat(cookie).contains("Max-Age=0"));

        // A copied cookie no longer works anywhere; the other device stays signed in.
        assertThat(postWithSession("/api/v1/auth/refresh", laptop).statusCode()).isEqualTo(401);
        assertThat(postWithSession("/api/v1/auth/refresh", phone).statusCode()).isEqualTo(200);
        // Signing out twice (or without a cookie) is harmless.
        assertThat(postWithSession("/api/v1/auth/logout", laptop).statusCode()).isEqualTo(204);
        assertThat(postWithSession("/api/v1/auth/logout", null).statusCode()).isEqualTo(204);
    }

    @Test
    void oldRefreshTokenStopsWorkingAfterRotationGracePeriod() throws Exception {
        String email = uniqueEmail("rotate");
        post("/api/v1/auth/register/customer", """
                {"fullName":"Rotating User","email":"%s","phone":"+94712345678","password":"parcel2026"}"""
                .formatted(email));
        verifyEmail(email);
        String first = sessionCookie(post("/api/v1/auth/login", """
                {"email":"%s","password":"parcel2026"}""".formatted(email)));

        String second = sessionCookie(postWithSession("/api/v1/auth/refresh", first));
        // Within the grace period (2s in the test profile) a second tab may still use the old token.
        assertThat(postWithSession("/api/v1/auth/refresh", first).statusCode()).isEqualTo(200);

        Thread.sleep(2_500);
        assertThat(postWithSession("/api/v1/auth/refresh", first).statusCode()).isEqualTo(401);
        assertThat(postWithSession("/api/v1/auth/refresh", second).statusCode()).isEqualTo(200);
    }

    @Test
    void resettingThePasswordSignsOutEveryDevice() throws Exception {
        String email = uniqueEmail("signout");
        post("/api/v1/auth/register/customer", """
                {"fullName":"Careful User","email":"%s","phone":"+94712345678","password":"oldpass2026"}"""
                .formatted(email));
        verifyEmail(email);
        String refreshToken = sessionCookie(post("/api/v1/auth/login", """
                {"email":"%s","password":"oldpass2026","rememberMe":true}""".formatted(email)));

        post("/api/v1/auth/password-reset", """
                {"email":"%s"}""".formatted(email));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq(email), eq("Reset your SwiftDrop password"), body.capture(), anyString());
        Matcher link = Pattern.compile("/reset-password\\?token=(\\S+)").matcher(body.getValue());
        assertThat(link.find()).isTrue();
        assertThat(post("/api/v1/auth/password-reset/confirm", """
                {"token":"%s","newPassword":"newpass2026"}"""
                .formatted(URLDecoder.decode(link.group(1), StandardCharsets.UTF_8))).statusCode()).isEqualTo(204);

        HttpResponse<String> refresh = postWithSession("/api/v1/auth/refresh", refreshToken);
        assertThat(refresh.statusCode()).isEqualTo(401);
        assertThat(json.readTree(refresh.body()).get("message").asString()).contains("session has expired");
    }

    @Test
    void confirmationLinksCanBeResentWithoutRevealingWhichEmailsExist() throws Exception {
        String email = uniqueEmail("resend");
        post("/api/v1/auth/register/customer", """
                {"fullName":"Slow Reader","email":"%s","phone":"+94712345678","password":"parcel2026"}"""
                .formatted(email));

        assertThat(post("/api/v1/auth/verify-email/resend", """
                {"email":"%s"}""".formatted(uniqueEmail("ghost"))).statusCode()).isEqualTo(202);
        // Right after sign-up the 60 s cooldown applies: still 202, no second email.
        assertThat(post("/api/v1/auth/verify-email/resend", """
                {"email":"%s"}""".formatted(email)).statusCode()).isEqualTo(202);

        verify(emailService, times(1)).send(eq(email), eq("Confirm your email for SwiftDrop"), anyString(),
                anyString());
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        assertThat(get("/api/v1/users/me").statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/users/me", "not-a-token").statusCode()).isEqualTo(401);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }
}
