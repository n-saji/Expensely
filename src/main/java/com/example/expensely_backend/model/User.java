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

	@Column()
	private String password;

	@Getter
	private String name;

	@Getter
	@Column()
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
	private String theme;

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

	@Column(nullable = true, columnDefinition = "varchar(1000)")
	@Getter
	@Setter
	private String profilePicFilePath;

	@Column(columnDefinition = "boolean default false")
	@Getter
	@Setter
	private boolean isOauth2User;

	@Column(columnDefinition = "boolean default true")
	@Getter
	@Setter
	private boolean isProfileComplete;

	@Column(columnDefinition = "boolean default false")
	@Getter
	@Setter
	private boolean isEmailVerified;
}
