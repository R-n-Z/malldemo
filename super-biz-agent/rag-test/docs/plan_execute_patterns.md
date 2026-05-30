# Plan-Execute Agent 设计模式

退货审核 Agent 从 ReAct → Plan-Execute → ReAct 的演进中沉淀的设计经验。

## 三种模式的适用边界

```
任务确定性
  │
  │ 高（步骤可枚举）         中（路径可预判但有分支）    低（完全开放探索）
  │
  ├── 简单指令式 Prompt ───── 退货审核 ───── Plan-Execute
  │   (pre_sales, chitchat)
  │
  └── ReAct ───── 客服对话 ───── 开放探索
      (post_sales 动态工具选择)
```

**关键决策树**：

```
是否需要动态 replan？
  ├── 否 → 步骤是否可枚举？
  │         ├── 是 → 简单指令式 prompt（在 prompt 里写步骤清单）
  │         └── 否 → ReAct（让 Agent 自主探索）
  └── 是 → 步骤间是否有短路逻辑？
            ├── 是 → Plan-Execute（Planner 管理短路）
            └── 否 → ReAct（Agent 自己管理步骤顺序）
```

## Plan-Execute 三 Agent 模式

### 核心架构

```
SupervisorAgent (编排器, recursionLimit=N)
  ├── Planner Agent (规划师)
  │     - 收集信息、分析现状
  │     - 出计划 (decision=PLAN, steps=[...])
  │     - 接受反馈、修正计划 (replan)
  │     - 最终判定 (decision=FINISH)
  │
  └── Executor Agent (执行器)
        - 只执行 Planner 指定的单一步骤
        - 成功/失败/空 三种状态返回
        - 严禁编造数据
```

### Prompt 设计模式

**Supervisor Prompt** — 三句话描述调度规则：

1. 首先调 Planner 出计划
2. Planner 有 steps → 调 Executor → 结果回传 Planner
3. Planner 输出 FINISH → 停止，输出报告

**Planner Prompt** — 包含四个阶段：

1. 信息收集：只调理解现状的工具
2. 制定计划：输出 decision=PLAN + steps JSON
3. 修正计划：收到反馈后判断是否短路/补充/FINISH
4. 最终判定：输出完整报告

**Executor Prompt** — 极简：

1. 只看 Planner 最新的 steps
2. 三种状态：SUCCESS / FAILED / EMPTY
3. 严禁编造

### 关键参数

| 参数 | 建议值 | 依据 |
|------|--------|------|
| recursionLimit | 5 | 正常 2-3 轮，留 2 轮容错 |
| 工具分配 | 全给 + prompt 约束 | 避免运行时工具不可用的边界情况 |

## 短路优化模式

Plan-Execute 的核心价值是支持执行路径的**动态压缩**。

### 短路触发器

| 触发条件 | 短路动作 | 节省 |
|----------|---------|------|
| strict_reject 命中 | 跳过 auto_approve + 时间检查 | 2-3 次 tool call + embedding API |
| rejectCount ≥ 3 | 跳过所有检查，直接 FINISH | 4-5 次 tool call |
| 信息已能判定 | 不再追加步骤 | 1-2 次 tool call |

### 实现方式

在 Planner prompt 中明确定义优先级和短路规则：

```
规则优先级（严格遵守）：
- strict_reject 优先于 auto_approve
- 如果 getRejectCount ≥ 3 → 直接 FINISH
- 如果命中 strict_reject → 后续计划跳过 auto_approve 和时间检查
```

## 何时用 ReAct 替代 Plan-Execute

以下情况 Plan-Execute 的复杂度不值得：

- **工具数 ≤ 3**：编排开销 > 直接推理
- **步骤无短路逻辑**：Plan-Execute 退化为顺序执行，无增量价值
- **单次调用延迟低**（< 100ms per tool call）：短路节省的收益不如编排的 token 开销
- **上下文窗口充裕**：如果 LLM 能轻松处理所有 tool outputs 而不丢失关键信息

### 退货审核为什么回到 ReAct

| 维度 | Plan-Execute | ReAct（当前） | 选择 ReAct 的理由 |
|------|-------------|--------------|-------------------|
| token 开销 | 3个Agent各有独立prompt，≈2000 tokens/轮 | 1个Agent，≈800 tokens/轮 | Plan-Execute 固定开销大 |
| 循环轮次 | 典型3轮(Plan→Exec→Plan→Exec→Plan→FINISH) | 典型3-5次工具调用 | 无显著差异 |
| 短路收益 | 生鲜场景跳2步 | ReAct prompt 也内置了短路规则 | 等效 |
| 维护复杂度 | 3个prompt需要协同 | 1个prompt | ReAct 更简单 |
| 可调试性 | 需要追踪Supervisor的调度决策 | 直接看Agent的工具调用链 | ReAct 更直观 |

**结论**：当前退货审核的工具数和短路逻辑复杂度，用 ReAct prompt 内置规则就能达到相同效果。Plan-Execute 的增量价值在工具数 > 8 或需要真正并行执行时才显现。

## 计划 → ReAct 迁移清单

将 Plan-Execute prompt 的经验迁移到 ReAct prompt：

- [x] **优先级框架**：明确 strict_reject > auto_approve，从"执行步骤"变为"推理参考"
- [x] **短路规则**：从"Planner replan 跳过"变为"Agent 自判信息够了就停"
- [x] **推理示例**：用具体的 Observe→Decide→Act→Observe 示例替代 JSON 输出格式
- [x] **语义分析前置**：analyzeReturnText 作为信息收集阶段的工具，替代 Planner 的语义消歧
- [x] **硬门禁保留**：rejectCount ≥ 3 → 直接转人工，写在 prompt 的判定框架第一条
