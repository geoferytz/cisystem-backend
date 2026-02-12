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
		SalesOrderEntity so = new SalesOrderEntity();
		so.setCustomer(cmd.customer());
		so.setReferenceNumber(cmd.referenceNumber());
		so.setSoldBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);
		applySaleLines(so, cmd.lines(), cmd.referenceNumber(), authentication);
		return salesOrderRepository.save(so);
	}

	@Transactional
	public SalesOrderEntity updateSale(UpdateSaleCommand cmd, Authentication authentication) {
		if (cmd == null || cmd.id() <= 0) {
			throw new IllegalArgumentException("Sale id is required");
		}
		SalesOrderEntity so = salesOrderRepository.findById(cmd.id()).orElseThrow();
		restoreInventoryForSale(so, authentication);

		so.setCustomer(cmd.customer());
		so.setReferenceNumber(cmd.referenceNumber());

		so.getLines().clear();
		applySaleLines(so, cmd.lines(), cmd.referenceNumber(), authentication);
		return salesOrderRepository.save(so);
	}

    @Transactional
    public boolean deleteSale(long id, Authentication authentication) {
        SalesOrderEntity so = salesOrderRepository.findById(id).orElseThrow();
        restoreInventoryForSale(so, authentication);
        salesOrderRepository.delete(so);
        return true;
    }

    private void restoreInventoryForSale(SalesOrderEntity so, Authentication authentication) {
        if (so == null) return;
        for (SalesOrderLineEntity line : so.getLines()) {
            String location = (line.getLocation() != null && !line.getLocation().isBlank()) ? line.getLocation().trim() : "MAIN";
            for (SalesDeductionEntity d : line.getDeductions()) {
                ProductBatchEntity batch = d.getBatch();
                if (batch == null) continue;
                int qty = d.getQuantity();
                if (qty <= 0) continue;

                InventoryItemEntity inv = inventoryRepository.findByBatchIdAndLocation(batch.getId(), location)
                        .orElseGet(() -> {
                            InventoryItemEntity i = new InventoryItemEntity();
                            i.setBatch(batch);
                            i.setLocation(location);
                            i.setQtyOnHand(0);
                            return i;
                        });
                inv.setQtyOnHand(inv.getQtyOnHand() + qty);
                inventoryRepository.save(inv);

                StockMovementEntity mv = new StockMovementEntity();
                mv.setType(StockMovementType.RETURN);
                mv.setBatch(batch);
                mv.setQuantity(qty);
                mv.setCreatedBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);
                mv.setNote(so.getReferenceNumber() != null ? ("Sale rollback ref: " + so.getReferenceNumber()) : "Sale rollback");
                stockMovementRepository.save(mv);
            }
        }
    }

    private void applySaleLines(SalesOrderEntity so, List<CreateSaleLineCommand> lines, String ref, Authentication authentication) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("At least one line is required");
        }

        for (CreateSaleLineCommand line : lines) {
            if (line == null) continue;
            if (line.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be > 0");
            }
            ProductEntity product = productRepository.findById(line.productId()).orElseThrow();
            String location = (line.location() != null && !line.location().isBlank()) ? line.location().trim() : "MAIN";

            SalesOrderLineEntity sol = new SalesOrderLineEntity();
            sol.setProduct(product);
            sol.setQuantity(line.quantity());
            sol.setUnitPrice(BigDecimal.valueOf(line.unitPrice()));
            sol.setLocation(location);

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
                mv.setNote(ref != null ? ("Sale ref: " + ref) : "Sale");
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
    }

    public record CreateSaleCommand(String customer, String referenceNumber, List<CreateSaleLineCommand> lines) {
    }

    public record UpdateSaleCommand(long id, String customer, String referenceNumber, List<CreateSaleLineCommand> lines) {
    }

    public record CreateSaleLineCommand(long productId, int quantity, double unitPrice, String location) {
    }
}
