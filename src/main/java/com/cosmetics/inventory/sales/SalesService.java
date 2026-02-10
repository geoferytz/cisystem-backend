package com.cosmetics.inventory.sales;

import com.cosmetics.inventory.inventory.InventoryItemEntity;
import com.cosmetics.inventory.inventory.InventoryRepository;
import com.cosmetics.inventory.product.ProductBatchEntity;
import com.cosmetics.inventory.product.ProductBatchRepository;
import com.cosmetics.inventory.product.ProductEntity;
import com.cosmetics.inventory.product.ProductRepository;
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
public class SalesService {
	private final SalesOrderRepository salesOrderRepository;
	private final ProductRepository productRepository;
	private final ProductBatchRepository batchRepository;
	private final InventoryRepository inventoryRepository;
	private final StockMovementRepository stockMovementRepository;

	public SalesService(
			SalesOrderRepository salesOrderRepository,
			ProductRepository productRepository,
			ProductBatchRepository batchRepository,
			InventoryRepository inventoryRepository,
			StockMovementRepository stockMovementRepository
	) {
		this.salesOrderRepository = salesOrderRepository;
		this.productRepository = productRepository;
		this.batchRepository = batchRepository;
		this.inventoryRepository = inventoryRepository;
		this.stockMovementRepository = stockMovementRepository;
	}

	@Transactional
	public SalesOrderEntity createSale(CreateSaleCommand cmd, Authentication authentication) {
		if (cmd.lines() == null || cmd.lines().isEmpty()) {
			throw new IllegalArgumentException("At least one line is required");
		}

		SalesOrderEntity so = new SalesOrderEntity();
		so.setCustomer(cmd.customer());
		so.setReferenceNumber(cmd.referenceNumber());
		so.setSoldBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);

		for (CreateSaleLineCommand line : cmd.lines()) {
			if (line.quantity() <= 0) {
				throw new IllegalArgumentException("Quantity must be > 0");
			}
			ProductEntity product = productRepository.findById(line.productId()).orElseThrow();
			String location = (line.location() != null && !line.location().isBlank()) ? line.location().trim() : "MAIN";

			SalesOrderLineEntity sol = new SalesOrderLineEntity();
			sol.setProduct(product);
			sol.setQuantity(line.quantity());
			sol.setUnitPrice(BigDecimal.valueOf(line.unitPrice()));

			int remaining = line.quantity();
			LocalDate today = LocalDate.now();

			List<ProductBatchEntity> batches = batchRepository.findByProductIdOrderByExpiryDateAscCreatedAtAsc(product.getId());
			for (ProductBatchEntity batch : batches) {
				if (remaining <= 0) break;
				if (batch.getExpiryDate().isBefore(today)) {
					continue; // expired batch restriction
				}

				InventoryItemEntity inv = inventoryRepository.findByBatchIdAndLocation(batch.getId(), location).orElse(null);
				if (inv == null || inv.getQtyOnHand() <= 0) {
					continue;
				}

				int take = Math.min(inv.getQtyOnHand(), remaining);
				inv.setQtyOnHand(inv.getQtyOnHand() - take);
				inventoryRepository.save(inv);

				StockMovementEntity mv = new StockMovementEntity();
				mv.setType(StockMovementType.OUT);
				mv.setBatch(batch);
				mv.setQuantity(take);
				mv.setCreatedBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);
				mv.setNote(cmd.referenceNumber() != null ? ("Sale ref: " + cmd.referenceNumber()) : "Sale");
				stockMovementRepository.save(mv);

				SalesDeductionEntity d = new SalesDeductionEntity();
				d.setBatch(batch);
				d.setQuantity(take);
				sol.addDeduction(d);

				remaining -= take;
			}

			if (remaining > 0) {
				throw new IllegalArgumentException("Insufficient non-expired stock for product " + product.getSku() + " at location " + location);
			}

			so.addLine(sol);
		}

		return salesOrderRepository.save(so);
	}

	public record CreateSaleCommand(String customer, String referenceNumber, List<CreateSaleLineCommand> lines) {
	}

	public record CreateSaleLineCommand(long productId, int quantity, double unitPrice, String location) {
	}
}
