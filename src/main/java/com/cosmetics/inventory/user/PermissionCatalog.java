package com.cosmetics.inventory.user;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Fine-grained actions per module, on top of the basic view/create/edit/delete.
 * Codes are stable identifiers stored per user; labels are shown in the admin UI.
 */
public final class PermissionCatalog {
	/** sensitive = not auto-granted to legacy rows; must be ticked explicitly. */
	public record Action(String code, String label, String description, boolean sensitive) {
		Action(String code, String label, String description) {
			this(code, label, description, false);
		}
	}

	private static final Map<PermissionModule, List<Action>> EXTRAS = new EnumMap<>(PermissionModule.class);

	static {
		EXTRAS.put(PermissionModule.COUNTER, List.of(
				new Action("DISCOUNT", "Give discount", "Apply a discount on a sale"),
				new Action("CREDIT_SALE", "Sell on credit", "Complete a sale with unpaid balance (debt)"),
				new Action("HOLD_CART", "Hold cart", "Park a sale and resume it later"),
				new Action("PRINT_RECEIPT", "Print receipt", "Preview and print sales receipts"),
				new Action("RETURN", "Start return", "Open return / exchange from the counter")));
		EXTRAS.put(PermissionModule.PRODUCTS, List.of(
				new Action("VIEW_COST", "View buying price", "See cost / buying price of products"),
				new Action("EDIT_PRICE", "Change prices", "Change buying and selling prices"),
				new Action("REGISTER_TO_BRANCH", "Add to branch", "Register existing products to a branch")));
		EXTRAS.put(PermissionModule.INVENTORY, List.of(
				new Action("RECEIVE", "Receive stock", "Confirm pending receiving orders"),
				new Action("ADJUST", "Adjust stock", "Correct quantities on hand"),
				new Action("VIEW_VALUE", "View stock value", "See inventory valuation at cost")));
		EXTRAS.put(PermissionModule.SALES, List.of(
				new Action("VIEW_CARDS", "View sales cards", "See Total Sales, Today Collection, Outstanding and Pending summary cards"),
				new Action("VIEW_ALL_SELLERS", "View all sellers", "See sales made by other users"),
				new Action("RECEIVE_PAYMENT", "Receive debt payment", "Record payments against sale debt"),
				new Action("PRINT_RECEIPT", "Print receipt", "Reprint receipts for past sales"),
				new Action("VIEW_PROFIT", "View profit", "See cost and profit on sales")));
		EXTRAS.put(PermissionModule.RETURNS, List.of(
				new Action("EXCHANGE", "Exchange products", "Swap returned items for other products"),
				new Action("CASH_REFUND", "Cash refund", "Give money back to the customer"),
				new Action("LATE_RETURN", "Late return override", "Accept returns after the 7-day window", true)));
		EXTRAS.put(PermissionModule.CUSTOMERS, List.of(
				new Action("VIEW_DEBT", "View debts", "See customer debt balances"),
				new Action("RECEIVE_PAYMENT", "Receive payment", "Record customer debt payments")));
		EXTRAS.put(PermissionModule.PURCHASING, List.of(
				new Action("RECEIVE", "Receive purchase", "Receive deliveries into stock"),
				new Action("PRICE_CHANGE", "Approve price change", "Accept new prices from purchase orders")));
		EXTRAS.put(PermissionModule.EXPENSES, List.of(
				new Action("VIEW_ALL", "View all expenses", "See expenses recorded by other users")));
		EXTRAS.put(PermissionModule.REPORTS, List.of(
				new Action("SALES", "Sales report", "Daily and period sales reports"),
				new Action("BREAKDOWN", "Sales breakdown", "Payment methods and sellers breakdown"),
				new Action("DEBTORS", "Debtors report", "Customers owing money"),
				new Action("STOCK", "Stock report", "Stock balance, movers and expiry"),
				new Action("AUDIT", "Movement audit", "All stock movements"),
				new Action("PRINT", "Print reports", "Print or save reports as PDF")));
		EXTRAS.put(PermissionModule.TRANSFERS, List.of(
				new Action("SEND", "Send transfer", "Dispatch stock to another branch"),
				new Action("RECEIVE", "Receive transfer", "Confirm incoming transfers")));
		EXTRAS.put(PermissionModule.USERS_ROLES, List.of(
				new Action("RESET_PASSWORD", "Reset passwords", "Reset other users' passwords", true),
				new Action("MANAGE_PERMISSIONS", "Manage permissions", "Change what other users can do", true)));
	}

	private PermissionCatalog() {}

	public static List<Action> extras(PermissionModule module) {
		return EXTRAS.getOrDefault(module, List.of());
	}

	public static boolean isValid(PermissionModule module, String code) {
		return extras(module).stream().anyMatch(a -> a.code().equals(code));
	}
}
