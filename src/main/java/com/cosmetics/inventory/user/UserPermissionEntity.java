package com.cosmetics.inventory.user;

import jakarta.persistence.*;

@Entity
@Table(
		name = "user_permissions",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_user_permissions_user_module", columnNames = {"user_id", "module"})
		}
)
public class UserPermissionEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

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

	public Long getId() {
		return id;
	}

	public UserEntity getUser() {
		return user;
	}

	public void setUser(UserEntity user) {
		this.user = user;
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
}
