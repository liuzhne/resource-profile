/*
 * ESLint 8 (eslintrc 格式) —— Vue 3 + Vite + 自动导入。
 * 分工：格式交给 Prettier（`@vue/eslint-config-prettier` 关掉与 Prettier 冲突的格式规则，
 * 故本配置不强制分号/引号/缩进，与现有「无分号 + 单引号」风格不冲突）；ESLint 只管代码质量。
 * 自动导入的全局符号：vue/vue-router/pinia 由 `.eslintrc-auto-import.json`（vite 构建时生成）提供；
 * Element Plus 经 resolver 动态注入的函数式 API 不在该文件中，需在下方 globals 手动声明。
 */
module.exports = {
  root: true,
  env: {
    browser: true,
    node: true,
    es2021: true
  },
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
    './.eslintrc-auto-import.json',
    '@vue/eslint-config-prettier'
  ],
  parserOptions: {
    // <script lang="ts"> 由 @typescript-eslint/parser 解析（项目 74% 的 .vue 用 TS 语法，无 tsconfig，仅作语法解析）
    parser: '@typescript-eslint/parser',
    ecmaVersion: 'latest',
    sourceType: 'module'
  },
  globals: {
    // Element Plus 经 ElementPlusResolver 自动导入的函数式 API（不在 auto-import 生成的 globals 中）
    ElMessage: 'readonly',
    ElMessageBox: 'readonly',
    ElNotification: 'readonly',
    ElLoading: 'readonly'
  },
  rules: {
    // 项目存在单词组件名（index.vue / login 等），不强制多词组件名
    'vue/multi-word-component-names': 'off',
    // 未使用变量降为警告，允许下划线前缀占位
    'no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    'vue/no-unused-vars': ['warn', { ignorePattern: '^_' }]
  }
}
