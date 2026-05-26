<template>
  <div class="chat-container">
    <!-- 左侧会话列表 -->
    <div class="session-panel">
      <div class="panel-header">客服会话</div>
      <div class="session-list">
        <div v-for="s in sessions" :key="s.id"
          class="session-item" :class="{ active: currentSession && currentSession.id === s.id }"
          @click="openSession(s)">
          <div class="session-user">
            <span class="user-name">{{ s.member_name }}</span>
            <span class="session-time">{{ s.update_time || s.create_time }}</span>
          </div>
          <div class="session-product" v-if="s.product_name">{{ s.product_name }}</div>
          <div class="session-last">{{ s.last_message || '暂无消息' }}</div>
        </div>
        <div v-if="sessions.length === 0" class="empty">暂无会话</div>
      </div>
    </div>

    <!-- 右侧聊天窗口 -->
    <div class="chat-panel">
      <div v-if="!currentSession" class="no-session">请选择一个会话</div>
      <template v-else>
        <div class="chat-header">
          <span>{{ currentSession.member_name }}</span>
          <span class="product-tag" v-if="currentSession.product_name">{{ currentSession.product_name }}</span>
          <div class="header-actions">
            <el-button v-if="!currentSession.admin_id" size="mini" type="success" @click="takeSession">接单</el-button>
            <el-button size="mini" type="danger" @click="closeCurrentSession">关闭</el-button>
          </div>
        </div>
        <div class="chat-messages" ref="msgBox">
          <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.senderType === 1 ? 'msg-user' : 'msg-admin'">
            <div class="msg-bubble">{{ m.content }}</div>
            <div class="msg-info">{{ m.sender_name }} · {{ m.create_time }}</div>
          </div>
        </div>
        <div class="chat-input">
          <el-input v-model="inputText" placeholder="输入回复..." @keyup.enter.native="sendMsg" />
          <el-button type="primary" @click="sendMsg" :disabled="!inputText.trim()">发送</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
import { getChatSessions, getChatMessages, sendChatMessage, takeChatSession, closeChatSession } from '@/api/chat'

export default {
  data() {
    return {
      sessions: [],
      currentSession: null,
      messages: [],
      inputText: '',
      pollTimer: null
    }
  },
  mounted() {
    this.loadSessions()
    this.pollTimer = setInterval(() => {
      this.loadSessions()
      if (this.currentSession) this.loadMessages()
    }, 3000)
  },
  beforeDestroy() {
    if (this.pollTimer) clearInterval(this.pollTimer)
  },
  methods: {
    async loadSessions() {
      try {
        const res = await getChatSessions()
        if (res.code === 200) this.sessions = res.data || []
      } catch (e) {}
    },
    async openSession(s) {
      this.currentSession = s
      await this.loadMessages()
    },
    async loadMessages() {
      if (!this.currentSession) return
      try {
        const res = await getChatMessages(this.currentSession.id)
        if (res.code === 200) {
          this.messages = res.data || []
          this.$nextTick(() => {
            const box = this.$refs.msgBox
            if (box) box.scrollTop = box.scrollHeight
          })
        }
      } catch (e) {}
    },
    async sendMsg() {
      const text = this.inputText.trim()
      if (!text || !this.currentSession) return
      try {
        await sendChatMessage({ sessionId: this.currentSession.id, content: text })
        this.inputText = ''
        await this.loadMessages()
      } catch (e) {
        this.$message.error('发送失败')
      }
    },
    async takeSession() {
      try {
        await takeChatSession(this.currentSession.id)
        this.$message.success('已接单')
        await this.loadSessions()
      } catch (e) {}
    },
    async closeCurrentSession() {
      try {
        await closeChatSession(this.currentSession.id)
        this.$message.success('会话已关闭')
        this.currentSession = null
        this.messages = []
        await this.loadSessions()
      } catch (e) {}
    }
  }
}
</script>

<style scoped>
.chat-container { display: flex; height: calc(100vh - 100px); background: #fff; border-radius: 4px; overflow: hidden; }
.session-panel { width: 320px; border-right: 1px solid #eee; display: flex; flex-direction: column; }
.panel-header { padding: 16px 20px; font-size: 16px; font-weight: bold; border-bottom: 1px solid #eee; }
.session-list { flex: 1; overflow-y: auto; }
.session-item { padding: 14px 20px; border-bottom: 1px solid #f0f0f0; cursor: pointer; }
.session-item:hover { background: #f5f7fa; }
.session-item.active { background: #ecf5ff; }
.session-user { display: flex; justify-content: space-between; margin-bottom: 6px; }
.user-name { font-weight: bold; font-size: 14px; }
.session-time { font-size: 12px; color: #999; }
.session-product { font-size: 12px; color: #409EFF; margin-bottom: 4px; }
.session-last { font-size: 12px; color: #999; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty { text-align: center; color: #999; padding: 40px; }
.chat-panel { flex: 1; display: flex; flex-direction: column; }
.no-session { flex: 1; display: flex; align-items: center; justify-content: center; color: #999; font-size: 16px; }
.chat-header { padding: 12px 20px; border-bottom: 1px solid #eee; display: flex; align-items: center; gap: 10px; font-weight: bold; }
.product-tag { font-size: 12px; color: #409EFF; background: #ecf5ff; padding: 2px 8px; border-radius: 4px; font-weight: normal; }
.header-actions { margin-left: auto; display: flex; gap: 8px; }
.chat-messages { flex: 1; overflow-y: auto; padding: 20px; }
.msg { margin-bottom: 20px; }
.msg-user { text-align: right; }
.msg-user .msg-bubble { background: #409EFF; color: #fff; display: inline-block; }
.msg-admin .msg-bubble { background: #f0f0f0; color: #333; display: inline-block; }
.msg-bubble { max-width: 60%; padding: 10px 16px; border-radius: 8px; font-size: 14px; word-break: break-all; }
.msg-info { font-size: 12px; color: #999; margin-top: 4px; }
.chat-input { padding: 12px 20px; border-top: 1px solid #eee; display: flex; gap: 10px; }
</style>
