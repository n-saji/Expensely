package com.example.expensely_backend.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "users")
public class User {

	@Setter
	@Getter
	@Id
	@Column(columnDefinition = "UUID", updatable = false, nullable = false)
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Getter
	@Column(nullable = false, unique = true)
	private String email;

	@Column
	private String password;

	@Getter
	@Column(nullable = false)
	private String name;

	@Getter
	private String country_code;

	@Getter
	@Column(unique = true)
	private String phone;

	@Column(nullable = false)
	@Getter
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(nullable = false)
	@Getter
	private String currency = "USD"; // Default currency set to USD

	@Column(nullable = false, columnDefinition = "varchar(255) default 'light'")
	@Getter
	@Setter
	private String theme = "light";

	@Column(name = "theme_color", nullable = false, columnDefinition = "varchar(255) default 'teal'")
	@Getter
	@Setter
	@JsonProperty("theme_color")
	@JsonAlias("themeColor")
	private String themeColor;

	@Column(nullable = false, columnDefinition = "varchar(255) default 'en'")
	@Getter
	@Setter
	private String language = "en"; // Default language set to English

	@Column(nullable = false, columnDefinition = "boolean default true")
	@Getter
	@Setter
	private Boolean isActive; // Default active status set to true

	@Column(nullable = false, columnDefinition = "boolean default false")
	@Getter
	@Setter
	private Boolean isAdmin;

	@Column(nullable = false, columnDefinition = "boolean default true")
	@Getter
	@Setter
	private Boolean NotificationsEnabled;

	@Column(nullable = false, columnDefinition = "boolean default true")
	@Getter
	@Setter
	@JsonProperty("alerts_enabled")
	@JsonAlias("alertsEnabled")
	private Boolean alertsEnabled;

	@Column(columnDefinition = "varchar(1000)")
	@Getter
	@Setter
	private String profilePicFilePath;

	@Transient
	@JsonProperty("profilePictureUrl")
	public String getProfilePictureUrl() {
		if (profilePicFilePath == null || profilePicFilePath.isBlank()) {
			return null;
		}
		if (profilePicFilePath.startsWith("http://") || profilePicFilePath.startsWith("https://")) {
			return profilePicFilePath;
		}
		String bucket = System.getenv("AWS_PROFILE_BUCKET_NAME");
		if (bucket == null || bucket.isBlank()) {
			bucket = "expensely-profiles";
		}
		String region = System.getenv("AWS_REGION");
		if (region != null && !region.isBlank()) {
			return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + profilePicFilePath;
		}
		return "https://" + bucket + ".s3.amazonaws.com/" + profilePicFilePath;
	}

	@Column(columnDefinition = "boolean default false")
	@Getter
	@Setter
	@JsonProperty("isOauth2User")
	@JsonAlias("is_oauth2_user")
	private boolean isOauth2User;

	@Column(columnDefinition = "boolean default true")
	@Getter
	@Setter
	private boolean isProfileComplete;

	@Column(columnDefinition = "boolean default false")
	@Getter
	@Setter
	private boolean isEmailVerified;

	@Column(name = "has_transactions", nullable = false, columnDefinition = "boolean default false")
	@Setter
	@JsonProperty("hasTransactions")
	@JsonAlias("has_transactions")
	private boolean hasTransactions = false;

	@JsonProperty("hasTransactions")
	public boolean getHasTransactions() {
		return hasTransactions;
	}
}
