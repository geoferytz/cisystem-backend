package com.cosmetics.inventory.mysales;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MySalesService {
	private final MySalesRepository mySalesRepository;

	public MySalesService(MySalesRepository mySalesRepository) {
		this.mySalesRepository = mySalesRepository;
	}

	@Transactional(readOnly = true)
	public List<MySaleEntity> findAll() {
		return mySalesRepository.findAllByOrderByCreatedAtDesc();
	}

	@Transactional
	public MySaleEntity create(CreateMySaleCommand cmd, Authentication authentication) {
		validate(cmd);

		MySaleEntity sale = new MySaleEntity();
		sale.setCustomer(trimToNull(cmd.customer()));
		sale.setReferenceNumber(trimToNull(cmd.referenceNumber()));
		sale.setCreatedBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);

		for (CreateMySaleLineCommand line : cmd.lines()) {
			MySaleLineEntity l = new MySaleLineEntity();
			l.setProductName(line.productName().trim());
			l.setQuantity(line.quantity());
			l.setUnitPrice(BigDecimal.valueOf(line.unitPrice()));
			sale.addLine(l);
		}

		return mySalesRepository.save(sale);
	}

	@Transactional
	public MySaleEntity update(UpdateMySaleCommand cmd) {
		validate(cmd);

		MySaleEntity sale = mySalesRepository.findById(cmd.id()).orElseThrow();
		sale.setCustomer(trimToNull(cmd.customer()));
		sale.setReferenceNumber(trimToNull(cmd.referenceNumber()));

		sale.clearLines();
		for (CreateMySaleLineCommand line : cmd.lines()) {
			MySaleLineEntity l = new MySaleLineEntity();
			l.setProductName(line.productName().trim());
			l.setQuantity(line.quantity());
			l.setUnitPrice(BigDecimal.valueOf(line.unitPrice()));
			sale.addLine(l);
		}

		return mySalesRepository.save(sale);
	}

	@Transactional
	public boolean delete(long id) {
		if (!mySalesRepository.existsById(id)) {
			return false;
		}
		mySalesRepository.deleteById(id);
		return true;
	}

	private void validate(CreateMySaleCommand cmd) {
		if (cmd == null || cmd.lines() == null || cmd.lines().isEmpty()) {
			throw new IllegalArgumentException("At least one line is required");
		}
		for (CreateMySaleLineCommand line : cmd.lines()) {
			validateLine(line);
		}
	}

	private void validate(UpdateMySaleCommand cmd) {
		if (cmd == null) {
			throw new IllegalArgumentException("Input is required");
		}
		if (cmd.id() <= 0) {
			throw new IllegalArgumentException("Id is required");
		}
		if (cmd.lines() == null || cmd.lines().isEmpty()) {
			throw new IllegalArgumentException("At least one line is required");
		}
		for (CreateMySaleLineCommand line : cmd.lines()) {
			validateLine(line);
		}
	}

	private void validateLine(CreateMySaleLineCommand line) {
		if (line == null || line.productName() == null || line.productName().isBlank()) {
			throw new IllegalArgumentException("Product name is required");
		}
		if (line.quantity() <= 0) {
			throw new IllegalArgumentException("Quantity must be > 0");
		}
		if (!Double.isFinite(line.unitPrice()) || line.unitPrice() < 0) {
			throw new IllegalArgumentException("Unit price must be >= 0");
		}
	}

	private String trimToNull(String s) {
		if (s == null) return null;
		String t = s.trim();
		return t.isBlank() ? null : t;
	}

	public record CreateMySaleCommand(String customer, String referenceNumber, List<CreateMySaleLineCommand> lines) {
	}

	public record UpdateMySaleCommand(long id, String customer, String referenceNumber, List<CreateMySaleLineCommand> lines) {
	}

	public record CreateMySaleLineCommand(String productName, int quantity, double unitPrice) {
	}
}
