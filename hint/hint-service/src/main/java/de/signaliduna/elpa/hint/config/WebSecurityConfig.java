package de.signaliduna.elpa.hint.config;

import com.c4_soft.springaddons.security.oidc.spring.SpringAddonsMethodSecurityExpressionHandler;
import com.c4_soft.springaddons.security.oidc.spring.SpringAddonsMethodSecurityExpressionRoot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {
	private final String[] authorizedUsers;
	private final String [] authorizedMigrationUsers;

	public WebSecurityConfig(@Value("${authorization.users}") String[] authorizedUsers, @Value("${authorization.migration_users}")String[] authorizedMigrationUsers) {
		this.authorizedUsers = authorizedUsers;
		this.authorizedMigrationUsers = authorizedMigrationUsers;
	}

	@Bean
	public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
		return new SpringAddonsMethodSecurityExpressionHandler(() -> new CustomMethodSecurityExpressionRoot(Arrays.asList(authorizedUsers), Arrays.asList(authorizedMigrationUsers)));
	}

	private static final class CustomMethodSecurityExpressionRoot extends SpringAddonsMethodSecurityExpressionRoot {
		private final List<String> authorizedUsers;
		private final List<String> authorizedMigrationUsers;

		public CustomMethodSecurityExpressionRoot(List<String> authorizedUsers, List<String> authorizedMigrationUsers) {
			this.authorizedUsers = authorizedUsers;
			this.authorizedMigrationUsers = authorizedMigrationUsers;
		}

		public boolean isAuthorizedUser() {
			return this.authorizedUsers.contains(getAuthentication().getName());
		}

		public boolean isAuthorizedMigrationUser() {
			return this.authorizedMigrationUsers.contains(getAuthentication().getName());
		}

	}
}
