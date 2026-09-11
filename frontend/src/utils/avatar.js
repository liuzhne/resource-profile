// 列表页的文字头像：用姓名首字 + 按序轮转的底色，避免每行都要后端给头像
// 色板取自毛玻璃原型（柔和中明度，白字对比度足够）
const AVATAR_COLORS = ['#7aa7f5', '#f5a25a', '#6fc78a', '#b48cf0', '#f08ca8', '#5fb8c9']

/** 取姓名首字作为头像文字 */
export const initialOf = (name) => (name ? String(name).charAt(0) : '?')

/** 按行序轮转底色，保证同一列表内相邻行颜色不重复 */
export const avatarBg = (index = 0) => AVATAR_COLORS[index % AVATAR_COLORS.length]

export { AVATAR_COLORS }
