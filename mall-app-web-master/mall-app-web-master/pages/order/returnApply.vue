<template>
	<view class="container">
		<!-- 步骤条 -->
		<view class="steps">
			<view class="step" :class="{ active: currentStep >= 1, done: currentStep > 1 }">
				<view class="step-num">1</view>
				<text>填写原因</text>
			</view>
			<view class="step-line" :class="{ active: currentStep > 1 }"></view>
			<view class="step" :class="{ active: currentStep >= 2, done: currentStep > 2 }">
				<view class="step-num">2</view>
				<text>补充描述</text>
			</view>
			<view class="step-line" :class="{ active: currentStep > 2 }"></view>
			<view class="step" :class="{ active: currentStep >= 3 }">
				<view class="step-num">3</view>
				<text>确认提交</text>
			</view>
		</view>

		<!-- 步骤1：选择退货原因和商品 -->
		<view v-if="currentStep === 1" class="step-content">
			<view class="card">
				<view class="card-title">退货商品</view>
				<view class="product-item" v-for="item in orderItems" :key="item.id"
					@click="toggleSelectItem(item)" :class="{ selected: selectedItems.includes(item.id) }">
					<view class="check-box">
						<text class="yticon icon-xuanzhong2" v-if="selectedItems.includes(item.id)"></text>
						<text class="yticon icon-round" v-else></text>
					</view>
					<image :src="item.productPic" class="product-img" />
					<view class="product-info">
						<text class="product-name">{{item.productName}}</text>
						<text class="product-attr">{{item.productAttr | formatAttr}}</text>
						<text class="product-price">￥{{item.productPrice}} x {{item.productQuantity}}</text>
					</view>
				</view>
			</view>

			<view class="card">
				<view class="card-title">退货原因</view>
				<view class="reason-list">
					<view class="reason-item" v-for="r in returnReasons" :key="r.id"
						@click="selectReason(r)" :class="{ selected: selectedReason && selectedReason.id === r.id }">
						<text>{{r.name}}</text>
						<text class="yticon icon-xuanzhong2" v-if="selectedReason && selectedReason.id === r.id"></text>
					</view>
				</view>
			</view>

			<button class="btn-primary" @click="nextStep" :disabled="!canProceedStep1">
				{{ canProceedStep1 ? '下一步' : '请选择退货原因' }}
			</button>
		</view>

		<!-- 步骤2：补充描述和上传凭证 -->
		<view v-if="currentStep === 2" class="step-content">
			<view class="card">
				<view class="card-title">问题描述</view>
				<textarea v-model="description" placeholder="请详细描述退货原因，便于我们快速审核..."
					maxlength="500" class="desc-input" />
				<text class="char-count">{{description.length}}/500</text>
			</view>

			<view class="card">
				<view class="card-title">上传凭证（选填）</view>
				<view class="upload-area">
					<view class="upload-item" v-for="(img, idx) in proofImages" :key="idx">
						<image :src="img" class="upload-img" />
						<text class="delete-btn" @click="removeImage(idx)">×</text>
					</view>
					<view class="upload-btn" @click="chooseImage" v-if="proofImages.length < 6">
						<text class="add-icon">+</text>
						<text class="upload-text">{{proofImages.length === 0 ? '上传图片' : proofImages.length + '/6'}}</text>
					</view>
				</view>
			</view>

			<view class="card">
				<view class="card-title">退款信息</view>
				<view class="info-row">
					<text class="info-label">退款金额</text>
					<text class="info-value red">￥{{refundAmount}}</text>
				</view>
				<view class="info-row">
					<text class="info-label">退货数量</text>
					<text class="info-value">{{selectedItems.length}} 件</text>
				</view>
			</view>

			<view class="btn-group">
				<button class="btn-secondary" @click="prevStep">上一步</button>
				<button class="btn-primary" @click="nextStep">下一步</button>
			</view>
		</view>

		<!-- 步骤3：确认提交 -->
		<view v-if="currentStep === 3" class="step-content">
			<view class="card">
				<view class="card-title">确认退货信息</view>
				<view class="confirm-section">
					<view class="confirm-row">
						<text class="label">订单编号</text>
						<text class="value">{{orderSn}}</text>
					</view>
					<view class="confirm-row">
						<text class="label">退货商品</text>
						<text class="value">{{selectedItems.length}} 件</text>
					</view>
					<view class="confirm-row">
						<text class="label">退货原因</text>
						<text class="value">{{selectedReason ? selectedReason.name : ''}}</text>
					</view>
					<view class="confirm-row">
						<text class="label">退款金额</text>
						<text class="value red">￥{{refundAmount}}</text>
					</view>
					<view class="confirm-row" v-if="description">
						<text class="label">问题描述</text>
						<text class="value">{{description}}</text>
					</view>
				</view>
			</view>

			<view class="notice">
				<text class="notice-title">温馨提示：</text>
				<text>1. 提交后将由AI智能审核，审核结果请留意消息通知</text>
				<text>2. 审核通过后请按指引寄回商品</text>
				<text>3. 生鲜、数码、内衣等特殊商品可能不支持退货</text>
			</view>

			<view class="btn-group">
				<button class="btn-secondary" @click="prevStep">上一步</button>
				<button class="btn-primary" :loading="submitting" @click="submitApply">确认提交</button>
			</view>
		</view>

		<!-- 提交成功 -->
		<view v-if="currentStep === 4" class="step-content">
			<view class="success-card">
				<text class="yticon icon-success" style="font-size:80upx;color:#4cd964"></text>
				<text class="success-title">退货申请已提交</text>
				<text class="success-desc">AI智能审核中，预计1-3分钟出结果</text>
				<text class="success-desc">审核结果将通过消息通知您</text>
				<button class="btn-primary" @click="goToOrderDetail">返回订单详情</button>
				<button class="btn-secondary" @click="goToReturnList">查看退货记录</button>
			</view>
		</view>
	</view>
