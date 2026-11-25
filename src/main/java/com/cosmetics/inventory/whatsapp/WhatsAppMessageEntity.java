package com.cosmetics.inventory.whatsapp;

import jakarta.persistence.*;

import java.time.Instant;

/** One row per WhatsApp message we tried to send; status is updated by the webhook. */
@Entity
@Table(name = "whatsapp_messages", indexes = {
		@Index(name = "ix_whatsapp_wamid", columnList = "wamid"),
		@Index(name = "ix_whatsapp_sale", columnList = "salesOrderId,kind"),
		@Index(name = "ix_whatsapp_customer", columnList = "customerId,kind")
})
public class WhatsAppMessageEntity {
	public enum Kind { RECEIPT, DEBT_REMINDER }

	/** Ordered by progress so a late "sent" webhook never overwrites "read". */
	public enum Status { PENDING, SENT, DELIVERED, READ, FAILED, SKIPPED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Kind kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status = Status.PENDING;

	private Long customerId;
	private Long salesOrderId;

	@Column(length = 20)
	private String phone;

	@Column(length = 120)
	private String template;

	@Column(length = 200)
	private String wamid;

	@Column(length = 1000)
	private String error;

	@Column(length = 320)
	private String createdBy;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	private Instant updatedAt;

	public Long getId() { return id; }
	public Kind getKind() { return kind; }
	public void setKind(Kind kind) { this.kind = kind; }
	public Status getStatus() { return status; }
	public Long getCustomerId() { return customerId; }
	public void setCustomerId(Long customerId) { this.customerId = customerId; }
	public Long getSalesOrderId() { return salesOrderId; }
	public void setSalesOrderId(Long salesOrderId) { this.salesOrderId = salesOrderId; }
	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }
	public String getTemplate() { return template; }
	public void setTemplate(String template) { this.template = template; }
	public String getWamid() { return wamid; }
	public void setWamid(String wamid) { this.wamid = wamid; }
	public String getError() { return error; }
	public String getCreatedBy() { return createdBy; }
	public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }

	public void setStatus(Status status) {
		this.status = status;
		this.updatedAt = Instant.now();
	}

	public void setError(String error) {
		this.error = error == null ? null : error.length() > 1000 ? error.substring(0, 1000) : error;
	}

	/** Applies a webhook status without moving backwards (sent -> delivered -> read; failed always wins). */
	public void advanceTo(Status next) {
		if (next == Status.FAILED || next.ordinal() > status.ordinal() || status == Status.PENDING) setStatus(next);
	}

	public boolean succeeded() {
		return status == Status.SENT || status == Status.DELIVERED || status == Status.READ;
	}
}
