import request from '@/utils/requestUtil'

export function memberLogin(data) {
	return request({
		method: 'POST',
		url: '/sso/login',
		data: data
	})
}

export function memberInfo() {
	return request({
		method: 'GET',
		url: '/sso/info'
	})
}