</template>

<script>
	import { fetchOrderDetail, createReturnApply } from '@/api/order.js';

	export default {
		data() {
			return {
				orderId: null,
				orderSn: '',
				orderItems: [],
				currentStep: 1,
				selectedItems: [],
				selectedReason: null,
				returnReasons: [
					{ id: 1, name: '七天无理由退货' },
					{ id: 2, name: '商品质量问题' },
					{ id: 3, name: '商品与描述不符' },
					{ id: 4, name: '发错货/少件' },
					{ id: 5, name: '物流损坏' },
					{ id: 6, name: '不想要了' },
					{ id: 7, name: '其他原因' }
				],
				description: '',
				proofImages: [],
				submitting: false
			}
		},
		computed: {
			canProceedStep1() {
				return this.selectedItems.length > 0 && this.selectedReason !== null;
			},
			refundAmount() {
				let total = 0;
				for (let item of this.orderItems) {
					if (this.selectedItems.includes(item.id)) {
						total += item.productPrice * item.productQuantity;
					}
				}
				return total.toFixed(2);
			}
		},
		filters: {
			formatAttr(attr) {
				if (!attr) return '';
				try {
					let arr = JSON.parse(attr);
					return arr.map(a => a.key + ':' + a.value).join('; ');
				} catch (e) {
					return attr;
				}
			}
		},
		onLoad(option) {
			this.orderId = option.orderId;
			this.loadOrderDetail();
		},
		methods: {
			async loadOrderDetail() {
				try {
					let res = await fetchOrderDetail(this.orderId);
					let order = res.data;
					this.orderSn = order.orderSn;
					this.orderItems = order.orderItemList || [];
					// 自动选中全部商品
					this.selectedItems = this.orderItems.map(item => item.id);
				} catch (e) {
					uni.showToast({ title: '加载订单失败', icon: 'none' });
				}
			},
			toggleSelectItem(item) {
				let idx = this.selectedItems.indexOf(item.id);
				if (idx >= 0) {
					this.selectedItems.splice(idx, 1);
				} else {
					this.selectedItems.push(item.id);
				}
			},
			selectReason(reason) {
				this.selectedReason = reason;
			},
			nextStep() {
				if (this.currentStep === 1 && !this.canProceedStep1) return;
				this.currentStep++;
			},
			prevStep() {
				this.currentStep--;
			},
			chooseImage() {
				uni.chooseImage({
					count: 6 - this.proofImages.length,
					sizeType: ['compressed'],
					sourceType: ['album', 'camera'],
					success: (res) => {
						this.proofImages.push(...res.tempFilePaths);
					}
				});
			},
			removeImage(idx) {
				this.proofImages.splice(idx, 1);
			},
			async submitApply() {
				this.submitting = true;
				try {
					let selectedProduct = this.orderItems.find(
						item => this.selectedItems.includes(item.id)
					);
					if (!selectedProduct) {
						uni.showToast({ title: '请选择退货商品', icon: 'none' });
						this.submitting = false;
						return;
					}
					let param = {
						orderId: parseInt(this.orderId),
						productId: selectedProduct.productId,
						orderSn: this.orderSn,
						productPic: selectedProduct.productPic,
						productName: selectedProduct.productName,
						productBrand: selectedProduct.productBrand || '',
						productAttr: selectedProduct.productAttr || '',
						productCount: this.selectedItems.length,
						productPrice: selectedProduct.productPrice,
						productRealPrice: selectedProduct.productPrice,
						reason: this.selectedReason ? this.selectedReason.name : '',
						description: this.description,
						proofPics: this.proofImages.join(',')
					};
					await createReturnApply(param);
					this.submitting = false;
					this.currentStep = 4;
				} catch (e) {
					this.submitting = false;
					uni.showToast({ title: '提交失败，请重试', icon: 'none' });
				}
			},
			goToOrderDetail() {
				uni.redirectTo({
					url: `/pages/order/orderDetail?orderId=${this.orderId}`
				});
			},
			goToReturnList() {
				uni.redirectTo({
					url: '/pages/order/returnList'
				});
			}
		}
	}
