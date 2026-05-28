<template>
	<view>
		<view class="status-section">
			<image :src="orderStatus.image" class="icon" />
			<text class="label-text">{{orderStatus.text}}</text>
		</view>
		<view class="address-section">
			<view class="order-content">
				<text class="yticon icon-shouhuodizhi"></text>
				<view class="cen">
					<view class="top">
						<text class="name">{{order.receiverName}}</text>
						<text class="mobile">{{order.receiverPhone}}</text>
					</view>
					<text class="address">{{order.receiverProvince}} {{order.receiverCity}} {{order.receiverRegion}} {{order.receiverDetailAddress}}</text>
				</view>
			</view>
			<image class="a-bg" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAu4AAAAFCAYAAAAaAWmiAAAAGXRFWHRTb2Z0d2FyZQBBZG9iZSBJbWFnZVJlYWR5ccllPAAAAyJpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADw/eHBhY2tldCBiZWdpbj0i77u/IiBpZD0iVzVNME1wQ2VoaUh6cmVTek5UY3prYzlkIj8+IDx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IkFkb2JlIFhNUCBDb3JlIDUuMy1jMDExIDY2LjE0NTY2MSwgMjAxMi8wMi8wNi0xNDo1NjoyNyAgICAgICAgIj4gPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4gPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIiB4bWxuczp4bXBNTT0iaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wL21tLyIgeG1sbnM6c3RSZWY9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9zVHlwZS9SZXNvdXJjZVJlZiMiIHhtcDpDcmVhdG9yVG9vbD0iQWRvYmUgUGhvdG9zaG9wIENTNiAoV2luZG93cykiIHhtcE1NOkluc3RhbmNlSUQ9InhtcC5paWQ6Rjk3RjkzMjM2NzMxMTFFOUI4RkU4OEZGMDcxQzgzOEYiIHhtcE1NOkRvY3VtZW50SUQ9InhtcC5kaWQ6Rjk3RjkzMjQ2NzMxMTFFOUI4RkU4OEZGMDcxQzgzOEYiPiA8eG1wTU06RGVyaXZlZEZyb20gc3RSZWY6aW5zdGFuY2VJRD0ieG1wLmlpZDpGOTdGOTMyMTY3MzExMUU5QjhGRTg4RkYwNzFDODM4RiIgc3RSZWY6ZG9jdW1lbnRJRD0ieG1wLmRpZDpGOTdGOTMyMjY3MzExMUU5QjhGRTg4RkYwNzFDODM4RiIvPiA8L3JkZjpEZXNjcmlwdGlvbj4gPC9yZGY6UkRGPiA8L3g6eG1wbWV0YT4gPD94cGFja2V0IGVuZD0iciI/PrEOZlQAAAiuSURBVHjazJp7bFvV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"></image>
		</view>

		<view class="goods-section">
			<view class="g-header b-b">
				<text class="name">商品信息</text>
			</view>
			<view class="g-item" v-for="item in order.orderItemList" :key="item.id">
				<image :src="item.productPic"></image>
				<view class="right">
					<text class="title clamp">{{item.productName}}</text>
					<text class="spec">{{item.productAttr | formatProductAttr}}</text>
					<text class="promotion clamp">{{item.promotionName}}</text>
					<view class="price-box">
						<text class="price">￥{{item.productPrice}}</text>
						<text class="number">x {{item.productQuantity}}</text>
					</view>
				</view>
			</view>
		</view>

		<view class="yt-list">
			<view class="yt-list-cell b-b"><text class="cell-tit clamp">商品合计</text><text class="cell-tip">￥{{order.totalAmount}}</text></view>
			<view class="yt-list-cell b-b"><text class="cell-tit clamp">运费</text><text class="cell-tip">￥{{order.freightAmount}}</text></view>
			<view class="yt-list-cell b-b"><text class="cell-tit clamp">活动优惠</text><text class="cell-tip red">-￥{{order.promotionAmount}}</text></view>
			<view class="yt-list-cell b-b"><text class="cell-tit clamp">优惠券</text><text class="cell-tip red">-￥{{order.couponAmount}}</text></view>
			<view class="yt-list-cell b-b"><text class="cell-tit clamp">积分抵扣</text><text class="cell-tip red">-￥{{order.integrationAmount}}</text></view>
			<view class="yt-list-cell desc-cell"><text class="cell-tit clamp">备注</text><text class="cell-tip">{{order.note}}</text></view>
		</view>

		<view class="yt-list" v-if="returnApply">
			<view class="return-status-bar" :class="returnStatusClass">
				<text class="return-status-text">{{returnApply.status | formatReturnStatus}}</text>
				<text class="return-status-hint" v-if="returnApply.status === 3 && returnApply.handleNote">拒绝原因：{{returnApply.handleNote}}</text>
			</view>
		</view>

		<view class="yt-list">
			<view class="yt-list-cell b-b"><text class="cell-tit clamp">订单编号</text><text class="cell-tip">{{order.orderSn}}</text></view>
			<view class="yt-list-cell b-b"><text class="cell-tit clamp">提交时间</text><text class="cell-tip">{{order.createTime | formatDateTime}}</text></view>
			<view class="yt-list-cell b-b"><text class="cell-tit clamp">支付方式</text><text class="cell-tip">{{order.payType | formatPayType}}</text></view>
			<view class="yt-list-cell b-b" v-if="order.status==1||order.status==2||order.status==3"><text class="cell-tit clamp">实付金额</text><text class="cell-tip">￥{{order.payAmount}}</text></view>
			<view class="yt-list-cell b-b" v-if="order.status==1||order.status==2||order.status==3"><text class="cell-tit clamp">付款时间</text><text class="cell-tip">{{order.paymentTime | formatDateTime}}</text></view>
		</view>

		<view class="footer" v-if="order.status!=4">
			<view class="action-box b-t" v-if="order.status==0">
				<button class="action-btn" @click="handleReturnApply">申请售后</button>
				<button class="action-btn" @click="cancelOrder(order.id)">取消订单</button>
				<button class="action-btn recom" @click="payOrder(order.id)">立即付款</button>
			</view>
			<view class="action-box b-t" v-if="order.status==1">
				<button class="action-btn" @click="handleReturnApply">申请售后</button>
			</view>
			<view class="action-box b-t" v-if="order.status == 2">
				<button class="action-btn" @click="handleReturnApply">申请售后</button>
				<button class="action-btn">查看物流</button>
				<button class="action-btn recom" @click="receiveOrder(order.id)">确认收货</button>
			</view>
			<view class="action-box b-t" v-if="order.status == 3">
				<button class="action-btn" @click="handleReturnApply">申请售后</button>
				<button class="action-btn recom">评价商品</button>
			</view>
			<view class="price-content" v-if="order.status==0">
				<text>应付金额</text><text class="price-tip">￥</text><text class="price">{{order.payAmount}}</text>
			</view>
		</view>
	</view>
