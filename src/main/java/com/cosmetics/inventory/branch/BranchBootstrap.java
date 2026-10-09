package com.cosmetics.inventory.branch;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class BranchBootstrap implements ApplicationRunner {
	private final BranchService branchService;

	public BranchBootstrap(BranchService branchService) {
		this.branchService = branchService;
	}

	@Override
	public void run(ApplicationArguments args) {
		branchService.ensureMainBranch();
	}
}
