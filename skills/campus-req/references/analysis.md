# 分析约定

analysis.json包含version、module、layer、requirements；每个条目id/source/acceptance从目录原样读取。项目已保存PDF来源，保留摘要、条目、页码，不反复复制整本任务书。

```json
{
  "version": 2,
  "module": "M02",
  "layer": "frontend",
  "requirements": [{
    "id": "RES-01",
    "source": {},
    "acceptance": [],
    "scenarios": [{
      "id": "RES-01.S01",
      "covers": [1],
      "preconditions": ["已确认的初态和数据来源"],
      "actions": ["从场景入口执行操作"],
      "expected": ["covers引用的可观察结果"],
      "depends_on": [],
      "objects": [{
        "kind": "page", "key": "/manage/rooms",
        "status": "existing", "evidence": "本模块routes.ts及相关观察位置"
      }],
      "cells": []
    }]
  }],
  "issues": []
}
```

示例的source和acceptance必须换成权威目录实际值。covers从1编号，合并覆盖必须完整。depends_on引用本分析稳定场景ID，必须无环；跨模块依赖另记公开契约和integration_dependencies。

对象kind可为page/component/api/table/constraint/task，status为existing/missing/unverified。证据具体到文件、接口观察或数据库查询。名称不能直接猜成选择器；采样缺口与待实现项分别记录。

重大歧义写影响、已查依据、候选和解除条件，记录实际用户回复来源。沿用现有最小department_code，不增加组织树。工具检查所有权、来源、覆盖、ID和依赖，AI仍需核对语义；测试阶段按campus-test补cells。

## 原文保留与参考格式的映射

按 [原文与场景映射](source-and-scenarios.md) 添加 source_snapshots、boundary、original_text、business_scene_refs 等分析信息。
这些扩展字段便于复用参考的原文 / 对象 / 问题流程，现有校验器不验证它们的真实性，须回查源文件。
objects 的 status=missing 相当于参考 components.md 的待实现行，existing / unverified 保留事实差异；
问题写入顶层 issues，Q / U / A 的类别与逐字依据沿用参考规则。

analysis 固定后不追加运行状态或修改对象状态，以免破坏 test-plan 摘要；实现结果关联原 key 写入私有 runs。
若需改需求解释或测试方案，先 reset 相应检查点，保留旧证据，再建立新 Red / Green 链路。
