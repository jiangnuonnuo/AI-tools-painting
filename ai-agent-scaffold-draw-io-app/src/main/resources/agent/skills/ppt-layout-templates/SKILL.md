---
name: ppt-layout-templates
description: PPT页面布局模板库。当AI需要生成PPT页面时，必须参考此技能选择合适的布局模板，确保页面结构丰富多样。每种布局有独特的装饰骨架，视觉风格差异化。包含8种专业布局：封面页、内容要点页、三列卡片页、左右对比页、时间轴页、金句页、数据高亮页、结尾页。
license: Apache-2.0
metadata:
  author: xfg-studio
  version: "2.0.0"
  category: ppt-design
---

# PPT 页面布局模板库 v2

## 概述

生成 PPT 时，必须根据内容语义选择最合适的 layout 模板。**每种布局有独立的装饰骨架**，视觉风格完全不同，避免千篇一律。

禁止所有页面都用同一种布局——一份 PPT 至少使用 3 种以上不同的 layout 类型。

## 画布规格

- 尺寸：13.33 × 7.5 英寸（16:9 宽屏）
- 所有坐标、尺寸单位为英寸
- 前端自动渲染装饰（色块、条纹、底栏），AI 只需输出文字和表格元素
- ⚠️ 绝对不要输出 kind=shape 的元素

---

## 模板一览（每种布局装饰骨架不同！）

| layout 值 | 用途 | 装饰骨架（前端自动渲染） |
|-----------|------|------------------------|
| `title_slide` | 封面/首页 | 上半部主色块(0~3.4) + accent条纹 + 底栏 + 装饰圆 |
| `content_slide` | 要点/列表 | **左侧宽色带**(x:0,w:4.5) + accent竖线 + 底栏 |
| `card_3col` | 三列并列 | **矮头栏**(0~0.9) + 三列圆角卡片(顶部主色) + 底栏 |
| `comparison` | 左右对比 | **左右对称色块**(左primary/右primaryMid) + 中间accent竖线 + VS圆 |
| `timeline` | 时间轴/流程 | **左上小色块**(0~5.0×1.2) + 水平轴线+节点 + 底栏，无统一头栏 |
| `quote_slide` | 金句/引言 | 上半部主色块(0~3.4) + accent条纹 + 底栏 + 装饰圆 |
| `data_highlight` | 大数字/KPI | **窄头栏**(0~0.7) + 指标卡片 + **底部宽色带**(5.5~7.5) |
| `end_slide` | 结尾/致谢 | 上半部主色块(0~3.4) + accent条纹 + 底栏 + 装饰圆 |

---

## 模板 A：封面页 `title_slide`

**装饰层**：上半部主色(y:0~3.4) + accent条纹(y:3.4) + 底栏(y:7.15~7.5) + 装饰圆

**AI 输出元素**：
```json
[
  {"kind":"text","content":"主标题","x":0.8,"y":0.8,"w":11.7,"h":2.0,"fontSize":44,"color":"FFFFFF","bold":true,"align":"center"},
  {"kind":"text","content":"副标题/日期","x":1.5,"y":4.0,"w":10.3,"h":1.2,"fontSize":22,"color":"666666","bold":false,"align":"center"},
  {"kind":"text","content":"演讲者 · 日期","x":1.5,"y":5.5,"w":10.3,"h":0.8,"fontSize":14,"color":"AAAAAA","bold":false,"align":"center"}
]
```

---

## 模板 B：内容要点页 `content_slide`

**装饰层**：左侧宽色带(x:0, w:4.5, primary色) + accent竖线(x:4.5) + 底栏(y:7.15~7.5)

**关键区别**：没有统一头栏！左侧色带贯穿全页，标题放在左侧色带上，正文放在右侧白色区域。

**AI 输出元素**：
```json
[
  {"kind":"text","content":"本页标题","x":0.5,"y":0.8,"w":3.5,"h":1.5,"fontSize":26,"color":"FFFFFF","bold":true,"align":"left"},
  {"kind":"text","content":"• 要点一\n• 要点二\n• 要点三","x":5.0,"y":1.0,"w":7.5,"h":5.5,"fontSize":16,"color":"333333","bold":false,"align":"left"}
]
```

