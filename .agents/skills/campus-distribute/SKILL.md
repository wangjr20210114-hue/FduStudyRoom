---
name: campus-distribute
description: FduStudyRoom 协调人在 IDE 中重新打包或核验六个成员的入组技能包，复用已经登记的个人名额。
---

# 分发六个成员包

## 执行条件

协调人要求生成、更新或检查个人包，并可访问本机已有分发资料。普通成员在自己的包中使用 campus-join。

## 输入

定位包含 tools/onboarding/pack.py 和 docs/22-team-onboarding.md 的协调人基线。在本机已有协调人工作树时优先复用。Windows 当前配置使用用户 LocalAppData 下 FduStudyRoom/team-onboarding-20261119 的 source.bundle、slots.local，分发目录为桌面 FduStudyRoom-六人入组。其他机器使用协调人指定的既有私有目录。

## 执行过程

1. **Stage 1 核对来源。** 读取项目规则、模块清单和冻结提交，核对源码 bundle 的 HEAD。用程序检查六个名额与已有个人包一致，输出代号和检查结果即可。已有登记缺少原 slots.local 时，先定位原资料。
2. **Stage 2 生成。** 用户要求生成或更新时，由技能用 Python 调用 tools/onboarding/pack.py，依次传入源码 bundle、私有目录和分发目录。每个包只携带本人的凭据，输出还包括组长分发说明。
3. **Stage 3 验证。** 对每个 ZIP 检查 CRC 和入口文件，解压到独立临时目录并调用随包的 .agents/skills/campus-join/scripts/join.py status。核对 a–f 分别得到 M01–M06。只要求检查时从现有包执行验证。

## 输出

分发文件夹、六个代号与包名、检查结果，以及可直接转发给成员的一句 IDE 调用示例。由协调人把各包分别私发给对应成员。

## 完成条件

六个检测结果与模块清单一致，原有访问码一致，成员包可独立打开。公开代码交付仅包含生成工具、技能及说明。
