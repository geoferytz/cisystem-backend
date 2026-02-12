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
		String sqlDrop = "ALTER TABLE user_permissions DROP CONSTRAINT IF EXISTS user_permissions_module_check";
		String sqlAdd = "ALTER TABLE user_permissions ADD CONSTRAINT user_permissions_module_check CHECK (module IN (" +
				"'PRODUCTS','CATEGORIES','INVENTORY','STOCK_MOVEMENTS','SALES','MY_SALES','PURCHASING','EXPENSES','EXPENSE_CATEGORIES','REPORTS','USERS_ROLES'" +
				"))";

		try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
			s.execute(sqlDrop);
			s.execute(sqlAdd);
		}
	}
}
