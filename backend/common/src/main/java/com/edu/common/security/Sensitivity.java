package com.edu.common.security;

/**
 * 字段敏感度分级（参见 docs/educare/FIELD_PERMISSION.md §2）。
 *
 * <ul>
 *   <li>{@link #PUBLIC}   —— 任何已登录用户可见</li>
 *   <li>{@link #MEDIUM}   —— 个人能力/表现（GPA、出勤率…）</li>
 *   <li>{@link #HIGH}     —— 家庭/经济/联系方式</li>
 *   <li>{@link #EXTREME}  —— 心理量表原始分、咨询记录、罪错处分、医疗记录</li>
 * </ul>
 */
public enum Sensitivity {
    PUBLIC,
    MEDIUM,
    HIGH,
    EXTREME
}
