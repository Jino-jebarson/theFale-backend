package com.thefale.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class CustomUserPrincipal implements UserDetails {

    private final UUID id;
    private final String phone;
    private final String email;
    private final String role;
    private final String candidateLevel;
    private final String password;
    private final boolean enabled;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return password != null ? password : "";
    }

    @Override
    public String getUsername() {
        return id != null ? id.toString() : (phone != null ? phone : email);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isStudent() {
        return "STUDENT".equalsIgnoreCase(candidateLevel);
    }

    public boolean isGraduate() {
        return "FRESHER".equalsIgnoreCase(candidateLevel) || "PROFESSIONAL".equalsIgnoreCase(candidateLevel);
    }

    public boolean isFacility() {
        return "ROLE_FACILITY".equals(role);
    }

    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(role);
    }
}
