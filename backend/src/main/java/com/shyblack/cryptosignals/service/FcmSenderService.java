package com.shyblack.cryptosignals.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FcmSenderService {

    private static final Logger log = LoggerFactory.getLogger(FcmSenderService.class);
    private static final Gson gson = new Gson();

    // Attempts to obtain an OAuth2 access token from either env FIREBASE_ACCESS_TOKEN
    // or by exchanging a JWT signed with service-account JSON in FIREBASE_SERVICE_ACCOUNT_JSON.
    private String getAccessToken() {
        String token = System.getenv("FIREBASE_ACCESS_TOKEN");
        if (token != null && !token.isBlank()) return token;

        String saJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (saJson == null || saJson.isBlank()) {
            log.warn("No FIREBASE_ACCESS_TOKEN or FIREBASE_SERVICE_ACCOUNT_JSON available; cannot send FCM");
            return null;
        }

        try {
            Map<?, ?> sa = gson.fromJson(saJson, Map.class);
            String clientEmail = Objects.toString(sa.get("client_email"), null);
            String privateKeyPem = Objects.toString(sa.get("private_key"), null);
            String tokenUri = Objects.toString(sa.get("token_uri"), "https://oauth2.googleapis.com/token");

            long now = Instant.now().getEpochSecond();
            long exp = now + 3600;

            String header = gson.toJson(Map.of("alg", "RS256", "typ", "JWT"));
            String scope = "https://www.googleapis.com/auth/firebase.messaging";
            String claim = gson.toJson(Map.of(
                    "iss", clientEmail,
                    "scope", scope,
                    "aud", tokenUri,
                    "exp", exp,
                    "iat", now
            ));

            String unsigned = base64UrlEncode(header.getBytes()) + "." + base64UrlEncode(claim.getBytes());
            PrivateKey pk = loadPrivateKey(privateKeyPem);
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(pk);
            sig.update(unsigned.getBytes());
            byte[] signatureBytes = sig.sign();
            String signedJwt = unsigned + "." + base64UrlEncode(signatureBytes);

            // Exchange JWT for access token
            URL url = new URL(tokenUri);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            String body = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + signedJwt;
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
            }
            int rc = conn.getResponseCode();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    rc >= 400 ? conn.getErrorStream() : conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                if (rc >= 400) {
                    log.error("Error obtaining access token: {}", sb.toString());
                    return null;
                }
                Map<?, ?> resp = gson.fromJson(sb.toString(), Map.class);
                return Objects.toString(resp.get("access_token"), null);
            }

        } catch (Exception ex) {
            log.error("Failed to build access token from service account: {}", ex.getMessage(), ex);
            return null;
        }
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static PrivateKey loadPrivateKey(String pem) throws Exception {
        String cleaned = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    public boolean sendToToken(String token, String title, String body, JsonObject data) {
        try {
            String accessToken = getAccessToken();
            if (accessToken == null) return false;

            // Determine project id
            String saJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
            String projectId = null;
            if (saJson != null && !saJson.isBlank()) {
                Map<?, ?> sa = gson.fromJson(saJson, Map.class);
                projectId = Objects.toString(sa.get("project_id"), null);
            }
            if (projectId == null) projectId = System.getenv("FIREBASE_PROJECT_ID");
            if (projectId == null) {
                log.error("Firebase project id not configured");
                return false;
            }

            URL url = new URL("https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            JsonObject msg = new JsonObject();
            JsonObject message = new JsonObject();
            message.addProperty("token", token);
            JsonObject notif = new JsonObject();
            notif.addProperty("title", title);
            notif.addProperty("body", body);
            message.add("notification", notif);
            if (data != null) message.add("data", data);
            msg.add("message", message);

            String out = gson.toJson(msg);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(out.getBytes());
            }

            int rc = conn.getResponseCode();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    rc >= 400 ? conn.getErrorStream() : conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                if (rc >= 400) {
                    log.warn("FCM send failed rc={} resp={}", rc, sb.toString());
                    return false;
                }
                log.debug("FCM send response: {}", sb.toString());
                return true;
            }

        } catch (Exception ex) {
            log.error("FCM send error: {}", ex.getMessage(), ex);
            return false;
        }
    }
}
