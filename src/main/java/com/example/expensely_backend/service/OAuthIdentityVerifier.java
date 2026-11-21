package com.example.expensely_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class OAuthIdentityVerifier {
	public record Identity(String provider, String providerUserId, String email, String name) {}
	private final Environment environment;
	private final ObjectMapper mapper;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	public OAuthIdentityVerifier(Environment environment, ObjectMapper mapper) {
		this.environment = environment;
		this.mapper = mapper;
	}

	public Identity verify(String provider, String proof) {
		if (provider == null || proof == null || proof.isBlank()) throw new IllegalArgumentException("Provider proof is required");
		try {
			return switch (provider.toLowerCase().trim()) {
				case "google" -> google(proof);
				case "github" -> github(proof);
				case "discord" -> discord(proof);
				default -> throw new IllegalArgumentException("Unsupported OAuth provider");
			};
		} catch (IllegalArgumentException e) { throw e; }
		catch (Exception e) { throw new IllegalArgumentException("Provider proof could not be verified"); }
	}

	private Identity google(String token) throws Exception {
		String audience = environment.getProperty("GOOGLE_CLIENT_ID");
		if (audience == null || audience.isBlank()) throw new IllegalArgumentException("Google sign-in is unavailable");
		GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance())
				.setAudience(Collections.singletonList(audience)).build();
		GoogleIdToken idToken = verifier.verify(token);
		if (idToken == null || !Boolean.TRUE.equals(idToken.getPayload().getEmailVerified()))
			throw new IllegalArgumentException("Google identity is unverified");
		String name = (String) idToken.getPayload().get("name");
		return new Identity("google", idToken.getPayload().getSubject(), idToken.getPayload().getEmail(), name);
	}

	private Identity github(String token) throws Exception {
		String clientId = environment.getProperty("GITHUB_CLIENT_ID");
		String clientSecret = environment.getProperty("GITHUB_CLIENT_SECRET");
		if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank())
			throw new IllegalArgumentException("GitHub sign-in is unavailable");
		String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
		HttpRequest check = HttpRequest.newBuilder(URI.create("https://api.github.com/applications/" + clientId + "/token"))
				.timeout(Duration.ofSeconds(8)).header("Authorization", "Basic " + basic)
				.header("Accept", "application/vnd.github+json").header("User-Agent", "Expensely-Backend")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("access_token", token)))).build();
		HttpResponse<String> checked = http.send(check, HttpResponse.BodyHandlers.ofString());
		if (checked.statusCode() != 200 || !clientId.equals(mapper.readTree(checked.body()).path("app").path("client_id").asText()))
			throw new IllegalArgumentException("GitHub token was not issued to this application");
		JsonNode user = get("https://api.github.com/user", token);
		JsonNode emails = get("https://api.github.com/user/emails", token);
		String email = null;
		for (JsonNode entry : emails) if (entry.path("primary").asBoolean() && entry.path("verified").asBoolean()) {
			email = entry.path("email").asText(null);
			break;
		}
		if (email == null || user.path("id").isMissingNode()) throw new IllegalArgumentException("GitHub email is unverified");
		return new Identity("github", user.path("id").asText(), email, user.path("name").asText(user.path("login").asText()));
	}

	private Identity discord(String token) throws Exception {
		String clientId = environment.getProperty("DISCORD_CLIENT_ID");
		if (clientId == null || clientId.isBlank()) throw new IllegalArgumentException("Discord sign-in is unavailable");
		JsonNode authorization = get("https://discord.com/api/v10/oauth2/@me", token);
		if (!clientId.equals(authorization.path("application").path("id").asText()))
			throw new IllegalArgumentException("Discord token was not issued to this application");
		JsonNode user = get("https://discord.com/api/v10/users/@me", token);
		if (!user.path("verified").asBoolean() || user.path("email").asText("").isBlank() || user.path("id").asText("").isBlank())
			throw new IllegalArgumentException("Discord email is unverified");
		return new Identity("discord", user.path("id").asText(), user.path("email").asText(), user.path("global_name").asText(user.path("username").asText()));
	}

	private JsonNode get(String url, String token) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
				.header("Authorization", "Bearer " + token).header("Accept", "application/json")
				.header("User-Agent", "Expensely-Backend").GET().build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) throw new IllegalArgumentException("Provider rejected proof");
		return mapper.readTree(response.body());
	}
}
