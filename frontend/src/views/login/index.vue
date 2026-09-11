<template>
  <div class="login-container">
    <section class="login-panel">
      <div class="brand-side">
        <div class="brand-badge">EDU</div>
        <h1>师生资源画像系统</h1>
        <p>面向学校管理者、教师与辅导员的资源画像与风险预警工作台</p>
        <div class="capabilities">
          <span>师生档案</span>
          <span>心理测评</span>
          <span>AI 预警</span>
          <span>数据分析</span>
        </div>
      </div>

      <div class="login-box">
        <div class="login-header">
          <h2 class="title">账号登录</h2>
          <p class="subtitle">请使用平台账号进入综合管理工作台</p>
        </div>

        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          class="login-form"
          @keyup.enter="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="用户名"
              size="large"
              :prefix-icon="User"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="密码"
              size="large"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <el-form-item>
            <el-checkbox v-model="loginForm.remember">记住我</el-checkbox>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="loading"
              @click="handleLogin"
            >
              登录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="login-footer">
          <span>演示账号</span>
          <strong>admin / admin</strong>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const loading = ref(false)
const loginFormRef = ref()

const loginForm = reactive({
  username: 'admin',
  password: 'admin',
  remember: false
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await userStore.loginAction(loginForm)
        ElMessage.success('登录成功')
      } catch (error) {
        ElMessage.error('登录失败，请检查用户名和密码')
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped lang="scss">
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, rgba(31, 95, 191, 0.08), rgba(31, 95, 191, 0)), #f4f7fb;
  padding: 32px;
}

.login-panel {
  width: min(960px, 100%);
  min-height: 560px;
  display: grid;
  grid-template-columns: 1.08fr 0.92fr;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.12);
}

.brand-side {
  padding: 56px;
  background:
    linear-gradient(180deg, rgba(16, 35, 63, 0.84), rgba(16, 35, 63, 0.94)),
    linear-gradient(135deg, #1f5fbf, #10233f);
  color: #fff;

  .brand-badge {
    width: 54px;
    height: 32px;
    margin-bottom: 64px;
    border: 1px solid rgba(255, 255, 255, 0.28);
    border-radius: 6px;
    color: rgba(255, 255, 255, 0.92);
    font-weight: 700;
    letter-spacing: 0;
    line-height: 30px;
    text-align: center;
  }

  h1 {
    margin: 0;
    font-size: 30px;
    font-weight: 700;
    letter-spacing: 0;
  }

  p {
    max-width: 430px;
    margin: 18px 0 0;
    color: rgba(255, 255, 255, 0.76);
    font-size: 15px;
    line-height: 1.8;
  }

  .capabilities {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-top: 56px;

    span {
      padding: 7px 12px;
      border: 1px solid rgba(255, 255, 255, 0.16);
      border-radius: 4px;
      background: rgba(255, 255, 255, 0.08);
      color: rgba(255, 255, 255, 0.86);
      font-size: 13px;
    }
  }
}

.login-box {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 56px 48px;
}

.login-header {
  text-align: left;
  margin-bottom: 32px;

  .title {
    margin: 0;
    font-size: 24px;
    font-weight: 700;
    color: var(--text-color);
    margin-bottom: 8px;
  }

  .subtitle {
    font-size: 14px;
    color: var(--text-color-secondary);
  }
}

.login-form {
  .login-btn {
    width: 100%;
    height: 42px;
  }
}

.login-footer {
  margin-top: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--bg-color-subtle);
  font-size: 12px;
  color: var(--text-color-secondary);

  strong {
    color: var(--text-color);
    font-weight: 600;
  }
}

@media (max-width: 820px) {
  .login-container {
    padding: 18px;
  }

  .login-panel {
    grid-template-columns: 1fr;
  }

  .brand-side {
    padding: 32px;

    .brand-badge {
      margin-bottom: 28px;
    }

    .capabilities {
      margin-top: 28px;
    }
  }

  .login-box {
    padding: 32px;
  }
}
</style>
