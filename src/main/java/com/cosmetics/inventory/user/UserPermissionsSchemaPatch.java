package com.cosmetics.inventory.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class UserPermissionsSchemaPatch implements ApplicationRunner {
	private final DataSource dataSource;

	public UserPermissionsSchemaPatch(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		// Built from the enum so new modules are always accepted.
		String allowed = java.util.Arrays.stream(PermissionModule.values())
				.map(m -> "'" + m.name() + "'")
				.collect(java.util.stream.Collectors.joining(","));

		try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
			// role names are free-form now (dynamic roles) — drop the old enum check.
			s.execute("ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_name_check");
			for (String table : new String[]{"user_permissions", "role_permissions"}) {
				s.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + table + "_module_check");
				s.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + table + "_module_check CHECK (module IN (" + allowed + "))");
			}
		}
	}
}
