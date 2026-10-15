export const isSupportedPermission = (permission, roleCode) =>
  Array.isArray(permission.allowedRoles) && permission.allowedRoles.includes(roleCode)

export const supportedSelection = (catalog, roleCode, selected) => {
  const supported = catalog
    .filter((p) => isSupportedPermission(p, roleCode))
    .map((p) => String(p.id))
  return roleCode === 'admin'
    ? supported
    : selected.map(String).filter((id) => supported.includes(id))
}
