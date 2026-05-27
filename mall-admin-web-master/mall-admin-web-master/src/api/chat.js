import request from '@/utils/request'

export function getChatSessions() {
  return request({ url: '/chat/sessions', method: 'get' })
}

export function getChatMessages(sessionId) {
  return request({ url: '/chat/messages/' + sessionId, method: 'get' })
}

export function sendChatMessage(data) {
  return request({ url: '/chat/send', method: 'post', data: data })
}

export function takeChatSession(sessionId) {
  return request({ url: '/chat/session/take/' + sessionId, method: 'post' })
}

export function closeChatSession(sessionId) {
  return request({ url: '/chat/session/close/' + sessionId, method: 'post' })
}

export function recallChatMessage(messageId) {
  return request({ url: '/chat/recall/' + messageId, method: 'post' })
}
