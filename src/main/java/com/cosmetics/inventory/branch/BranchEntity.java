package com.cosmetics.inventory.branch;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "branches")
public class BranchEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String code;

	@Column(nullable = false, unique = true, length = 200)
	private String name;

	@Column(length = 500)
	private String address;

	@Column(length = 50)
	private String phone;

	@Column(nullable = false)
	private boolean active = true;

	@Column(nullable = false)
	private boolean main = false;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isMain() {
		return main;
	}

	public void setMain(boolean main) {
		this.main = main;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
