<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { http } from '@/api/http'

const loading = ref(false)
const errorMessage = ref('')
const logs = ref<any[]>([])
const current = ref(1)
const size = ref(10)
const total = ref(0)
const filters = ref({ operatorId: '', operation: '', businessType: '', result: '', startTime: '', endTime: '' })

// 审计查询条件全部交给后端组合，页面只维护筛选和分页状态。
async function loadLogs() {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await http.get('/operation-logs', {
      params: {
        page: current.value,
        size: size.value,
        operatorId: filters.value.operatorId || undefined,
        operation: filters.value.operation || undefined,
        businessType: filters.value.businessType || undefined,
        result: filters.value.result || undefined,
        startTime: filters.value.startTime || undefined,
        endTime: filters.value.endTime || undefined,
      },
    })
    logs.value = data.records || []
    total.value = data.total || 0
  } catch (error: any) {
    errorMessage.value = error.response?.status === 403
      ? '当前账号没有审计日志查询权限'
      : '操作日志加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  current.value = 1
  loadLogs()
}

function changePage(page: number) {
  current.value = page
  loadLogs()
}

onMounted(loadLogs)
</script>

<template>
  <main class="app-page">
    <header class="topbar">
      <div><p class="eyebrow">AUDIT CONSOLE</p><h1>操作日志</h1></div>
    </header>
    <section class="content-panel">
      <div class="toolbar">
        <el-input v-model="filters.operatorId" placeholder="操作人 ID" clearable />
        <el-input v-model="filters.operation" placeholder="操作编码，如 FINANCE_APPROVE" clearable />
        <el-input v-model="filters.businessType" placeholder="业务类型，如 EXPENSE_REPORT" clearable />
        <el-select v-model="filters.result" placeholder="结果" clearable>
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILURE" />
        </el-select>
        <el-date-picker v-model="filters.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="开始时间" clearable />
        <el-date-picker v-model="filters.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="结束时间" clearable />
        <el-button type="primary" @click="search">查询</el-button>
      </div>
      <el-alert v-if="errorMessage" :title="errorMessage" type="warning" :closable="false" show-icon />
      <el-table v-loading="loading" :data="logs" stripe>
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column prop="operatorId" label="操作人" width="90" />
        <el-table-column prop="operation" label="操作" width="160" />
        <el-table-column prop="businessId" label="业务单号" width="100" />
        <el-table-column prop="result" label="结果" width="90" />
        <el-table-column prop="requestId" label="请求 ID" min-width="220" show-overflow-tooltip />
        <el-table-column prop="requestPath" label="请求路径" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column prop="errorMessage" label="错误信息" />
      </el-table>
      <el-empty v-if="!loading && !errorMessage && logs.length === 0" description="暂无日志" />
      <el-pagination v-model:current-page="current" v-model:page-size="size"
        layout="total, prev, pager, next" :total="total" @current-change="changePage" />
    </section>
  </main>
</template>
