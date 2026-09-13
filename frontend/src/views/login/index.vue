<template>
  <div class="login-container">
    <!-- 左侧品牌栏：窄屏（< 920px）隐藏，logo 改由表单区顶部呈现 -->
    <aside class="brand-panel">
      <div class="brand-glow"></div>

      <div class="brand-head">
        <span class="brand-mark">
          <BrandLogo :size="21" />
        </span>
        <span class="brand-name">
          <span class="name">见微</span>
          <span class="divider"></span>
          <span class="sub">师生资源画像平台</span>
        </span>
      </div>

      <div class="brand-body">
        <h2>四类画像，一个入口</h2>
        <p>汇总学业、科研、教学与心理测评数据，为每一位师生生成可追溯的资源画像。</p>

        <ul class="brand-points">
          <li v-for="p in brandPoints" :key="p.title">
            <span class="point-icon">
              <el-icon :size="16"><component :is="p.icon" /></el-icon>
            </span>
            <span class="point-title">{{ p.title }}</span>
            <span class="point-note">{{ p.note }}</span>
          </li>
        </ul>
      </div>

      <div class="brand-foot">
        <span>© 2026 信息中心</span>
        <span class="link" @click="notReady">使用条款</span>
        <span class="link" @click="notReady">隐私政策</span>
      </div>
    </aside>

    <!-- 右侧表单区 -->
    <main class="form-panel">
      <div class="form-top">
        <span class="muted">还没有账号？</span>
        <span class="link-accent" @click="notReady">联系管理员开通</span>
      </div>

      <div class="form-center">
        <div class="form-box">
          <!-- 窄屏时补上品牌标识 -->
          <div class="compact-brand">
            <span class="compact-mark">
              <BrandLogo :size="21" />
            </span>
            <span class="brand-name">
              <span class="name">见微</span>
              <span class="divider"></span>
              <span class="sub">师生资源画像平台</span>
            </span>
          </div>

          <h1 class="welcome">欢迎回来</h1>
          <p class="welcome-sub">请使用校内账号登录见微，继续查看你的画像数据。</p>

          <!-- 登录方式切换 -->
          <div class="seg-control login-tabs">
            <button
              v-for="t in loginTabs"
              :key="t.key"
              type="button"
              class="seg-item"
              :class="{ 'is-active': loginTab === t.key }"
              @click="switchTab(t.key)"
            >
              {{ t.label }}
            </button>
          </div>

          <!-- 账号密码登录 -->
          <el-form
            v-show="loginTab === 'password'"
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            class="login-form"
            @keyup.enter="handleLogin"
          >
            <el-form-item prop="username">
              <label class="field-label">账号</label>
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                :prefix-icon="User"
              />
            </el-form-item>

            <el-form-item prop="password">
              <div class="field-label-row">
                <label class="field-label">密码</label>
                <span class="link-accent" @click="notReady">忘记密码？</span>
              </div>
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>
          </el-form>

          <!-- 手机验证码登录：原型有此入口，后端暂无短信接口，整体禁用占位 -->
          <div v-show="loginTab === 'sms'" class="sms-form">
            <div class="field">
              <label class="field-label">手机号</label>
              <el-input disabled placeholder="138 0013 8000">
                <template #prefix>
                  <span class="phone-prefix tnum">+86</span>
                </template>
              </el-input>
            </div>
            <div class="field">
              <label class="field-label">验证码</label>
              <div class="sms-row">
                <el-input disabled placeholder="6 位验证码" :prefix-icon="Message" />
                <el-button class="sms-btn" disabled>获取验证码</el-button>
              </div>
            </div>
            <p class="sms-hint">短信登录暂未开放，请使用账号密码登录。</p>
          </div>

          <el-checkbox v-model="loginForm.remember" class="remember"
            >记住我，7 天内免登录</el-checkbox
          >

          <el-button
            type="primary"
            class="login-btn"
            :loading="loading"
            :disabled="loginTab === 'sms'"
            @click="handleLogin"
          >
            登录
          </el-button>

          <div class="divider-row">
            <span class="line"></span>
            <span class="or">或</span>
            <span class="line"></span>
          </div>

          <div class="alt-login">
            <el-button class="alt-btn" @click="notReady">
              <el-icon :size="16"><School /></el-icon>
              <span>统一身份认证</span>
            </el-button>
            <el-button class="alt-btn" @click="notReady">
              <el-icon :size="16"><Grid /></el-icon>
              <span>扫码登录</span>
            </el-button>
          </div>

          <p class="default-account">默认账号：admin / admin</p>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Lock, Message, School, Grid } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { prewarmGateway } from '@/utils/prewarm'
import BrandLogo from '@/components/BrandLogo.vue'

const userStore = useUserStore()
const loading = ref(false)
const loginFormRef = ref()
const loginTab = ref('password')

const loginTabs = [
  { key: 'password', label: '账号密码' },
  { key: 'sms', label: '手机验证码' }
]

