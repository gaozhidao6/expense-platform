<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const errorMessage = ref('')
const keyword = ref('')
const current = ref(1)
const size = ref(10)
const total = ref(0)
const users = ref<any[]>([])
const loginUser = computed(() => auth.user)

// 用户查询只向后端传分页和关键字，ADMIN 权限由后端再次校验。
async function loadUsers() {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await http.get('/users', {
      params: { page: current.value, size: size.value, keyword: keyword.value || undefined },
    })
    users.value = data.records
    total.value = data.total
  } catch (error: any) {
    errorMessage.value = error.response?.status === 403
      ? '当前账号没有用户管理权限（需要 ADMIN）'
      : '用户列表加载失败'
  } finally {
    loading.value = false
  }
}

// 退出登录必须同时清理 Token 和本地用户快照。
function logout() {
  auth.clearSession()
  router.push('/login')
}

function changePage(page: number) {
  current.value = page
  loadUsers()
}

onMounted(loadUsers)
</script>

<template>
  <main class="app-page">
    <header class="topbar">
      <div>
        <p class="eyebrow">ADMIN CONSOLE</p>
        <h1>用户管理</h1>
      </div>
      <div class="session">
        <span>{{ loginUser?.username }} · {{ loginUser?.roles?.join(', ') }}</span>
        <el-button text @click="logout">退出</el-button>
      </div>
    </header>

    <section class="content-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索用户名、姓名或工号" clearable @keyup.enter="current = 1; loadUsers()" />
        <el-button type="primary" @click="current = 1; loadUsers()">查询</el-button>
      </div>
      <el-alert v-if="errorMessage" :title="errorMessage" type="warning" show-icon :closable="false" />
      <el-table v-loading="loading" :data="users" stripe>
        <el-table-column prop="employeeNo" label="工号" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="realName" label="姓名" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="status" label="状态">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !errorMessage && users.length === 0" description="暂无用户数据" />
      <el-pagination
        v-model:current-page="current"
        v-model:page-size="size"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="changePage"
      />
    </section>
  </main>
</template>