**设计要点**：标题在左侧色带上 → color=FFFFFF，正文在右侧白色区域 → color=333333，左侧色带宽度4.5"，右侧内容 x ≥ 5.0。

---

## 模板 C：三列卡片 `card_3col`

**装饰层**：矮头栏(y:0~0.9) + accent条纹 + 三列圆角卡片(y:1.3~6.8, 顶部0.8高primary色块) + 底栏

**AI 输出元素**：
```json
[
  {"kind":"text","content":"页面标题","x":0.5,"y":0.1,"w":12.3,"h":0.7,"fontSize":24,"color":"FFFFFF","bold":true,"align":"left"},
  {"kind":"text","content":"卡片一标题","x":0.6,"y":1.4,"w":3.9,"h":0.6,"fontSize":16,"color":"FFFFFF","bold":true,"align":"center"},
  {"kind":"text","content":"卡片一描述","x":0.6,"y":2.3,"w":3.9,"h":4.0,"fontSize":13,"color":"666666","bold":false,"align":"center"},
  {"kind":"text","content":"卡片二标题","x":4.7,"y":1.4,"w":3.9,"h":0.6,"fontSize":16,"color":"FFFFFF","bold":true,"align":"center"},
  {"kind":"text","content":"卡片二描述","x":4.7,"y":2.3,"w":3.9,"h":4.0,"fontSize":13,"color":"666666","bold":false,"align":"center"},
  {"kind":"text","content":"卡片三标题","x":8.9,"y":1.4,"w":3.9,"h":0.6,"fontSize":16,"color":"FFFFFF","bold":true,"align":"center"},
  {"kind":"text","content":"卡片三描述","x":8.9,"y":2.3,"w":3.9,"h":4.0,"fontSize":13,"color":"666666","bold":false,"align":"center"}
]
```

---

## 模板 D：左右对比 `comparison`

**装饰层**：左侧primary色块(x:0, w:6.2) + 右侧primaryMid色块(x:7.13, w:6.2) + 中间accent竖线(x:6.55) + VS圆

**关键区别**：没有统一头栏！左右色块贯穿全页，白色卡片浮在色块上。

**AI 输出元素**：
```json
[
  {"kind":"text","content":"对比主题","x":0.8,"y":0.2,"w":5.0,"h":0.8,"fontSize":24,"color":"FFFFFF","bold":true,"align":"left"},
  {"kind":"text","content":"方案 A","x":0.8,"y":1.5,"w":4.6,"h":0.6,"fontSize":18,"color":"333333","bold":true,"align":"center"},
  {"kind":"text","content":"• 优势一\n• 优势二\n• 优势三","x":0.8,"y":2.3,"w":4.6,"h":4.0,"fontSize":14,"color":"333333","bold":false,"align":"left"},
  {"kind":"text","content":"方案 B","x":7.8,"y":1.5,"w":4.6,"h":0.6,"fontSize":18,"color":"333333","bold":true,"align":"center"},
  {"kind":"text","content":"• 优势一\n• 优势二\n• 优势三","x":7.8,"y":2.3,"w":4.6,"h":4.0,"fontSize":14,"color":"333333","bold":false,"align":"left"}
]
```

---

## 模板 E：时间轴 `timeline`

**装饰层**：左上小色块(x:0, w:5.0, h:1.2) + accent竖线(x:5.0) + 水平轴线(y:3.2) + 节点圆 + 底栏

**关键区别**：没有统一头栏！上方大面积留白给时间轴内容呼吸。

**AI 输出元素**（3个节点）：
```json
[
  {"kind":"text","content":"发展历程","x":0.5,"y":0.15,"w":4.2,"h":0.8,"fontSize":24,"color":"FFFFFF","bold":true,"align":"left"},
  {"kind":"text","content":"2023","x":1.0,"y":1.6,"w":3.0,"h":0.6,"fontSize":22,"color":"333333","bold":true,"align":"center"},
  {"kind":"text","content":"里程碑事件描述","x":1.0,"y":4.2,"w":3.0,"h":2.5,"fontSize":13,"color":"666666","bold":false,"align":"center"},
  {"kind":"text","content":"2024","x":5.0,"y":1.6,"w":3.0,"h":0.6,"fontSize":22,"color":"333333","bold":true,"align":"center"},
  {"kind":"text","content":"里程碑事件描述","x":5.0,"y":4.2,"w":3.0,"h":2.5,"fontSize":13,"color":"666666","bold":false,"align":"center"},
  {"kind":"text","content":"2025","x":9.0,"y":1.6,"w":3.0,"h":0.6,"fontSize":22,"color":"333333","bold":true,"align":"center"},
  {"kind":"text","content":"里程碑事件描述","x":9.0,"y":4.2,"w":3.0,"h":2.5,"fontSize":13,"color":"666666","bold":false,"align":"center"}
]
```