const brandPoints = [
  { icon: 'UserFilled', title: '教师画像', note: '教学 · 科研 · 评价' },
  { icon: 'Reading', title: '学生画像', note: '学业 · 综合素质' },
  { icon: 'FirstAidKit', title: '心理健康', note: '测评 · 趋势分析' },
  { icon: 'Bell', title: 'AI 预警', note: '风险识别 · 归因报告' }
]

const loginForm = reactive({
  username: 'admin',
  password: 'admin',
  remember: false
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const switchTab = (key: string) => {
  loginTab.value = key
}

const notReady = () => {
  ElMessage.info('该功能暂未开放')
}

// 登录成功后跳转的页面都要经网关，而网关可能正在休眠。
// 趁用户填表单这十几秒先把网关唤醒，两段等待重叠起来。
onMounted(() => {
  prewarmGateway()
})

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid: boolean) => {
    if (valid) {
      loading.value = true
      try {
        await userStore.loginAction(loginForm)
        ElMessage.success('登录成功')
      } catch (error) {
        // 网络/冷启动类失败已由 request.js 统一提示，此处只兜「凭据不对」，
        // 否则服务唤醒超时会被误报成密码错误，且重复弹两条提示
        if (error.response) {
          ElMessage.error('登录失败，请检查用户名和密码')
        }
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped lang="scss">
.login-container {
  height: 100vh;
  display: flex;
  overflow: hidden;
}

/* ---------- 左侧品牌栏 ---------- */
.brand-panel {
  width: 46%;
  max-width: 620px;
  flex-shrink: 0;
  position: relative;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 24px;
  padding: 36px 44px;
  // 原型 brandTo = accent 各通道 × 0.52
  background: linear-gradient(150deg, #007aff 0%, rgb(0, 63, 133) 100%);

  .brand-glow {
    position: absolute;
    inset: 0;
    pointer-events: none;
    background:
      radial-gradient(620px 420px at 88% 6%, rgba(255, 255, 255, 0.26), transparent 62%),
      radial-gradient(520px 380px at 6% 96%, rgba(255, 255, 255, 0.16), transparent 60%);
  }

  .brand-head {
    position: relative;
    display: flex;
    align-items: center;
    gap: 11px;
    flex-shrink: 0;
  }

  .brand-mark {
    width: 36px;
    height: 36px;
    border-radius: 11px;
    background: rgba(255, 255, 255, 0.22);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border: 1px solid rgba(255, 255, 255, 0.34);
    color: #fff;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .brand-name {
    display: flex;
    align-items: baseline;
    gap: 9px;

    .name {
      color: #fff;
      font-size: 19px;
      font-weight: 600;
      letter-spacing: 0.06em;
    }

    .divider {
      width: 1px;
      height: 13px;
      background: rgba(255, 255, 255, 0.34);
      display: block;
    }

    .sub {
      color: rgba(255, 255, 255, 0.82);
      font-size: 13px;
    }
  }

  .brand-body {
    position: relative;

    h2 {
      color: #fff;
      font-size: 30px;
      font-weight: 600;
      line-height: 1.34;
      letter-spacing: -0.02em;
      text-wrap: balance;
    }

    > p {
      color: rgba(255, 255, 255, 0.82);
      font-size: 15px;
      line-height: 1.7;
      margin-top: 14px;
      max-width: 400px;
      text-wrap: pretty;
    }
  }

  .brand-points {
    display: flex;
    flex-direction: column;
    gap: 12px;
    margin-top: 26px;

    li {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .point-icon {
      width: 34px;
      height: 34px;
      border-radius: 11px;
      background: rgba(255, 255, 255, 0.18);
      border: 1px solid rgba(255, 255, 255, 0.26);
      color: #fff;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .point-title {
      color: #fff;
      font-size: 14px;
      font-weight: 500;
    }

    .point-note {
      color: rgba(255, 255, 255, 0.72);
      font-size: 13px;
    }
  }

  .brand-foot {
    position: relative;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 22px;
    flex-shrink: 0;
    color: rgba(255, 255, 255, 0.72);
    font-size: 12px;

    .link {
      cursor: pointer;

      &:hover {
        color: #fff;
      }
    }
  }
}

/* ---------- 右侧表单区 ---------- */
.form-panel {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding: 24px 20px;

  .form-top {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
    font-size: 13px;
  }

  .form-center {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 28px 0;
  }

  .form-box {
    width: 100%;
    max-width: 372px;
  }
}

.muted {
  color: var(--text-color-secondary);
}

.link-accent {
  color: var(--primary-color);
  font-weight: 500;
  cursor: pointer;
  font-size: 12px;

  &:hover {
    color: var(--primary-color-active);
  }
}

.form-top .link-accent {
  font-size: 13px;
}

// 窄屏才出现的紧凑品牌标识
.compact-brand {
  display: none;
  align-items: center;
  gap: 11px;
  margin-bottom: 28px;

  .compact-mark {
    width: 36px;
    height: 36px;
    border-radius: 11px;
    background: var(--primary-color);
    color: #fff;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    box-shadow: 0 6px 16px -8px rgba(0, 122, 255, 0.6);
  }

  .brand-name {
    display: flex;
    align-items: baseline;
    gap: 9px;

    .name {
      color: var(--text-color);
      font-size: 19px;
      font-weight: 600;
      letter-spacing: 0.06em;
    }

    .divider {
      width: 1px;
      height: 13px;
      background: rgba(60, 60, 67, 0.2);
      display: block;
    }

    .sub {
      color: var(--text-color-secondary);
      font-size: 13px;
    }
  }
}

.welcome {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-color);
  letter-spacing: -0.02em;
}

.welcome-sub {
  font-size: 14px;
  color: var(--text-color-secondary);
  margin-top: 8px;
}

.login-tabs {
  margin: 24px 0 18px;
  padding: 3px;
  border-radius: var(--radius-md);

  .seg-item {
    height: 28px;
    padding: 0 16px;
    font-size: 13px;
    border-radius: var(--radius-sm);
  }
}

/* ---------- 表单字段 ---------- */
.field-label,
.field-label-row {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-color-secondary);
  margin-bottom: 7px;
  line-height: 1;
}

.field-label-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;

  .field-label {
    margin-bottom: 0;
  }
}

.login-form,
.sms-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

:deep(.el-form-item) {
  margin-bottom: 0;
  display: block;
}

// 玻璃输入框：46px / 圆角 12px
:deep(.el-input__wrapper) {
  height: 46px;
  padding: 0 14px;
  background: var(--glass-bg-input);
  border-radius: var(--radius-lg);
  box-shadow: 0 0 0 1px var(--border-color-strong) inset;
  transition:
    box-shadow 0.18s,
    background 0.18s;

  &:hover {
    box-shadow: 0 0 0 1px rgba(60, 60, 67, 0.28) inset;
  }

  &.is-focus {
    background: rgba(255, 255, 255, 0.86);
    box-shadow:
      0 0 0 1px var(--primary-color) inset,
      0 0 0 4px var(--primary-tint);
  }
}

:deep(.el-input__inner) {
  font-size: 15px;
  color: var(--text-color);
}

.phone-prefix {
  color: rgba(60, 60, 67, 0.55);
  font-size: 14px;
  padding-right: 10px;
  border-right: 1px solid var(--border-color-strong);
}

.sms-row {
  display: flex;
  gap: 10px;

  .el-input {
    flex: 1;
    min-width: 0;
  }

  .sms-btn {
    flex-shrink: 0;
    height: 46px;
    padding: 0 16px;
    font-size: 13px;
    font-weight: 600;
    border-radius: var(--radius-lg);
    border: 1px solid var(--border-color-strong);
    background: var(--glass-bg-input);
  }
}

.sms-hint {
  font-size: 12px;
  color: var(--text-color-tertiary);
}

.remember {
  margin: 18px 0 20px;
  height: auto;

  :deep(.el-checkbox__label) {
    font-size: 14px;
    color: var(--fill-grey-fg);
  }

  :deep(.el-checkbox__inner) {
    width: 18px;
    height: 18px;
    border-radius: 6px;
    background: rgba(255, 255, 255, 0.7);
    border-color: rgba(60, 60, 67, 0.22);

    &::after {
      left: 6px;
      top: 3px;
    }
  }
}

.login-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: var(--radius-lg);
  border: none;
  box-shadow: 0 6px 18px -8px rgba(0, 122, 255, 0.6);
  transition:
    transform 0.12s ease,
    box-shadow 0.18s ease;

  &:hover:not(.is-disabled) {
    transform: translateY(-1px);
    box-shadow: 0 10px 24px -8px rgba(0, 122, 255, 0.6);
  }

  &:active:not(.is-disabled) {
    transform: translateY(0);
  }
}

.divider-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 22px 0;

  .line {
    flex: 1;
    height: 1px;
    background: rgba(60, 60, 67, 0.12);
    display: block;
  }

  .or {
    font-size: 12px;
    color: rgba(60, 60, 67, 0.5);
  }
}

.alt-login {
  display: flex;
  gap: 10px;

  .alt-btn {
    flex: 1;
    min-width: 0;
    height: 44px;
    margin: 0;
    font-size: 14px;
    font-weight: 500;
    border-radius: var(--radius-lg);
    border: 1px solid var(--border-color-strong);
    background: var(--glass-bg-input);
    color: var(--fill-grey-fg);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;

    &:hover {
      background: rgba(255, 255, 255, 0.86);
      border-color: rgba(60, 60, 67, 0.28);
      color: var(--text-color);
    }
  }
}

.default-account {
  margin-top: 22px;
  text-align: center;
  font-size: 12px;
  color: rgba(60, 60, 67, 0.5);
}

/* ---------- 窄屏：品牌栏收起 ---------- */
@media (max-width: 919px) {
  .brand-panel {
    display: none;
  }

  .compact-brand {
    display: flex;
  }
}
</style>
