import request from '@/utils/requestUtil'

export function generateConfirmOrder(data) {
	return request({
		method: 'POST',
		url: '/order/generateConfirmOrder',
		data: data
	})
}

export function generateOrder(data) {
	return request({
		method: 'POST',
		url: '/order/generateOrder',
		data: data
	})
}

export function fetchOrderList(params) {
	return request({
		method: 'GET',
		url: '/order/list',
		params: params
	})
}

export function payOrderSuccess(params) {
	return request({
		method: 'POST',
		url: '/order/paySuccess',
		params: params
	})
}

export function fetchOrderDetail(orderId) {
	return request({
		method: 'GET',
		url: `/order/detail/${orderId}`
	})
}

export function cancelUserOrder(params) {
	return request({
		method: 'POST',
		url: '/order/cancelUserOrder',
		params: params
	})
}

export function confirmReceiveOrder(params) {
	return request({
		method: 'POST',
		url: '/order/confirmReceiveOrder',
		params: params
	})
}

export function deleteUserOrder(params) {
	return request({
		method: 'POST',
		url: '/order/deleteOrder',
		params: params
	})
}

export function fetchAliapyStatus(params) {
	return request({
		method: 'GET',
		url: '/alipay/query',
		params: params
	})
}