---

## 模板 F：金句页 `quote_slide`

**装饰层**：同 title_slide

**AI 输出元素**：
```json
[
  {"kind":"text","content":"关键引言或核心理念","x":1.5,"y":1.5,"w":10.3,"h":2.5,"fontSize":36,"color":"FFFFFF","bold":true,"align":"center"},
  {"kind":"text","content":"—— 出处 / 作者","x":1.5,"y":4.5,"w":10.3,"h":0.8,"fontSize":18,"color":"CCCCCC","bold":false,"align":"center"}
]
```

---

## 模板 G：数据高亮 `data_highlight`

**装饰层**：窄头栏(y:0~0.7) + accent条纹 + 三列指标卡片(y:1.2~5.0) + 底部宽色带(y:5.5~7.5) + 装饰圆

**AI 输出元素**（3个指标）：
```json
[
  {"kind":"text","content":"核心数据","x":0.5,"y":0.05,"w":12.3,"h":0.6,"fontSize":22,"color":"FFFFFF","bold":true,"align":"left"},
  {"kind":"text","content":"98.6%","x":0.8,"y":1.6,"w":3.6,"h":1.5,"fontSize":44,"color":"FFFFFF","bold":true,"align":"center"},
  {"kind":"text","content":"系统可用性","x":0.8,"y":3.2,"w":3.6,"h":0.6,"fontSize":14,"color":"666666","bold":false,"align":"center"},
  {"kind":"text","content":"1.2M","x":4.9,"y":1.6,"w":3.6,"h":1.5,"fontSize":44,"color":"FFFFFF","bold":true,"align":"center"},
  {"kind":"text","content":"日活跃用户","x":4.9,"y":3.2,"w":3.6,"h":0.6,"fontSize":14,"color":"666666","bold":false,"align":"center"},
  {"kind":"text","content":"50ms","x":8.9,"y":1.6,"w":3.6,"h":1.5,"fontSize":44,"color":"FFFFFF","bold":true,"align":"center"},
  {"kind":"text","content":"平均响应时间","x":8.9,"y":3.2,"w":3.6,"h":0.6,"fontSize":14,"color":"666666","bold":false,"align":"center"}
]
```

---

## 模板 H：结尾页 `end_slide`

**装饰层**：同 title_slide

**AI 输出元素**：
```json
[
  {"kind":"text","content":"感谢聆听","x":0.8,"y":0.8,"w":11.7,"h":2.0,"fontSize":48,"color":"FFFFFF","bold":true,"align":"center"},
  {"kind":"text","content":"联系方式","x":1.5,"y":4.2,"w":10.3,"h":1.0,"fontSize":16,"color":"666666","bold":false,"align":"center"}
]
```

---

## 关键约束

1. **多样性**：一份5页以上的PPT，至少使用3种不同的layout
2. **不用shape**：所有装饰由前端渲染，AI只输出text和table
3. **颜色规则**（按装饰骨架区分）：
   - 全幅色块区（title/quote/end封面部分） → color=FFFFFF
   - content_slide 左侧色带(x<4.5) → color=FFFFFF；右侧区域(x≥5.0) → color=333333
   - card_3col 头栏 → color=FFFFFF；卡片标题(在primary色块上) → color=FFFFFF；描述 → color=666666
   - comparison 顶部标题(在色块上) → color=FFFFFF；卡片内容(白色卡片上) → color=333333
   - timeline 标题(小色块上) → color=FFFFFF；节点内容 → color=333333/666666
   - data_highlight 头栏 → color=FFFFFF；大数字 → color=FFFFFF(前端覆盖为primary)；说明 → color=666666
4. **坐标安全**：x+w ≤ 13.33，y+h ≤ 7.5，元素不重叠
5. **字号规范**：封面标题40-48，内容页标题24-30，正文13-18，大数字36-52
