package com.swiftdrop.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.notification.service.EmailService;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A user viewing and editing their own profile (GET/PATCH /api/v1/users/me). */
class UserProfileIT extends IntegrationTestBase {

    private static final String ME = "/api/v1/users/me";
    private static final String PASSWORD = "parcel2026";

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private JsonMapper json;

    private record Customer(String email, String token) {
    }

    private Customer newCustomer() throws Exception {
        String email = "profile-" + UUID.randomUUID() + "@example.com";
        assertThat(post("/api/v1/auth/register/customer", """
                {"fullName":"Kasun Perera","email":"%s","phone":"+94712345678","password":"%s"}"""
                .formatted(email, PASSWORD)).statusCode()).isEqualTo(201);
        verifyEmail(email);
        HttpResponse<String> login = post("/api/v1/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD));
        assertThat(login.statusCode()).isEqualTo(200);
        return new Customer(email, json.readTree(login.body()).get("token").asString());
    }

    private HttpResponse<String> update(String token, String body) throws Exception {
        return patch(ME, body, token);
    }

    @Test
    void aCustomerUpdatesTheirNameAndPhone() throws Exception {
        Customer customer = newCustomer();

        HttpResponse<String> updated = update(customer.token(), """
                {"fullName":"Kasun Silva","phone":"+94771234567"}""");

        assertThat(updated.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(updated.body());
        assertThat(body.get("fullName").asString()).isEqualTo("Kasun Silva");
        assertThat(body.get("phone").asString()).isEqualTo("+94771234567");
        assertThat(body.get("email").asString()).isEqualTo(customer.email()); // unchanged

        JsonNode me = json.readTree(get(ME, customer.token()).body());
        assertThat(me.get("fullName").asString()).isEqualTo("Kasun Silva");
        assertThat(me.get("phone").asString()).isEqualTo("+94771234567");
    }

    @Test
    void invalidDetailsAreRejectedPerField() throws Exception {
        Customer customer = newCustomer();

        HttpResponse<String> response = update(customer.token(), """
                {"fullName":"K4sun","phone":"0771234567"}""");

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode errors = json.readTree(response.body()).get("fieldErrors");
        assertThat(errors.toString()).contains("fullName").contains("phone");
        assertThat(update(customer.token(), "{}").statusCode()).isEqualTo(400);
        // Nothing changed.
        assertThat(json.readTree(get(ME, customer.token()).body()).get("fullName").asString())
                .isEqualTo("Kasun Perera");
    }

    @Test
    void emailAndRoleInTheBodyAreIgnored() throws Exception {
        Customer customer = newCustomer();

        HttpResponse<String> response = update(customer.token(), """
                {"fullName":"Kasun Perera","phone":"+94712345678","email":"x@example.com","role":"DISPATCHER_ADMIN"}""");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("email").asString()).isEqualTo(customer.email());
        assertThat(body.get("role").asString()).isEqualTo("CUSTOMER");
    }

    @Test
    void signingInIsRequired() throws Exception {
        assertThat(update(null, """
                {"fullName":"Kasun Perera","phone":"+94712345678"}""").statusCode()).isEqualTo(401);
    }
}
