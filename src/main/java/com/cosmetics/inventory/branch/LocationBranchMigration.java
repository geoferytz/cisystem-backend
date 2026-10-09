package com.cosmetics.inventory.branch;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Converts the legacy free-text `location` column into canonical branch codes:
 * for every distinct location found, a Branch row is created (if missing) and
 * the rows are rewritten to the normalized code.
 */
@Component
@Order(2)
public class LocationBranchMigration implements ApplicationRunner {
	private static final String[] TABLES = {
			"inventory",
			"sales_order_lines",
			"counter_cart_lines",
			"repurchase_order_lines"
	};

	private final DataSource dataSource;
	private final BranchRepository branchRepository;

	public LocationBranchMigration(DataSource dataSource, BranchRepository branchRepository) {
		this.dataSource = dataSource;
		this.branchRepository = branchRepository;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		Set<String> locations = new LinkedHashSet<>();
		try (Connection c = dataSource.getConnection()) {
			for (String table : TABLES) {
				if (!tableExists(c, table)) {
					continue;
				}
				try (Statement s = c.createStatement();
					 ResultSet rs = s.executeQuery("select distinct location from " + table)) {
					while (rs.next()) {
						locations.add(rs.getString(1));
					}
				}
			}

			for (String raw : locations) {
				String code = normalize(raw);
				String name = raw != null && !raw.isBlank() ? raw.trim() : "Main Branch";
				String finalCode = code;
				branchRepository.findByCodeIgnoreCase(code).orElseGet(() -> {
					BranchEntity b = new BranchEntity();
					b.setCode(finalCode);
					b.setName("MAIN".equals(finalCode) ? "Main Branch" : name);
					b.setMain("MAIN".equals(finalCode));
					return branchRepository.save(b);
				});

				for (String table : TABLES) {
					if (!tableExists(c, table)) {
						continue;
					}
					try (PreparedStatement ps = c.prepareStatement(
							"update " + table + " set location = ? where location = ? or (location is null and ? is null)")) {
						ps.setString(1, code);
						ps.setString(2, raw);
						ps.setString(3, raw);
						ps.executeUpdate();
					}
				}
			}
		}
	}

	private boolean tableExists(Connection c, String table) throws Exception {
		try (PreparedStatement ps = c.prepareStatement(
				"select count(*) from information_schema.columns where table_name = ? and column_name = 'location'")) {
			ps.setString(1, table);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() && rs.getLong(1) > 0;
			}
		}
	}

	static String normalize(String value) {
		if (value == null || value.isBlank()) {
			return "MAIN";
		}
		String code = value.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("_+", "_");
		code = code.replaceAll("^_|_$", "");
		return code.isEmpty() ? "MAIN" : code;
	}
}
