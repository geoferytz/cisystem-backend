package com.cosmetics.inventory.user;

import jakarta.persistence.*;

@Entity
@Table(
		name = "role_permissions",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_role_permissions_role_module", columnNames = {"role_id", "module"})
		}
)
public class RolePermissionEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "role_id", nullable = false)
	private RoleEntity role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private PermissionModule module;

	@Column(nullable = false)
	private boolean canView;

	@Column(nullable = false)
	private boolean canCreate;

	@Column(nullable = false)
	private boolean canEdit;

	@Column(nullable = false)
	private boolean canDelete;

	/**
	 * Comma-separated fine-grained action codes (see PermissionCatalog).
	 * NULL = never configured; treated as "all non-sensitive extras granted".
	 */
	@Column(name = "extra_actions", length = 1000)
	private String extraActions;

	public Long getId() {
		return id;
	}

	public RoleEntity getRole() {
		return role;
	}

	public void setRole(RoleEntity role) {
		this.role = role;
	}

	public PermissionModule getModule() {
		return module;
	}

	public void setModule(PermissionModule module) {
		this.module = module;
	}

	public boolean isCanView() {
		return canView;
	}

	public void setCanView(boolean canView) {
		this.canView = canView;
	}

	public boolean isCanCreate() {
		return canCreate;
	}

	public void setCanCreate(boolean canCreate) {
		this.canCreate = canCreate;
	}

	public boolean isCanEdit() {
		return canEdit;
	}

	public void setCanEdit(boolean canEdit) {
		this.canEdit = canEdit;
	}

	public boolean isCanDelete() {
		return canDelete;
	}

	public void setCanDelete(boolean canDelete) {
		this.canDelete = canDelete;
	}

	public String getExtraActions() {
		return extraActions;
	}

	public void setExtraActions(String extraActions) {
		this.extraActions = extraActions;
	}
}
