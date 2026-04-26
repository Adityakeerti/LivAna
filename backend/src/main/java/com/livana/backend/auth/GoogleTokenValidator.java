package com.livana.backend.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.livana.backend.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Validates Google ID tokens received from the mobile app after the user
 * taps 'Sign in with Google'. Cryptographic validation using Google's public keys —
 * no direct call to any user-data API.
 *
 * The clientId MUST match the OAuth client ID used by the mobile app exactly,
 * otherwise Google will reject the token (audience mismatch).
 */
@Component
public class GoogleTokenValidator {

    @Value("${app.google.client-id}")
    private String clientId;

    /**
     * Validate the Google ID token and return the verified payload.
     *
     * @param idTokenString raw ID token from the mobile app
     * @return verified GoogleIdToken.Payload containing email, sub (google_id), name, picture
     * @throws UnauthorizedException if token is null, expired, or audience does not match
     */
    public GoogleIdToken.Payload validate(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(clientId))
                    .build();

            GoogleIdToken token = verifier.verify(idTokenString);
            if (token == null) {
                throw new UnauthorizedException("Invalid Google token — verification returned null");
            }
            return token.getPayload();

        } catch (UnauthorizedException e) {
            throw e;   // re-throw domain exceptions as-is
        } catch (Exception e) {
            throw new UnauthorizedException("Google token validation failed: " + e.getMessage());
        }
    }
}
