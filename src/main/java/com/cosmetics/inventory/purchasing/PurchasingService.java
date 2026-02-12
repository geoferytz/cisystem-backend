package com.cosmetics.inventory.purchasing;

import com.cosmetics.inventory.inventory.InventoryItemEntity;
import com.cosmetics.inventory.inventory.InventoryRepository;
import com.cosmetics.inventory.product.ProductBatchEntity;
import com.cosmetics.inventory.product.ProductBatchRepository;
import com.cosmetics.inventory.product.ProductEntity;
import com.cosmetics.inventory.product.ProductRepository;
import com.cosmetics.inventory.sales.SalesDeductionRepository;
import com.cosmetics.inventory.stockmovement.StockMovementEntity;
import com.cosmetics.inventory.stockmovement.StockMovementRepository;
import com.cosmetics.inventory.stockmovement.StockMovementType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class PurchasingService {
	private final PurchaseOrderRepository purchaseOrderRepository;
	private final ProductRepository productRepository;
	private final ProductBatchRepository batchRepository;
	private final InventoryRepository inventoryRepository;
	private final StockMovementRepository stockMovementRepository;
	private final SalesDeductionRepository salesDeductionRepository;

	public PurchasingService(
			PurchaseOrderRepository purchaseOrderRepository,
			ProductRepository productRepository,
			ProductBatchRepository batchRepository,
			InventoryRepository inventoryRepository,
			StockMovementRepository stockMovementRepository,
			SalesDeductionRepository salesDeductionRepository
	) {
		this.purchaseOrderRepository = purchaseOrderRepository;
		this.productRepository = productRepository;
		this.batchRepository = batchRepository;
		this.inventoryRepository = inventoryRepository;
		this.stockMovementRepository = stockMovementRepository;
		this.salesDeductionRepository = salesDeductionRepository;
	}

	@Transactional
	public PurchaseOrderEntity receivePurchase(ReceivePurchaseCommand cmd, Authentication authentication) {
		if (cmd.lines() == null || cmd.lines().isEmpty()) {
			throw new IllegalArgumentException("At least one line is required");
		}

		PurchaseOrderEntity po = new PurchaseOrderEntity();
		po.setSupplier(cmd.supplier());
		po.setInvoiceNumber(cmd.invoiceNumber());
		po.setReceivedBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);

		for (ReceivePurchaseLineCommand line : cmd.lines()) {
			ProductEntity product = productRepository.findById(line.productId()).orElseThrow();

			ProductBatchEntity batch = new ProductBatchEntity();
			batch.setProduct(product);
			batch.setBatchNumber(line.batchNumber().trim());
			batch.setExpiryDate(LocalDate.parse(line.expiryDate()));
			batch.setCostPrice(BigDecimal.valueOf(line.costPrice()));
			batch.setQuantityReceived(line.quantityReceived());
			ProductBatchEntity persistedBatch = batchRepository.save(batch);

			InventoryItemEntity inv = inventoryRepository
					.findByBatchIdAndLocation(persistedBatch.getId(), "MAIN")
					.orElseGet(() -> {
						InventoryItemEntity i = new InventoryItemEntity();
						i.setBatch(persistedBatch);
						i.setLocation("MAIN");
						return i;
					});
			inv.setQtyOnHand(inv.getQtyOnHand() + line.quantityReceived());
			inventoryRepository.save(inv);

			StockMovementEntity mv = new StockMovementEntity();
			mv.setType(StockMovementType.IN);
			mv.setBatch(persistedBatch);
			mv.setQuantity(line.quantityReceived());
			mv.setCreatedBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);
			mv.setNote(cmd.invoiceNumber() != null ? ("Purchase invoice: " + cmd.invoiceNumber()) : "Purchase received");
			stockMovementRepository.save(mv);

			PurchaseOrderLineEntity pol = new PurchaseOrderLineEntity();
			pol.setProduct(product);
			pol.setBatch(persistedBatch);
			pol.setCostPrice(BigDecimal.valueOf(line.costPrice()));
			pol.setQuantityReceived(line.quantityReceived());
			po.addLine(pol);
		}

		return purchaseOrderRepository.save(po);
	}

	@Transactional
	public PurchaseOrderEntity updatePurchase(UpdatePurchaseCommand cmd, Authentication authentication) {
		if (cmd == null || cmd.id() <= 0) {
			throw new IllegalArgumentException("Purchase id is required");
		}
		PurchaseOrderEntity po = purchaseOrderRepository.findById(cmd.id()).orElseThrow();
		restoreInventoryForPurchase(po, authentication);

		po.setSupplier(cmd.supplier());
		po.setInvoiceNumber(cmd.invoiceNumber());
		po.getLines().clear();
		applyPurchaseLines(po, cmd.lines(), cmd.invoiceNumber(), authentication);
		return purchaseOrderRepository.save(po);
	}

	@Transactional
	public boolean deletePurchase(long id, Authentication authentication) {
		PurchaseOrderEntity po = purchaseOrderRepository.findById(id).orElseThrow();
		restoreInventoryForPurchase(po, authentication);
		purchaseOrderRepository.delete(po);
		return true;
	}

	private void restoreInventoryForPurchase(PurchaseOrderEntity po, Authentication authentication) {
		if (po == null) return;
		for (var line : po.getLines()) {
			var batch = line.getBatch();
			if (batch == null) continue;
			Long batchId = batch.getId();
			if (batchId == null) continue;
			if (salesDeductionRepository.existsByBatchId(batchId)) {
				throw new IllegalArgumentException("Cannot edit/delete purchase: batch " + batch.getBatchNumber() + " has already been sold");
			}

			int qty = line.getQuantityReceived();
			if (qty <= 0) continue;
			InventoryItemEntity inv = inventoryRepository.findByBatchIdAndLocation(batchId, "MAIN").orElse(null);
			if (inv == null || inv.getQtyOnHand() < qty) {
				throw new IllegalArgumentException("Cannot edit/delete purchase: insufficient stock to rollback batch " + batch.getBatchNumber());
			}
			inv.setQtyOnHand(inv.getQtyOnHand() - qty);
			inventoryRepository.save(inv);

			StockMovementEntity mv = new StockMovementEntity();
			mv.setType(StockMovementType.RETURN);
			mv.setBatch(batch);
			mv.setQuantity(qty);
			mv.setCreatedBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);
			mv.setNote(po.getInvoiceNumber() != null ? ("Purchase rollback invoice: " + po.getInvoiceNumber()) : "Purchase rollback");
			stockMovementRepository.save(mv);
		}
	}

	private void applyPurchaseLines(PurchaseOrderEntity po, List<ReceivePurchaseLineCommand> lines, String invoice, Authentication authentication) {
		if (lines == null || lines.isEmpty()) {
			throw new IllegalArgumentException("At least one line is required");
		}
		for (ReceivePurchaseLineCommand line : lines) {
			ProductEntity product = productRepository.findById(line.productId()).orElseThrow();

			ProductBatchEntity batch = new ProductBatchEntity();
			batch.setProduct(product);
			batch.setBatchNumber(line.batchNumber().trim());
			batch.setExpiryDate(LocalDate.parse(line.expiryDate()));
			batch.setCostPrice(BigDecimal.valueOf(line.costPrice()));
			batch.setQuantityReceived(line.quantityReceived());
			ProductBatchEntity persistedBatch = batchRepository.save(batch);

			InventoryItemEntity inv = inventoryRepository
					.findByBatchIdAndLocation(persistedBatch.getId(), "MAIN")
					.orElseGet(() -> {
						InventoryItemEntity i = new InventoryItemEntity();
						i.setBatch(persistedBatch);
						i.setLocation("MAIN");
						return i;
					});
			inv.setQtyOnHand(inv.getQtyOnHand() + line.quantityReceived());
			inventoryRepository.save(inv);

			StockMovementEntity mv = new StockMovementEntity();
			mv.setType(StockMovementType.IN);
			mv.setBatch(persistedBatch);
			mv.setQuantity(line.quantityReceived());
			mv.setCreatedBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);
			mv.setNote(invoice != null ? ("Purchase invoice: " + invoice) : "Purchase received");
			stockMovementRepository.save(mv);

			PurchaseOrderLineEntity pol = new PurchaseOrderLineEntity();
			pol.setProduct(product);
			pol.setBatch(persistedBatch);
			pol.setCostPrice(BigDecimal.valueOf(line.costPrice()));
			pol.setQuantityReceived(line.quantityReceived());
			po.addLine(pol);
		}
	}

	public record ReceivePurchaseCommand(String supplier, String invoiceNumber, List<ReceivePurchaseLineCommand> lines) {
	}

	public record UpdatePurchaseCommand(long id, String supplier, String invoiceNumber, List<ReceivePurchaseLineCommand> lines) {
	}

	public record ReceivePurchaseLineCommand(long productId, String batchNumber, String expiryDate, double costPrice,
									 int quantityReceived) {
	}
}