</script>

<style lang="scss">
	page {
		background: $page-color-base;
	}

	.container {
		padding-bottom: 40upx;
	}

	.steps {
		display: flex;
		align-items: center;
		justify-content: center;
		background: #fff;
		padding: 30upx;
		margin-bottom: 20upx;

		.step {
			display: flex;
			flex-direction: column;
			align-items: center;
			font-size: 22upx;
			color: #ccc;

			&.active { color: $base-color; }
			&.done { color: #4cd964; }

			.step-num {
				width: 44upx;
				height: 44upx;
				border-radius: 50%;
				background: #eee;
				text-align: center;
				line-height: 44upx;
				font-size: 24upx;
				margin-bottom: 8upx;
				color: #999;
			}
			&.active .step-num {
				background: $base-color;
				color: #fff;
			}
			&.done .step-num {
				background: #4cd964;
				color: #fff;
			}
		}

		.step-line {
			width: 80upx;
			height: 2upx;
			background: #eee;
			margin: 0 12upx;
			margin-bottom: 30upx;

			&.active {
				background: $base-color;
			}
		}
	}

	.card {
		background: #fff;
		margin: 0 20upx 20upx;
		border-radius: 12upx;
		padding: 24upx;

		.card-title {
			font-size: 30upx;
			font-weight: 600;
			color: #333;
			margin-bottom: 20upx;
		}
	}

	.product-item {
		display: flex;
		align-items: center;
		padding: 16upx 0;
		border-bottom: 1px solid #f5f5f5;

		&.selected { background: #fff9f9; }

		.check-box {
			margin-right: 20upx;
			font-size: 40upx;
		}

		.icon-xuanzhong2 { color: $base-color; }
		.icon-round { color: #ccc; }

		.product-img {
			width: 100upx;
			height: 100upx;
			border-radius: 8upx;
		}

		.product-info {
			flex: 1;
			margin-left: 20upx;

			.product-name {
				font-size: 28upx;
				color: #333;
			}

			.product-attr {
				font-size: 24upx;
				color: #999;
				display: block;
				margin-top: 4upx;
			}

			.product-price {
				font-size: 28upx;
				color: $base-color;
				margin-top: 6upx;
			}
		}
	}

	.reason-list {
		.reason-item {
			display: flex;
			justify-content: space-between;
			align-items: center;
			padding: 24upx 0;
			border-bottom: 1px solid #f5f5f5;
			font-size: 28upx;
			color: #333;

			&.selected {
				color: $base-color;
				.icon-xuanzhong2 { font-size: 36upx; }
			}
		}
	}

	.desc-input {
		width: 100%;
		height: 200upx;
		font-size: 28upx;
		color: #333;
		padding: 16upx;
		background: #f8f8f8;
		border-radius: 8upx;
	}

	.char-count {
		text-align: right;
		font-size: 24upx;
		color: #999;
		margin-top: 8upx;
	}

	.upload-area {
		display: flex;
		flex-wrap: wrap;

		.upload-item {
			position: relative;
			margin: 0 16upx 16upx 0;

			.upload-img {
				width: 140upx;
				height: 140upx;
				border-radius: 8upx;
			}

			.delete-btn {
				position: absolute;
				top: -12upx;
				right: -12upx;
				width: 36upx;
				height: 36upx;
				background: rgba(0,0,0,.5);
				color: #fff;
				border-radius: 50%;
				text-align: center;
				line-height: 36upx;
				font-size: 28upx;
			}
		}

		.upload-btn {
			width: 140upx;
			height: 140upx;
			border: 2upx dashed #ddd;
			border-radius: 8upx;
			display: flex;
			flex-direction: column;
			align-items: center;
			justify-content: center;

			.add-icon {
				font-size: 48upx;
				color: #ccc;
			}

			.upload-text {
				font-size: 22upx;
				color: #999;
				margin-top: 8upx;
			}
		}
	}

	.info-row {
		display: flex;
		justify-content: space-between;
		padding: 16upx 0;
		font-size: 28upx;

		.info-label { color: #666; }
		.info-value { color: #333; }
		.red { color: $base-color; }
	}

	.confirm-section {
		.confirm-row {
			display: flex;
			padding: 16upx 0;
			border-bottom: 1px solid #f5f5f5;
			font-size: 26upx;

			.label {
				width: 150upx;
				color: #999;
			}

			.value {
				flex: 1;
				color: #333;
			}

			.red { color: $base-color; font-weight: 600; }
		}
	}

	.notice {
		background: #fff9f0;
		margin: 20upx;
		border-radius: 12upx;
		padding: 24upx;
		font-size: 24upx;
		color: #999;

		.notice-title {
			font-weight: 600;
			color: #e6a23c;
			display: block;
			margin-bottom: 8upx;
		}

		text { display: block; line-height: 1.8; }
	}

	.success-card {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 80upx 40upx;
		text-align: center;

		.success-title {
			font-size: 36upx;
			font-weight: 600;
			margin-top: 24upx;
		}

		.success-desc {
			font-size: 28upx;
			color: #999;
			margin-top: 16upx;
		}

		button {
			width: 500upx;
			margin-top: 30upx;
		}
	}

	.btn-primary {
		width: 90%;
		margin-top: 30upx;
		background: $base-color;
		color: #fff;
		border-radius: 80upx;
		height: 88upx;
		line-height: 88upx;
		font-size: 30upx;
	}

	.btn-secondary {
		width: 90%;
		margin-top: 20upx;
		background: #fff;
		color: #666;
		border: 1px solid #ddd;
		border-radius: 80upx;
		height: 88upx;
		line-height: 88upx;
		font-size: 30upx;
	}

	.btn-group {
		margin-top: 30upx;
		display: flex;
		flex-direction: column;
		align-items: center;

		.btn-secondary { margin-top: 12upx; }
	}
</style>
