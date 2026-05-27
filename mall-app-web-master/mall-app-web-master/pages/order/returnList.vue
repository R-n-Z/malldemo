<template>
	<view class="container">
		<view v-if="returnList.length === 0" class="empty">
			<text class="yticon icon-order" style="font-size:100upx;color:#ccc"></text>
			<text class="empty-text">暂无退货记录</text>
		</view>

		<view class="return-item" v-for="item in returnList" :key="item.id"
			@click="goToDetail(item.id)">
			<view class="item-header">
				<text class="order-sn">服务单号：{{item.id}}</text>
				<text class="status" :class="statusClass(item.status)">
					{{item.status | formatStatus}}
				</text>
			</view>
			<view class="item-body">
				<image :src="item.productPic" class="product-img" />
				<view class="product-info">
					<text class="product-name">{{item.productName}}</text>
					<text class="product-attr">{{item.productAttr}}</text>
					<text class="product-reason">原因：{{item.reason}}</text>
				</view>
			</view>
			<view class="item-footer">
				<text class="time">{{item.createTime | formatTime}}</text>
				<text class="amount">退款：￥{{item.productRealPrice * item.productCount}}</text>
			</view>
			<view v-if="item.status === 3 && item.handleNote" class="reject-note">
				<text>拒绝原因：{{item.handleNote}}</text>
			</view>
		</view>
	</view>
</template>

<script>
	import { fetchReturnApplyList } from '@/api/order.js';
	import { formatDate } from '@/utils/date';

	export default {
		data() {
			return {
				returnList: []
			}
		},
		filters: {
			formatStatus(status) {
				let map = { 0: '待处理', 1: '退货中', 2: '已完成', 3: '已拒绝' };
				return map[status] || '未知';
			},
			formatTime(time) {
				if (!time) return '';
				let date = new Date(time);
				return formatDate(date, 'yyyy-MM-dd hh:mm:ss');
			}
		},
		onLoad() {
			this.loadData();
		},
		methods: {
			async loadData() {
				try {
					let res = await fetchReturnApplyList({ pageNum: 1, pageSize: 50 });
					this.returnList = res.data && res.data.list ? res.data.list : [];
				} catch (e) {
					uni.showToast({ title: '加载失败', icon: 'none' });
				}
			},
			statusClass(status) {
				if (status === 0) return 'pending';
				if (status === 1) return 'processing';
				if (status === 2) return 'done';
				if (status === 3) return 'rejected';
				return '';
			},
			goToDetail(applyId) {
				// 跳转到退货详情（暂用订单详情作为中转）
				uni.showToast({ title: '退货详情：' + applyId, icon: 'none' });
			}
		}
	}
</script>

<style lang="scss">
	page {
		background: $page-color-base;
	}

	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding-top: 200upx;

		.empty-text {
			font-size: 28upx;
			color: #999;
			margin-top: 20upx;
		}
	}

	.return-item {
		background: #fff;
		margin: 16upx 20upx;
		border-radius: 12upx;
		padding: 20upx;
		overflow: hidden;

		.item-header {
			display: flex;
			justify-content: space-between;
			align-items: center;
			padding-bottom: 16upx;
			border-bottom: 1px solid #f5f5f5;

			.order-sn { font-size: 26upx; color: #666; }
			.status { font-size: 26upx; font-weight: 600; }
			.pending { color: #e6a23c; }
			.processing { color: #409eff; }
			.done { color: #67c23a; }
			.rejected { color: #f56c6c; }
		}

		.item-body {
			display: flex;
			padding: 16upx 0;

			.product-img {
				width: 100upx;
				height: 100upx;
				border-radius: 8upx;
			}

			.product-info {
				flex: 1;
				margin-left: 20upx;

				.product-name { font-size: 28upx; color: #333; }
				.product-attr { font-size: 24upx; color: #999; display: block; margin-top: 4upx; }
				.product-reason { font-size: 24upx; color: #666; margin-top: 6upx; }
			}
		}

		.item-footer {
			display: flex;
			justify-content: space-between;
			font-size: 24upx;
			color: #999;
			padding-top: 12upx;
			border-top: 1px solid #f5f5f5;
		}

		.reject-note {
			margin-top: 12upx;
			padding: 12upx;
			background: #fef0f0;
			border-radius: 6upx;
			font-size: 24upx;
			color: #f56c6c;
		}
	}
</style>
