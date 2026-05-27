<template>
	<view class="chat-page">
		<!-- 顶部 -->
		<view class="chat-header">
			<text class="back-btn yticon icon-zuojiantou-up" @click="navBack"></text>
			<view class="header-info">
				<text class="title">AI客服</text>
				<text class="subtitle" v-if="productName">{{productName}}</text>
			</view>
		</view>

		<!-- 消息列表 -->
		<scroll-view class="msg-list" :scroll-top="scrollTop" scroll-y @scrolltolower="onScroll">
			<view class="msg-item" v-for="(m, i) in messages" :key="i"
				:class="m.senderType === 1 ? 'msg-self' : 'msg-agent'">
				<view class="msg-bubble" :class="{ 'recalled': m.status === 2 }"
					v-if="m.senderType === 1" @longpress="onLongPress(m)">
					{{m.status === 2 ? '消息已撤回' : m.content}}
				</view>
				<view class="msg-bubble agent-bubble" :class="{ 'recalled': m.status === 2 }"
					v-else @longpress="onLongPress(m)">
					{{m.status === 2 ? '消息已撤回' : m.content}}
				</view>
				<text class="msg-time">{{m.senderType === 2 ? 'AI客服' : '我'}} · {{m.createTime}}</text>
			</view>
			<view class="loading-msg" v-if="waiting">
				<text>AI客服正在思考...</text>
			</view>
		</scroll-view>

		<!-- 快捷问题 -->
		<view class="quick-questions" v-if="messages.length === 0">
			<text class="qq-title">常见问题</text>
			<view class="qq-item" v-for="(q, i) in quickQuestions" :key="i" @click="sendQuick(q)">
				{{q}}
			</view>
		</view>

		<!-- 输入区 -->
		<view class="input-area">
			<input class="msg-input" v-model="inputText" placeholder="输入您的问题..."
				@confirm="sendMsg" confirm-type="send" />
			<button class="send-btn" @click="sendMsg" :disabled="!inputText.trim()">发送</button>
		</view>
	</view>
</template>

<script>
	import { createChatSession, getChatMessages, sendChatMessage, recallChatMessage } from '@/api/chat.js';

	export default {
		data() {
			return {
				productId: 0,
				productName: '',
				productPic: '',
				sessionId: null,
				messages: [],
				inputText: '',
				waiting: false,
				scrollTop: 0,
				pollTimer: null,
				quickQuestions: [
					'这个商品有什么优惠吗？',
					'支持哪些支付方式？',
					'发货需要多长时间？',
					'可以退换货吗？',
					'如何联系人工客服？'
				]
			}
		},
		onLoad(options) {
			this.productId = options.productId || 0;
			this.productName = decodeURIComponent(options.productName || '');
			this.productPic = decodeURIComponent(options.productPic || '');
			this.initSession();
		},
		onUnload() {
			if (this.pollTimer) clearInterval(this.pollTimer);
		},
		methods: {
			navBack() {
				uni.navigateBack();
			},
			async initSession() {
				try {
					const res = await createChatSession({
						productId: this.productId,
						productName: this.productName,
						productPic: this.productPic
					});
					if (res.code === 200) {
						this.sessionId = res.data.id;
						await this.loadMessages();
					}
				} catch (e) {
					uni.showToast({ title: '会话创建失败', icon: 'none' });
				}
				// 轮询新消息
				this.pollTimer = setInterval(() => {
					this.loadMessages(true);
				}, 2000);
			},
			async loadMessages(silent) {
				if (!this.sessionId) return;
				try {
					const res = await getChatMessages(this.sessionId);
					if (res.code === 200 && res.data) {
						this.messages = res.data;
						if (!silent) this.scrollToBottom();
					}
				} catch (e) {}
			},
			async sendMsg() {
				const text = this.inputText.trim();
				if (!text || !this.sessionId) return;
				this.inputText = '';
				this.waiting = true;
				try {
					await sendChatMessage({
						sessionId: this.sessionId,
						content: text
					});
					await this.loadMessages();
				} catch (e) {
					uni.showToast({ title: '发送失败', icon: 'none' });
				}
				this.waiting = false;
				this.scrollToBottom();
			},
			sendQuick(q) {
				this.inputText = q;
				this.sendMsg();
			},
			scrollToBottom() {
				this.$nextTick(() => {
					this.scrollTop = 99999;
				});
			},
			onScroll() {},
			onLongPress(msg) {
				if (msg.status === 2) return; // 已撤回的消息不再操作
				const items = ['复制'];
				if (msg.senderType === 1) items.push('撤回');
				uni.showActionSheet({
					itemList: items,
					success: async (res) => {
						if (items[res.tapIndex] === '复制') {
							uni.setClipboardData({ data: msg.content });
							uni.showToast({ title: '已复制', icon: 'none' });
						} else if (items[res.tapIndex] === '撤回') {
							try {
								const resp = await recallChatMessage(msg.id);
								if (resp.code === 200) {
									await this.loadMessages();
								} else {
									uni.showToast({ title: resp.message || '撤回失败', icon: 'none' });
								}
							} catch (e) {
								uni.showToast({ title: '撤回失败', icon: 'none' });
							}
						}
					}
				});
			}
		}
	}
