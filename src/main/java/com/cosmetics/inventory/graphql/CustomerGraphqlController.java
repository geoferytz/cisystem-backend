package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.customer.CustomerEntity;
import com.cosmetics.inventory.customer.CustomerPaymentEntity;
import com.cosmetics.inventory.customer.CustomerPaymentRepository;
import com.cosmetics.inventory.customer.CustomerRepository;
import com.cosmetics.inventory.sales.SalesOrderEntity;
import com.cosmetics.inventory.sales.SalesOrderLineEntity;
import com.cosmetics.inventory.sales.SalesOrderRepository;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService;
import com.cosmetics.inventory.user.UserEntity;
import com.cosmetics.inventory.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Controller
public class CustomerGraphqlController {
	private final CustomerRepository customerRepository;
	private final CustomerPaymentRepository customerPaymentRepository;
	private final SalesOrderRepository salesOrderRepository;
	private final PermissionsService permissionsService;
	private final UserRepository userRepository;

	public CustomerGraphqlController(CustomerRepository customerRepository, CustomerPaymentRepository customerPaymentRepository, SalesOrderRepository salesOrderRepository, PermissionsService permissionsService, UserRepository userRepository) {
		this.customerRepository = customerRepository;
		this.customerPaymentRepository = customerPaymentRepository;
		this.salesOrderRepository = salesOrderRepository;
		this.permissionsService = permissionsService;
		this.userRepository = userRepository;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<CustomerDto> customers(@Argument String query, Authentication authentication) {
		requireAny(authentication, PermissionsService.PermissionAction.VIEW);
		String q = query != null ? query.trim() : null;
		List<CustomerEntity> found = (q != null && !q.isBlank())
				? customerRepository.findTop200ByNameContainingIgnoreCaseOrPhoneContainingIgnoreCaseOrderByNameAsc(q, q)
				: customerRepository.findAllByOrderByIdDesc();
		return found.stream().map(CustomerDto::from).toList();
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional
	public CustomerDto createCustomer(@Argument CreateCustomerInput input, Authentication authentication) {
		requireAny(authentication, PermissionsService.PermissionAction.CREATE);
		if (input == null || input.name() == null || input.name().isBlank()) {
			throw new IllegalArgumentException("Customer name is required");
		}
		CustomerEntity c = new CustomerEntity();
		c.setName(input.name().trim());
		c.setPhone(trimToNull(input.phone()));
		c.setEmail(trimToNull(input.email()));
		c.setAddress(trimToNull(input.address()));
		c.setCreatedBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);
		return CustomerDto.from(customerRepository.save(c));
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional
	public CustomerDto updateCustomer(@Argument UpdateCustomerInput input, Authentication authentication) {
		requireAny(authentication, PermissionsService.PermissionAction.EDIT);
		if (input == null || input.id() == null || input.id() <= 0) {
			throw new IllegalArgumentException("Customer id is required");
		}
		if (input.name() == null || input.name().isBlank()) {
			throw new IllegalArgumentException("Customer name is required");
		}
		CustomerEntity c = customerRepository.findById(input.id()).orElseThrow();
		c.setName(input.name().trim());
		c.setPhone(trimToNull(input.phone()));
		c.setEmail(trimToNull(input.email()));
		c.setAddress(trimToNull(input.address()));
		return CustomerDto.from(customerRepository.save(c));
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<CustomerPaymentDto> customerPayments(@Argument Long customerId, Authentication authentication) {
		requireAny(authentication, PermissionsService.PermissionAction.VIEW);
		if (customerId == null || customerId <= 0) {
			return List.of();
		}
		return customerPaymentRepository.findTop50ByCustomer_IdOrderByPaidAtDescIdDesc(customerId).stream()
				.map(CustomerPaymentDto::from)
				.toList();
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional
	public List<CustomerPaymentDto> recordCustomerPayment(@Argument Long customerId, @Argument Double amount, @Argument String paidAt, Authentication authentication) {
		requireAny(authentication, PermissionsService.PermissionAction.EDIT);
		if (customerId == null || customerId <= 0) {
			throw new IllegalArgumentException("Customer is required");
		}
		if (amount == null || !(amount > 0)) {
			throw new IllegalArgumentException("Payment amount must be greater than zero");
		}
		CustomerEntity customer = customerRepository.findById(customerId)
				.orElseThrow(() -> new IllegalArgumentException("Customer not found"));
		Instant when;
		try {
			when = paidAt != null && !paidAt.isBlank() ? Instant.parse(paidAt.trim()) : Instant.now();
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid payment date");
		}

		BigDecimal remaining = BigDecimal.valueOf(amount);
		String recordedBy = authentication != null ? String.valueOf(authentication.getPrincipal()) : null;
		List<CustomerPaymentEntity> saved = new ArrayList<>();
		for (SalesOrderEntity order : salesOrderRepository.findWithLinesByCustomerId(customerId)) {
			if (remaining.signum() <= 0) {
				break;
			}
			BigDecimal net = order.getLines().stream()
					.map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
					.reduce(BigDecimal.ZERO, BigDecimal::add)
					.subtract(order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO)
					.max(BigDecimal.ZERO);
			BigDecimal received = order.getAmountReceived() != null ? order.getAmountReceived() : net;
			BigDecimal outstanding = net.subtract(received).max(BigDecimal.ZERO);
			if (outstanding.signum() <= 0) {
				continue;
			}
			BigDecimal pay = remaining.min(outstanding);
			order.setAmountReceived(received.add(pay));
			salesOrderRepository.save(order);
			CustomerPaymentEntity p = new CustomerPaymentEntity();
			p.setCustomer(customer);
			p.setSalesOrder(order);
			p.setAmount(pay);
			p.setPaidAt(when);
			p.setCreatedBy(recordedBy);
			saved.add(customerPaymentRepository.save(p));
			remaining = remaining.subtract(pay);
		}
		if (saved.isEmpty()) {
			throw new IllegalArgumentException("This customer has no outstanding debt");
		}
		if (remaining.signum() > 0) {
			throw new IllegalArgumentException("Payment exceeds the customer's outstanding debt");
		}
		return saved.stream().map(CustomerPaymentDto::from).toList();
	}

	private void requireAny(Authentication authentication, PermissionsService.PermissionAction action) {
		if (authentication == null || authentication.getPrincipal() == null) {
			throw new AccessDeniedException("Not authenticated");
		}
		for (GrantedAuthority a : authentication.getAuthorities()) {
			if (a != null && "ROLE_ADMIN".equalsIgnoreCase(String.valueOf(a.getAuthority()))) {
				return;
			}
		}
		String email = String.valueOf(authentication.getPrincipal());
		UserEntity user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
		boolean ok = permissionsService.hasPermission(user.getId(), PermissionModule.CUSTOMERS, action)
				|| permissionsService.hasPermission(user.getId(), PermissionModule.SALES, action);
		if (!ok) {
			throw new AccessDeniedException("Forbidden");
		}
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	public record CreateCustomerInput(String name, String phone, String email, String address) {
	}

	public record UpdateCustomerInput(Long id, String name, String phone, String email, String address) {
	}
}
