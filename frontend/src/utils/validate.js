// 判断是否为外部链接
export const isExternal = (path) => {
  return /^(https?:|mailto:|tel:)/.test(path)
}

// 验证手机号
export const isValidPhone = (phone) => {
  return /^1[3-9]\d{9}$/.test(phone)
}

// 验证邮箱
export const isValidEmail = (email) => {
  return /^[\w-]+(\.[\w-]+)*@[\w-]+(\.[\w-]+)+$/.test(email)
}

// 验证身份证号
export const isValidIdCard = (idCard) => {
  return /^[1-9]\d{5}(18|19|20)\d{2}((0[1-9])|(1[0-2]))(([0-2][1-9])|10|20|30|31)\d{3}[0-9Xx]$/.test(
    idCard
  )
}
