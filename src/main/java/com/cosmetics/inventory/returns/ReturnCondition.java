package com.cosmetics.inventory.returns;

/** Physical condition of a returned item; decides whether it goes back to stock or is written off as a loss. */
public enum ReturnCondition {
	RESALABLE,
	DAMAGED,
	EXPIRED
}
