package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.inventory.InventoryRepository;
import com.cosmetics.inventory.sales.SalesOrderRepository;
import com.cosmetics.inventory.stockmovement.StockMovementRepository;
import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
	private final PermissionGuard permissionGuard;
	private final com.cosmetics.inventory.branch.BranchScope branchScope;

	private final com.cosmetics.inventory.returns.SalesReturnRepository salesReturnRepository;

	public AlertsReportsGraphqlController(
			InventoryRepository inventoryRepository,
			StockMovementRepository stockMovementRepository,
			SalesOrderRepository salesOrderRepository,
			PermissionGuard permissionGuard,
			com.cosmetics.inventory.branch.BranchScope branchScope,
			com.cosmetics.inventory.returns.SalesReturnRepository salesReturnRepository
	) {
		this.inventoryRepository = inventoryRepository;
		this.stockMovementRepository = stockMovementRepository;
		this.salesOrderRepository = salesOrderRepository;
		this.permissionGuard = permissionGuard;
		this.branchScope = branchScope;
		this.salesReturnRepository = salesReturnRepository;
	}

	/**
	 * Returns reduce the day's figures on the day the goods came back: refund value leaves sales,
	 * and resalable goods put their cost back (damaged/expired cost stays — it is already an expense).
	 * [0] = returned sales value, [1] = cost restored.
	 */
	private double[] returnsImpact(Instant from, Instant to, String scoped) {
		double[] acc = new double[2];
		for (var r : salesReturnRepository.findWithLinesBetween(from, to)) {
			if (scoped != null && !r.getBranch().equalsIgnoreCase(scoped)) continue;
			acc[0] += r.getReturnValue().doubleValue();
			for (var l : r.getLines()) {
				if (l.getCondition() == com.cosmetics.inventory.returns.ReturnCondition.RESALABLE && l.getBatch() != null && l.getBatch().getCostPrice() != null) {
					acc[1] += l.getBatch().getCostPrice().multiply(BigDecimal.valueOf(l.getQuantity())).doubleValue();
				}
			}
		}
		return acc;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<ExpiryAlertDto> expiryAlerts(@Argument int days, @Argument String branch, Authentication authentication) {
		permissionGuard.requireAny(authentication, PermissionsService.PermissionAction.VIEW, PermissionModule.EXPIRY_ALERTS, PermissionModule.INVENTORY, PermissionModule.REPORTS);
		LocalDate today = LocalDate.now();
		LocalDate until = today.plusDays(days);
		String scoped = branchScope.scopeFilter(authentication, branch);

		return inventoryRepository.findAll().stream()
				.filter(i -> i.getQtyOnHand() > 0)
				.filter(i -> scoped == null || i.getLocation().equalsIgnoreCase(scoped))
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
	public DailySalesReportDto dailySalesReport(Authentication authentication, @Argument String date, @Argument String branch) {
		permissionGuard.require(authentication, PermissionModule.REPORTS, PermissionsService.PermissionAction.VIEW);
		String scoped = branchScope.scopeFilter(authentication, branch);
		LocalDate day = LocalDate.parse(date);
		Instant from = day.atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant to = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

		var orders = salesOrderRepository.findBySoldAtGreaterThanEqualAndSoldAtLessThan(from, to);
		var itemsByProduct = new java.util.LinkedHashMap<Long, DailySalesReportItemDto>();

		BigDecimal totalSales = BigDecimal.ZERO;
		BigDecimal totalCost = BigDecimal.ZERO;
		BigDecimal totalDiscount = BigDecimal.ZERO;

		for (var so : orders) {
			BigDecimal orderSubtotal = BigDecimal.ZERO;
			for (var line : so.getLines()) {
				if (scoped != null && (line.getLocation() == null || !line.getLocation().equalsIgnoreCase(scoped))) {
					continue;
				}
				orderSubtotal = orderSubtotal.add(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
			}
			if (orderSubtotal.signum() <= 0) {
				continue;
			}
			BigDecimal orderDiscount = so.getDiscount() != null ? so.getDiscount() : BigDecimal.ZERO;
			if (orderDiscount.signum() < 0) {
				orderDiscount = BigDecimal.ZERO;
			} else if (orderDiscount.compareTo(orderSubtotal) > 0) {
				orderDiscount = orderSubtotal;
			}
			totalDiscount = totalDiscount.add(orderDiscount);

			for (var line : so.getLines()) {
				if (scoped != null && (line.getLocation() == null || !line.getLocation().equalsIgnoreCase(scoped))) {
					continue;
				}
				var p = line.getProduct();
				long productId = p.getId();
				int qty = line.getQuantity();
				BigDecimal salesAmount = line.getUnitPrice().multiply(BigDecimal.valueOf(qty));
				BigDecimal netSales = orderDiscount.signum() > 0
						? salesAmount.subtract(orderDiscount.multiply(salesAmount).divide(orderSubtotal, 4, RoundingMode.HALF_UP))
						: salesAmount;
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
				double newSales = acc.salesAmount() + netSales.doubleValue();
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

				totalSales = totalSales.add(netSales);
				totalCost = totalCost.add(costAmount);
			}
		}

		double[] ret = returnsImpact(from, to, scoped);
		double netSalesTotal = totalSales.doubleValue() - ret[0];
		double netCostTotal = totalCost.doubleValue() - ret[1];
		return new DailySalesReportDto(
				day.toString(),
				netSalesTotal,
				netCostTotal,
				netSalesTotal - netCostTotal,
				totalDiscount.doubleValue(),
				ret[0],
				itemsByProduct.values().stream()
						.sorted(Comparator.comparing(DailySalesReportItemDto::salesAmount).reversed())
						.toList()
		);
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<DailySalesReportDto> salesDailyReports(Authentication authentication, @Argument String from, @Argument String to, @Argument String branch) {
		permissionGuard.require(authentication, PermissionModule.REPORTS, PermissionsService.PermissionAction.VIEW);
		String scoped = branchScope.scopeFilter(authentication, branch);
		LocalDate fromDay = LocalDate.parse(from);
		LocalDate toDay = LocalDate.parse(to);
		if (toDay.isBefore(fromDay)) {
			return List.of();
		}
		Instant fromI = fromDay.atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant toI = toDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

		var orders = salesOrderRepository.findBySoldAtGreaterThanEqualAndSoldAtLessThan(fromI, toI);
		Map<LocalDate, double[]> totals = new java.util.TreeMap<>();

		for (var so : orders) {
			LocalDate day = so.getSoldAt().atZone(ZoneOffset.UTC).toLocalDate();
			BigDecimal orderSubtotal = BigDecimal.ZERO;
			for (var line : so.getLines()) {
				if (scoped != null && (line.getLocation() == null || !line.getLocation().equalsIgnoreCase(scoped))) {
					continue;
				}
				orderSubtotal = orderSubtotal.add(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
			}
			if (orderSubtotal.signum() <= 0) {
				continue;
			}
			BigDecimal orderDiscount = so.getDiscount() != null ? so.getDiscount() : BigDecimal.ZERO;
			if (orderDiscount.signum() < 0) {
				orderDiscount = BigDecimal.ZERO;
			} else if (orderDiscount.compareTo(orderSubtotal) > 0) {
				orderDiscount = orderSubtotal;
			}

			double[] acc = totals.computeIfAbsent(day, k -> new double[4]);
			for (var line : so.getLines()) {
				if (scoped != null && (line.getLocation() == null || !line.getLocation().equalsIgnoreCase(scoped))) {
					continue;
				}
				BigDecimal salesAmount = line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
				BigDecimal netSales = orderDiscount.signum() > 0
						? salesAmount.subtract(orderDiscount.multiply(salesAmount).divide(orderSubtotal, 4, RoundingMode.HALF_UP))
						: salesAmount;
				BigDecimal costAmount = line.getDeductions().stream()
						.map(d -> d.getBatch().getCostPrice().multiply(BigDecimal.valueOf(d.getQuantity())))
						.reduce(BigDecimal.ZERO, BigDecimal::add);
				acc[0] += netSales.doubleValue();
				acc[1] += costAmount.doubleValue();
			}
			acc[2] += orderDiscount.doubleValue();
		}

		for (var r : salesReturnRepository.findWithLinesBetween(fromI, toI)) {
			if (scoped != null && !r.getBranch().equalsIgnoreCase(scoped)) continue;
			LocalDate day = r.getReturnedAt().atZone(ZoneOffset.UTC).toLocalDate();
			double[] acc = totals.computeIfAbsent(day, k -> new double[4]);
			acc[0] -= r.getReturnValue().doubleValue();
			acc[3] += r.getReturnValue().doubleValue();
			for (var l : r.getLines()) {
				if (l.getCondition() == com.cosmetics.inventory.returns.ReturnCondition.RESALABLE && l.getBatch() != null && l.getBatch().getCostPrice() != null) {
					acc[1] -= l.getBatch().getCostPrice().multiply(BigDecimal.valueOf(l.getQuantity())).doubleValue();
				}
			}
		}

		return totals.entrySet().stream()
				.map(e -> new DailySalesReportDto(
						e.getKey().toString(),
						e.getValue()[0],
						e.getValue()[1],
						e.getValue()[0] - e.getValue()[1],
						e.getValue()[2],
						e.getValue()[3],
						List.of()
				))
				.toList();
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<LowStockAlertDto> lowStockAlerts(@Argument int threshold, @Argument String branch, Authentication authentication) {
		permissionGuard.requireAny(authentication, PermissionsService.PermissionAction.VIEW, PermissionModule.EXPIRY_ALERTS, PermissionModule.INVENTORY, PermissionModule.REPORTS);
		String scoped = branchScope.scopeFilter(authentication, branch);
		Map<Long, Integer> qtyByProduct = inventoryRepository.findAll().stream()
				.filter(i -> i.getQtyOnHand() > 0)
				.filter(i -> scoped == null || i.getLocation().equalsIgnoreCase(scoped))
				.collect(java.util.stream.Collectors.groupingBy(
					i -> i.getBatch().getProduct().getId(),
					java.util.stream.Collectors.summingInt(i -> i.getQtyOnHand())
				));

		return inventoryRepository.findAll().stream()
				.filter(i -> scoped == null || i.getLocation().equalsIgnoreCase(scoped))
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
	public List<LowStockBatchAlertDto> lowStockBatchAlerts(@Argument int threshold, @Argument String branch, Authentication authentication) {
		permissionGuard.requireAny(authentication, PermissionsService.PermissionAction.VIEW, PermissionModule.EXPIRY_ALERTS, PermissionModule.INVENTORY, PermissionModule.REPORTS);
		String scoped = branchScope.scopeFilter(authentication, branch);
		return inventoryRepository.findAll().stream()
				.filter(i -> scoped == null || i.getLocation().equalsIgnoreCase(scoped))
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
	public InventoryValuationDto inventoryValuation(Authentication authentication, @Argument String branch) {
		permissionGuard.require(authentication, PermissionModule.REPORTS, PermissionsService.PermissionAction.VIEW);
		String scoped = branchScope.scopeFilter(authentication, branch);
		double total = inventoryRepository.findAll().stream()
				.filter(i -> i.getQtyOnHand() > 0)
				.filter(i -> scoped == null || i.getLocation().equalsIgnoreCase(scoped))
				.mapToDouble(i -> i.getQtyOnHand() * i.getBatch().getCostPrice().doubleValue())
				.sum();
		return new InventoryValuationDto(total);
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<StockMovementDto> movementAuditReport(Authentication authentication, @Argument MovementAuditFilter filter) {
		permissionGuard.require(authentication, PermissionModule.REPORTS, PermissionsService.PermissionAction.VIEW);
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
			double totalDiscountAmount,
			double totalReturnsAmount,
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
