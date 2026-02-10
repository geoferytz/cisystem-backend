package com.cosmetics.inventory.mysales;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "my_sales")
public class MySaleEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	@Column(length = 320)
	private String createdBy;

	@Column(length = 200)
	private String customer;

	@Column(length = 120)
	private String referenceNumber;

	@OneToMany(mappedBy = "mySale", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<MySaleLineEntity> lines = new ArrayList<>();

	public Long getId() {
		return id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public String getCustomer() {
		return customer;
	}

	public void setCustomer(String customer) {
		this.customer = customer;
	}

	public String getReferenceNumber() {
		return referenceNumber;
	}

	public void setReferenceNumber(String referenceNumber) {
		this.referenceNumber = referenceNumber;
	}

	public List<MySaleLineEntity> getLines() {
		return lines;
	}

	public void addLine(MySaleLineEntity line) {
		lines.add(line);
		line.setMySale(this);
	}

	public void clearLines() {
		lines.clear();
	}
}
