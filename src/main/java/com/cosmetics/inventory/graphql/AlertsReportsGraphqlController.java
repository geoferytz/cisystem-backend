package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.inventory.InventoryRepository;
import com.cosmetics.inventory.sales.SalesOrderRepository;
import com.cosmetics.inventory.stockmovement.StockMovementRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Controller
public class AlertsReportsGraphqlController {
	private final InventoryRepository inventoryRepository;
	private final StockMovementRepository stockMovementRepository;
	private final SalesOrderRepository salesOrderRepository;

	public AlertsReportsGraphqlController(InventoryRepository inventoryRepository, StockMovementRepository stockMovementRepository, SalesOrderRepository salesOrderRepository) {
		this.inventoryRepository = inventoryRepository;
		this.stockMovementRepository = stockMovementRepository;
		this.salesOrderRepository = salesOrderRepository;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<ExpiryAlertDto> expiryAlerts(@Argument int days) {
		LocalDate today = LocalDate.now();
		LocalDate until = today.plusDays(days);

		return inventoryRepository.findAll().stream()
				.filter(i -> i.getQtyOnHand() > 0)
				.filter(i -> !i.getBatch().getExpiryDate().isAfter(until))
				.map(i -> {
					var batch = i.getBatch();
					var product = batch.getProduct();
					int daysToExpiry = (int) ChronoUnit.DAYS.between(today, batch.getExpiryDate());
					return new ExpiryAlertDto(
							product.getId(),
							product.getSku(),
							product.getName(),
							batch.getId(),
							batch.getBatchNumber(),
							batch.getExpiryDate().toString(),
							i.getQtyOnHand(),
							daysToExpiry
					);
				})
				.sorted(Comparator.comparingInt(ExpiryAlertDto::daysToExpiry))
				.toList();
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public DailySalesReportDto dailySalesReport(@Argument String date) {
		LocalDate day = LocalDate.parse(date);
		Instant from = day.atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant to = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

		var orders = salesOrderRepository.findBySoldAtGreaterThanEqualAndSoldAtLessThan(from, to);
		var itemsByProduct = new java.util.LinkedHashMap<Long, DailySalesReportItemDto>();

		BigDecimal totalSales = BigDecimal.ZERO;
		BigDecimal totalCost = BigDecimal.ZERO;

		for (var so : orders) {
			for (var line : so.getLines()) {
				var p = line.getProduct();
				long productId = p.getId();
				int qty = line.getQuantity();
				BigDecimal salesAmount = line.getUnitPrice().multiply(BigDecimal.valueOf(qty));
				BigDecimal costAmount = line.getDeductions().stream()
						.map(d -> d.getBatch().getCostPrice().multiply(BigDecimal.valueOf(d.getQuantity())))
						.reduce(BigDecimal.ZERO, BigDecimal::add);

				DailySalesReportItemDto acc = itemsByProduct.get(productId);
				if (acc == null) {
					acc = new DailySalesReportItemDto(
							productId,
							p.getSku(),
							p.getName(),
							0,
							0.0,
							0.0,
							0.0
					);
					itemsByProduct.put(productId, acc);
				}

				int newQty = acc.quantitySold() + qty;
				double newSales = acc.salesAmount() + salesAmount.doubleValue();
				double newCost = acc.costAmount() + costAmount.doubleValue();
				double newProfit = newSales - newCost;
				itemsByProduct.put(productId, new DailySalesReportItemDto(
						acc.productId(),
						acc.sku(),
						acc.productName(),
						newQty,
						newSales,
						newCost,
						newProfit
				));

				totalSales = totalSales.add(salesAmount);
				totalCost = totalCost.add(costAmount);
			}
		}

		double totalProfit = totalSales.subtract(totalCost).doubleValue();
		return new DailySalesReportDto(
				day.toString(),
				totalSales.doubleValue(),
				totalCost.doubleValue(),
				totalProfit,
				itemsByProduct.values().stream()
						.sorted(Comparator.comparing(DailySalesReportItemDto::salesAmount).reversed())
						.toList()
		);
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<LowStockAlertDto> lowStockAlerts(@Argument int threshold) {
		Map<Long, Integer> qtyByProduct = inventoryRepository.findAll().stream()
				.filter(i -> i.getQtyOnHand() > 0)
				.collect(java.util.stream.Collectors.groupingBy(
					i -> i.getBatch().getProduct().getId(),
					java.util.stream.Collectors.summingInt(i -> i.getQtyOnHand())
				));

		return inventoryRepository.findAll().stream()
				.map(i -> i.getBatch().getProduct())
				.distinct()
				.map(p -> new LowStockAlertDto(
						p.getId(),
						p.getSku(),
						p.getName(),
						qtyByProduct.getOrDefault(p.getId(), 0),
						threshold
				))
				.filter(a -> a.qtyOnHand() < threshold)
				.sorted(Comparator.comparingInt(LowStockAlertDto::qtyOnHand))
				.toList();
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<LowStockBatchAlertDto> lowStockBatchAlerts(@Argument int threshold) {
		return inventoryRepository.findAll().stream()
				.map(i -> {
					var batch = i.getBatch();
					var product = batch.getProduct();
					return new LowStockBatchAlertDto(
							product.getId(),
							product.getSku(),
							product.getName(),
							batch.getId(),
							batch.getBatchNumber(),
							batch.getExpiryDate().toString(),
							i.getLocation(),
							i.getQtyOnHand(),
							threshold
					);
				})
				.filter(a -> a.qtyOnHand() <= threshold)
				.sorted(Comparator.comparingInt(LowStockBatchAlertDto::qtyOnHand))
				.toList();
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public InventoryValuationDto inventoryValuation() {
		double total = inventoryRepository.findAll().stream()
				.filter(i -> i.getQtyOnHand() > 0)
				.mapToDouble(i -> i.getQtyOnHand() * i.getBatch().getCostPrice().doubleValue())
				.sum();
		return new InventoryValuationDto(total);
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<StockMovementDto> movementAuditReport(@Argument MovementAuditFilter filter) {
		String type = filter != null ? filter.type() : null;
		Instant from = filter != null && filter.from() != null && !filter.from().isBlank()
				? Instant.parse(filter.from())
				: null;
		Instant to = filter != null && filter.to() != null && !filter.to().isBlank()
				? Instant.parse(filter.to())
				: null;

		return stockMovementRepository.findAll().stream()
				.filter(mv -> type == null || type.isBlank() || mv.getType().name().equalsIgnoreCase(type.trim()))
				.filter(mv -> from == null || !mv.getCreatedAt().isBefore(from))
				.filter(mv -> to == null || !mv.getCreatedAt().isAfter(to))
				.sorted(Comparator.comparing(mv -> mv.getCreatedAt(), Comparator.reverseOrder()))
				.map(StockMovementDto::from)
				.toList();
	}

	public record MovementAuditFilter(String type, String from, String to) {
	}

	public record ExpiryAlertDto(
			Long productId,
			String sku,
			String productName,
			Long batchId,
			String batchNumber,
			String expiryDate,
			int qtyOnHand,
			int daysToExpiry
	) {
	}

	public record LowStockAlertDto(
			Long productId,
			String sku,
			String productName,
			int qtyOnHand,
			int threshold
	) {
	}

	public record LowStockBatchAlertDto(
			Long productId,
			String sku,
			String productName,
			Long batchId,
			String batchNumber,
			String expiryDate,
			String location,
			int qtyOnHand,
			int threshold
	) {
	}

	public record InventoryValuationDto(double totalStockValue) {
	}

	public record DailySalesReportDto(
			String date,
			double totalSalesAmount,
			double totalCostAmount,
			double totalProfitAmount,
			List<DailySalesReportItemDto> items
	) {
	}

	public record DailySalesReportItemDto(
			Long productId,
			String sku,
			String productName,
			int quantitySold,
			double salesAmount,
			double costAmount,
			double profitAmount
	) {
	}
}