</template>

<script>
	import { fetchOrderDetail, cancelUserOrder, confirmReceiveOrder, fetchReturnApplyList } from '@/api/order.js';
	import { formatDate } from '@/utils/date';

	export default {
		data() {
			return {
				orderId: null,
				order: {},
				orderStatus: {},
				returnApply: null
			}
		},
		computed: {
			returnStatusClass() {
				if (!this.returnApply) return '';
				const s = this.returnApply.status;
				if (s === 0) return 'status-pending';
				if (s === 1) return 'status-processing';
				if (s === 2) return 'status-done';
				if (s === 3) return 'status-rejected';
				return '';
			}
		},
		onLoad(option) {
			this.orderId = option.orderId;
			this.loadData();
		},
		filters: {
			formatProductAttr(jsonAttr) {
				let attrArr = JSON.parse(jsonAttr);
				let attrStr = '';
				for (let attr of attrArr) { attrStr += attr.key + ":" + attr.value + ";"; }
				return attrStr
			},
			formatDateTime(time) {
				if (time == null || time === '') return 'N/A';
				return formatDate(new Date(time), 'yyyy-MM-dd hh:mm:ss')
			},
			formatPayType(payType) {
				if (payType == 0) return "未支付";
				else if (payType == 1) return "支付宝支付";
				else if (payType == 2) return "微信支付";
				return null;
			},
			formatReturnStatus(status) {
				const map = { 0: '待处理', 1: '退货中', 2: '已完成', 3: '已拒绝' };
				return map[status] || '未知';
			}
		},
		methods: {
			async loadData() {
				fetchOrderDetail(this.orderId).then(response => {
					this.order = response.data;
					this.setOrderStatus(this.order.status);
					this.loadReturnStatus();
				});
			},
			async loadReturnStatus() {
				try {
					const res = await fetchReturnApplyList({ pageNum: 1, pageSize: 10 });
					const list = res.data && res.data.list ? res.data.list : [];
					this.returnApply = list.find(item => item.orderId == this.orderId) || null;
				} catch (e) {}
			},
			handleReturnApply() {
				uni.navigateTo({ url: `/pages/order/returnApply?orderId=${this.orderId}` });
			},
			submit() {},
			stopPrevent() {},
			cancelOrder(orderId) {
				let self = this;
				uni.showModal({
					title: '提示', content: '是否要取消该订单？',
					success: function(res) {
						if (res.confirm) {
							uni.showLoading({ title: '请稍后' })
							cancelUserOrder({ orderId: orderId }).then(() => {
								uni.hideLoading();
								self.loadData();
							});
						}
					}
				});
			},
			payOrder(orderId) {
				uni.redirectTo({ url: `/pages/money/pay?orderId=${orderId}` });
			},
			receiveOrder(orderId) {
				let self = this;
				uni.showModal({
					title: '提示', content: '是否要确认收货？',
					success: function(res) {
						if (res.confirm) {
							uni.showLoading({ title: '请稍后' })
							confirmReceiveOrder({ orderId: orderId }).then(() => {
								uni.hideLoading();
								self.loadData();
							});
						}
					}
				});
			},
			setOrderStatus(status) {
				switch (status) {
					case 0: this.orderStatus = { text: '等待付款', image: '/static/icon_wait.png' }; break;
					case 1: this.orderStatus = { text: '等待发货', image: '/static/icon_deliver.png' }; break;
					case 2: this.orderStatus = { text: '等待收货', image: '/static/icon_receive.png' }; break;
					case 3: this.orderStatus = { text: '交易完成', image: '/static/icon_finish.png' }; break;
					case 4: this.orderStatus = { text: '交易关闭', image: '/static/icon_close.png' }; break;
				}
			}
		}
	}
