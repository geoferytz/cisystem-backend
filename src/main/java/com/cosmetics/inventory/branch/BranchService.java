package com.cosmetics.inventory.branch;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BranchService {
	private final BranchRepository branchRepository;

	public BranchService(BranchRepository branchRepository) {
		this.branchRepository = branchRepository;
	}

	@Transactional(readOnly = true)
	public List<BranchEntity> findBranches(String query, Boolean active) {
		if (query != null && !query.isBlank()) {
			return branchRepository.findTop200ByNameContainingIgnoreCaseOrderByNameAsc(query.trim());
		}
		if (active != null) {
			return branchRepository.findTop200ByActiveOrderByNameAsc(active);
		}
		return branchRepository.findAll();
	}

	@Transactional
	public BranchEntity createBranch(CreateBranchCommand cmd) {
		if (cmd.name() == null || cmd.name().isBlank()) {
			throw new IllegalArgumentException("Name is required");
		}
		if (cmd.code() == null || cmd.code().isBlank()) {
			throw new IllegalArgumentException("Code is required");
		}

		branchRepository.findByNameIgnoreCase(cmd.name()).ifPresent(existing -> {
			throw new IllegalArgumentException("Branch name already exists");
		});
		branchRepository.findByCodeIgnoreCase(cmd.code()).ifPresent(existing -> {
			throw new IllegalArgumentException("Branch code already exists");
		});

		BranchEntity b = new BranchEntity();
		b.setCode(cmd.code().trim().toUpperCase());
		b.setName(cmd.name().trim());
		b.setAddress(cmd.address());
		b.setPhone(cmd.phone());
		return branchRepository.save(b);
	}

	@Transactional
	public BranchEntity updateBranch(UpdateBranchCommand cmd) {
		BranchEntity b = branchRepository.findById(cmd.id()).orElseThrow();

		if (cmd.name() != null && !cmd.name().isBlank() && !cmd.name().equalsIgnoreCase(b.getName())) {
			branchRepository.findByNameIgnoreCase(cmd.name()).ifPresent(existing -> {
				throw new IllegalArgumentException("Branch name already exists");
			});
			b.setName(cmd.name().trim());
		}

		if (cmd.code() != null && !cmd.code().isBlank() && !cmd.code().equalsIgnoreCase(b.getCode())) {
			if (b.isMain()) {
				throw new IllegalArgumentException("Main branch code cannot be changed");
			}
			branchRepository.findByCodeIgnoreCase(cmd.code()).ifPresent(existing -> {
				throw new IllegalArgumentException("Branch code already exists");
			});
			b.setCode(cmd.code().trim().toUpperCase());
		}

		if (cmd.address() != null) {
			b.setAddress(cmd.address());
		}
		if (cmd.phone() != null) {
			b.setPhone(cmd.phone());
		}
		if (cmd.active() != null) {
			if (b.isMain() && !cmd.active()) {
				throw new IllegalArgumentException("Main branch cannot be deactivated");
			}
			b.setActive(cmd.active());
		}

		return branchRepository.save(b);
	}

	@Transactional
	public boolean deleteBranch(long id) {
		BranchEntity b = branchRepository.findById(id).orElse(null);
		if (b == null) {
			return false;
		}
		if (b.isMain()) {
			throw new IllegalArgumentException("Main branch cannot be deleted");
		}
		branchRepository.deleteById(id);
		return true;
	}

	@Transactional
	public BranchEntity ensureMainBranch() {
		return branchRepository.findFirstByMainTrue().orElseGet(() -> {
			BranchEntity b = new BranchEntity();
			b.setCode("MAIN");
			b.setName("Main Branch");
			b.setMain(true);
			return branchRepository.save(b);
		});
	}

	public record CreateBranchCommand(String code, String name, String address, String phone) {
	}

	public record UpdateBranchCommand(long id, String code, String name, String address, String phone, Boolean active) {
	}
}
