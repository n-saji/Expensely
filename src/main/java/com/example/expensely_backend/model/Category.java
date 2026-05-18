package com.example.expensely_backend.model;

import com.example.expensely_backend.globals.globals;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Getter @Setter
    private UUID id;

    @ManyToOne
    @Setter @Getter
    private User user;

    @Column(nullable = false)
    @Getter @Setter
    private String name;

    @Column(nullable = false)
    @Getter @Setter
    private String type; // "expense" or "income"

    @Column(name = "icon", nullable = true, columnDefinition = "varchar(255) default 'DollarSign'")
    @Setter
    private String icon;

    @Column(name = "color", nullable = true, columnDefinition = "varchar(20) default '#808080'")
    @Setter
    private String color;

    @PrePersist
    private void applyDefaults() {
        if (this.icon == null || this.icon.isBlank()) {
            this.icon = globals.DEFAULT_CATEGORY_ICON;
        }
        if (this.color == null || this.color.isBlank()) {
            this.color = globals.DEFAULT_CATEGORY_COLOR;
        }
    }

    public String getIcon() {
        if (this.icon == null || this.icon.isBlank()) {
            return globals.DEFAULT_CATEGORY_ICON;
        }
        return this.icon;
    }

    public String getColor() {
        if (this.color == null || this.color.isBlank()) {
            return globals.DEFAULT_CATEGORY_COLOR;
        }
        return this.color;
    }
}
