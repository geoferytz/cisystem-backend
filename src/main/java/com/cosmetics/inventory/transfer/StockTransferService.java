package com.cosmetics.inventory.transfer;

import com.cosmetics.inventory.branch.BranchRepository;
import com.cosmetics.inventory.branch.BranchScope;
import com.cosmetics.inventory.inventory.InventoryItemEntity;
import com.cosmetics.inventory.inventory.InventoryRepository;
import com.cosmetics.inventory.product.ProductBatchEntity;
import com.cosmetics.inventory.product.ProductBatchRepository;
import com.cosmetics.inventory.stockmovement.StockMovementEntity;
import com.cosmetics.inventory.stockmovement.StockMovementRepository;
import com.cosmetics.inventory.stockmovement.StockMovementType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class StockTransferService {
	private final StockTransferRepository transfers;
	private final InventoryRepository inventory;
	private final ProductBatchRepository batches;
	private final StockMovementRepository movements;
	private final BranchRepository branchRepository;
	private final BranchScope branchScope;

	public StockTransferService(StockTransferRepository transfers, InventoryRepository inventory,
			ProductBatchRepository batches, StockMovementRepository movements,
			BranchRepository branchRepository, BranchScope branchScope) {
		this.transfers = transfers;
		this.inventory = inventory;
		this.batches = batches;
		this.movements = movements;
		this.branchRepository = branchRepository;
		this.branchScope = branchScope;
	}

	@Transactional(readOnly = true)
	public List<StockTransferEntity> list(Authentication authentication, String branch, String status, String query) {
		String scoped = branchScope.scopeFilter(authentication, branch);
		StockTransferEntity.Status st = parseStatus(status);
		String q = query == null ? null : query.trim().toLowerCase();
		return transfers.findTop500ByOrderByCreatedAtDesc().stream()
				.filter(t -> scoped == null || scoped.equalsIgnoreCase(t.getFromBranch()) || scoped.equalsIgnoreCase(t.getToBranch()))
				.filter(t -> st == null || t.getStatus() == st)
				.filter(t -> {
					if (q == null || q.isBlank()) return true;
					return (t.getReference() != null && t.getReference().toLowerCase().contains(q))
							|| t.getFromBranch().toLowerCase().contains(q)
							|| t.getToBranch().toLowerCase().contains(q)
							|| t.getLines().stream().anyMatch(l ->
									(l.getProductName() != null && l.getProductName().toLowerCase().contains(q))
											|| (l.getSku() != null && l.getSku().toLowerCase().contains(q))
											|| (l.getBatchNumber() != null && l.getBatchNumber().toLowerCase().contains(q)));
				})
				.toList();
	}

	@Transactional
	public StockTransferEntity create(CreateCommand cmd, Authentication authentication) {
		if (cmd.lines() == null || cmd.lines().isEmpty() || cmd.lines().size() > 100) {
			throw new IllegalArgumentException("Add between 1 and 100 products");
		}
		String from = branchScope.resolveLocation(authentication, cmd.fromBranch());
		String to = branchScope.resolveLocation(authentication, cmd.toBranch());
		if (from.equalsIgnoreCase(to)) {
			throw new IllegalArgumentException("Source and destination branches must differ");
		}

		Set<Long> seen = new HashSet<>();
		var transfer = new StockTransferEntity();
		transfer.setFromBranch(from);
		transfer.setToBranch(to);
		transfer.setNote(cmd.note() != null && !cmd.note().isBlank() ? cmd.note().trim() : null);
		transfer.setCreatedBy(actor(authentication));

		for (var input : cmd.lines()) {
			if (!seen.add(input.batchId())) {
				throw new IllegalArgumentException("Each batch can only appear once");
			}
			if (input.quantity() < 1 || input.quantity() > 1_000_000) {
				throw new IllegalArgumentException("Quantities must be between 1 and 1000000");
			}
			ProductBatchEntity batch = batches.findById(input.batchId()).orElseThrow();
			InventoryItemEntity inv = inventory.findByBatchIdAndLocation(batch.getId(), from).orElse(null);
			if (inv == null || inv.getQtyOnHand() < input.quantity()) {
				throw new IllegalArgumentException("Insufficient stock at " + from + " for batch " + batch.getBatchNumber());
			}
			var line = new StockTransferEntity.Line();
			line.setProductId(batch.getProduct().getId());
			line.setBatchId(batch.getId());
			line.setSku(batch.getProduct().getSku());
			line.setProductName(batch.getProduct().getName());
			line.setBatchNumber(batch.getBatchNumber());
			line.setQuantity(input.quantity());
			transfer.getLines().add(line);
		}

		StockTransferEntity saved = transfers.saveAndFlush(transfer);
		saved.setReference("TRF-" + String.format("%06d", saved.getId()));
		return transfers.save(saved);
	}

	@Transactional
	public StockTransferEntity dispatch(long id, Authentication authentication) {
		var t = lock(id);
		if (t.getStatus() != StockTransferEntity.Status.PENDING) {
			throw new IllegalArgumentException("Only pending transfers can be dispatched");
		}
		branchScope.assertBranch(authentication, t.getFromBranch());

		for (var line : t.getLines()) {
			InventoryItemEntity inv = inventory.findForUpdate(line.getBatchId(), t.getFromBranch()).orElse(null);
			if (inv == null || inv.getQtyOnHand() < line.getQuantity()) {
				throw new IllegalArgumentException("Insufficient stock at " + t.getFromBranch() + " for batch " + line.getBatchNumber());
			}
			inv.setQtyOnHand(inv.getQtyOnHand() - line.getQuantity());
			inventory.save(inv);
			movement(StockMovementType.OUT, inv.getBatch(), line.getQuantity(), authentication,
					"Transfer " + ref(t) + ": " + t.getFromBranch() + " → " + t.getToBranch());
		}

		t.setStatus(StockTransferEntity.Status.DISPATCHED);
		t.setDispatchedAt(Instant.now());
		t.setDispatchedBy(actor(authentication));
		return transfers.save(t);
	}

	@Transactional
	public StockTransferEntity receive(long id, Authentication authentication) {
		var t = lock(id);
		if (t.getStatus() != StockTransferEntity.Status.RECEIVED && t.getStatus() != StockTransferEntity.Status.DISPATCHED) {
			throw new IllegalArgumentException("Only dispatched transfers can be received");
		}
		if (t.getStatus() == StockTransferEntity.Status.RECEIVED) {
			return t;
		}
		branchScope.assertBranch(authentication, t.getToBranch());

		for (var line : t.getLines()) {
			ProductBatchEntity batch = batches.findById(line.getBatchId()).orElseThrow();
			InventoryItemEntity inv = inventory.findForUpdate(batch.getId(), t.getToBranch())
					.orElseGet(() -> {
						InventoryItemEntity i = new InventoryItemEntity();
						i.setBatch(batch);
						i.setLocation(t.getToBranch());
						i.setQtyOnHand(0);
						return i;
					});
			inv.setQtyOnHand(inv.getQtyOnHand() + line.getQuantity());
			inventory.save(inv);
			movement(StockMovementType.IN, batch, line.getQuantity(), authentication,
					"Transfer " + ref(t) + ": " + t.getFromBranch() + " → " + t.getToBranch());
		}

		t.setStatus(StockTransferEntity.Status.RECEIVED);
		t.setReceivedAt(Instant.now());
		t.setReceivedBy(actor(authentication));
		return transfers.save(t);
	}

	@Transactional
	public StockTransferEntity cancel(long id, Authentication authentication) {
		var t = lock(id);
		if (t.getStatus() != StockTransferEntity.Status.PENDING) {
			throw new IllegalArgumentException("Only pending transfers can be cancelled");
		}
		branchScope.assertBranch(authentication, t.getFromBranch());
		t.setStatus(StockTransferEntity.Status.CANCELLED);
		return transfers.save(t);
	}

	private StockTransferEntity lock(long id) {
		return transfers.lockById(id).orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
	}

	private void movement(StockMovementType type, ProductBatchEntity batch, int qty, Authentication authentication, String note) {
		StockMovementEntity mv = new StockMovementEntity();
		mv.setType(type);
		mv.setBatch(batch);
		mv.setQuantity(qty);
		mv.setCreatedBy(actor(authentication));
		mv.setNote(note);
		movements.save(mv);
	}

	private String ref(StockTransferEntity t) {
		return t.getReference() != null ? t.getReference() : ("#" + t.getId());
	}

	private String actor(Authentication authentication) {
		return authentication != null ? String.valueOf(authentication.getPrincipal()) : null;
	}

	private StockTransferEntity.Status parseStatus(String status) {
		if (status == null || status.isBlank()) return null;
		try {
			return StockTransferEntity.Status.valueOf(status.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public record CreateCommand(String fromBranch, String toBranch, String note, List<LineCommand> lines) {}
	public record LineCommand(long batchId, int quantity) {}
}
