
package com.mydrive.drive.account;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="users")
public class AppUser {
    @Id
    @Column(nullable=false, updatable=false)
    private java.util.UUID id;

    @Column(nullable=false, unique=true, length=320)
    private String email;

    @Column(name="password_hash", nullable=false, length=100)
    private String passwordHash;

    @Column(name="created_at", nullable=false, updatable=false)
    private java.time.Instant createdAt;

    protected AppUser() {
        // JPA requires a no-argument constructor
    }

    public AppUser(java.util.UUID id, String email, String passwordHash, java.time.Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public java.util.UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }
}