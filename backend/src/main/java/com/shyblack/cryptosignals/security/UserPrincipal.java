package com.shyblack.cryptosignals.security;

import com.shyblack.cryptosignals.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class UserPrincipal implements UserDetails {

	private final UUID id;
	private final String email;
	private final String password;
	private final boolean enabled;
	private final Collection<? extends GrantedAuthority> authorities;

	public UserPrincipal(User user) {
		this.id = user.getId();
		this.email = user.getEmail();
		this.password = user.getPasswordHash() != null ? user.getPasswordHash() : "";
		this.enabled = user.isEnabled();
		this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}
}
