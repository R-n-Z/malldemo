import request from '@/utils/requestUtil'

export function createChatSession(data) {
	return request({
		method: 'POST',
		url: '/chat/session/create',
		data: data
	})
}

export function getChatMessages(sessionId) {
	return request({
		method: 'GET',
		url: '/chat/messages/' + sessionId
	})
}

export function sendChatMessage(data) {
	return request({
		method: 'POST',
		url: '/chat/send',
		data: data
	})
}

export function closeChatSession(sessionId) {
	return request({
		method: 'POST',
		url: '/chat/session/close/' + sessionId
	})
}