</script>

<style lang="scss">
	.chat-page {
		display: flex;
		flex-direction: column;
		height: 100vh;
		background: #f5f5f5;
	}

	.chat-header {
		display: flex;
		align-items: center;
		padding: 20upx 30upx;
		padding-top: calc(var(--status-bar-height) + 20upx);
		background: #fff;
		border-bottom: 1px solid #eee;

		.back-btn { font-size: 40upx; margin-right: 20upx; color: #333; }
		.header-info { display: flex; flex-direction: column; }
		.title { font-size: 32upx; font-weight: bold; color: #333; }
		.subtitle { font-size: 24upx; color: #999; margin-top: 4upx; }
	}

	.msg-list {
		flex: 1;
		padding: 20upx 30upx;
		overflow-y: auto;
	}

	.msg-item {
		margin-bottom: 30upx;
		&.msg-self { display: flex; flex-direction: column; align-items: flex-end; }
		&.msg-agent { display: flex; flex-direction: column; align-items: flex-start; }
	}

	.msg-bubble {
		max-width: 80%;
		padding: 20upx 24upx;
		border-radius: 16upx;
		font-size: 28upx;
		line-height: 1.6;
		word-break: break-all;
		background: #fff;
		color: #333;
		border: 1px solid #e0e0e0;

		.msg-self & { background: #007aff; color: #fff; border: none; }
			&.recalled { background: #e0e0e0 !important; color: #999 !important; font-style: italic; border: none !important; }
	}

	.msg-time {
		font-size: 22upx;
		color: #999;
		margin-top: 8upx;
	}

	.loading-msg {
		text-align: center;
		padding: 20upx;
		color: #999;
		font-size: 24upx;
	}

	.quick-questions {
		padding: 20upx 30upx;
		.qq-title { font-size: 26upx; color: #999; margin-bottom: 16upx; }
		.qq-item {
			display: inline-block;
			padding: 14upx 24upx;
			margin: 0 16upx 16upx 0;
			background: #fff;
			border: 1px solid #007aff;
			border-radius: 30upx;
			font-size: 26upx;
			color: #007aff;
		}
	}

	.input-area {
		display: flex;
		align-items: center;
		padding: 16upx 20upx;
		padding-bottom: calc(16upx + env(safe-area-inset-bottom));
		background: #fff;
		border-top: 1px solid #eee;

		.msg-input {
			flex: 1;
			height: 70upx;
			padding: 0 20upx;
			background: #f5f5f5;
			border-radius: 35upx;
			font-size: 28upx;
		}
		.send-btn {
			margin-left: 16upx;
			width: 120upx;
			height: 70upx;
			line-height: 70upx;
			background: #007aff;
			color: #fff;
			font-size: 28upx;
			border-radius: 35upx;
			text-align: center;
			padding: 0;
		}
	}
</style>