</script>

<style lang="scss">
	page { background: $page-color-base; padding-bottom: 100upx; }
	.status-section { height: 200upx; background-color: $base-color; display: flex; align-items: center; padding: 30upx; .icon { width: 48upx; height: 48upx; } .label-text { color: #fff; margin-left: 30upx; } }
	.address-section { padding: 30upx 0; background: #fff; position: relative; .order-content { display: flex; align-items: center; } .icon-shouhuodizhi { flex-shrink: 0; display: flex; align-items: center; justify-content: center; width: 90upx; color: #888; font-size: 44upx; } .cen { display: flex; flex-direction: column; flex: 1; font-size: 28upx; color: $font-color-dark; } .name { font-size: 34upx; margin-right: 24upx; } .address { margin-top: 16upx; margin-right: 20upx; color: $font-color-light; } .a-bg { position: absolute; left: 0; bottom: 0; display: block; width: 100%; height: 5upx; } }
	.goods-section { margin-top: 16upx; background: #fff; padding-bottom: 1px; .g-header { display: flex; align-items: center; height: 84upx; padding: 0 30upx; position: relative; } .name { font-size: 30upx; color: $font-color-base; margin-left: 24upx; } .g-item { display: flex; margin: 20upx 30upx; image { flex-shrink: 0; display: block; width: 140upx; height: 140upx; border-radius: 4upx; } .right { flex: 1; padding-left: 24upx; overflow: hidden; } .title { font-size: 30upx; color: $font-color-dark; } .spec { font-size: 26upx; color: $font-color-light; } .promotion { font-size: 24upx; color: $base-color; } .price-box { display: flex; align-items: center; font-size: 32upx; color: $font-color-dark; padding-top: 10upx; .price { margin-bottom: 4upx; } .number { font-size: 26upx; color: $font-color-base; margin-left: 20upx; } } } }
	.yt-list { margin-top: 16upx; background: #fff; }
	.return-status-bar { padding: 20upx 30upx; display: flex; flex-direction: column; &.status-pending { background: #fdf6ec; .return-status-text { color: #e6a23c; } } &.status-processing { background: #ecf5ff; .return-status-text { color: #409eff; } } &.status-done { background: #f0f9eb; .return-status-text { color: #67c23a; } } &.status-rejected { background: #fef0f0; .return-status-text { color: #f56c6c; } } .return-status-text { font-size: 28upx; font-weight: 600; } .return-status-hint { font-size: 24upx; color: #999; margin-top: 8upx; } }
	.yt-list-cell { display: flex; align-items: center; padding: 10upx 30upx 10upx 40upx; line-height: 70upx; position: relative; &.b-b:after { left: 30upx; } .cell-tit { flex: 1; font-size: 26upx; color: $font-color-light; margin-right: 10upx; } .cell-tip { font-size: 26upx; color: $font-color-dark; &.red { color: $base-color; } } &.desc-cell { .cell-tit { max-width: 90upx; } } }
	.footer { position: fixed; left: 0; bottom: 0; z-index: 998; display: flex; align-items: center; width: 100%; height: 90upx; justify-content: space-between; font-size: 30upx; background-color: #fff; color: $font-color-base; box-shadow: 0 -1px 5px rgba(0, 0, 0, .1); .price-content { padding-left: 30upx; } .price-tip { color: $base-color; margin-left: 8upx; } .price { font-size: 36upx; color: $base-color; } }
	.action-box { display: flex; justify-content: flex-end; align-items: center; height: 100upx; position: relative; padding-right: 30upx; }
	.action-btn { width: 160upx; height: 60upx; margin: 0; margin-left: 24upx; padding: 0; text-align: center; line-height: 60upx; font-size: $font-sm + 2upx; color: $font-color-dark; background: #fff; border-radius: 100px; &:after { border-radius: 100px; } &.recom { background: #fff9f9; color: $base-color; &:after { border-color: #f7bcc8; } } }
</style>
