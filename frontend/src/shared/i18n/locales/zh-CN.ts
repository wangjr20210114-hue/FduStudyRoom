export default {
  "app": {
    "title": "自习室预约",
    "name": "校园自习室",
    "headingLine1": "为下一段专注，",
    "headingLine2": "找到一个好位置。",
    "description": "查询校园自习空间，安排你的学习时段。"
  },
  "common": {
    "language": "语言",
    "home": "返回起始页",
    "homeLabel": "FduStudyRoom 起始页",
    "retry": "重新尝试",
    "checkInput": "检查输入"
  },
  "errors": {
    "unknown": "操作未完成，请稍后重试。",
    "network": "网络连接失败，请检查网络后重试。",
    "timeout": "请求超时，请稍后重试。",
    "cancelled": "请求已取消。",
    "invalidResponse": "服务返回的数据格式异常，请稍后重试。",
    "invalidRequest": "请求参数不正确，请检查后重试。",
    "http": "请求失败，请稍后重试。",
    "unauthenticated": "登录状态已失效，请重新登录。",
    "forbidden": "你没有执行此操作的权限。",
    "notFound": "请求的内容不存在。",
    "conflict": "数据状态已变化，请刷新后重试。",
    "validation": "提交的信息不符合要求，请检查输入。",
    "rateLimited": "操作过于频繁，请稍后再试。",
    "server": "服务暂时不可用，请稍后重试。"
  },
  "preview": {
    "skip": "跳到组件内容",
    "eyebrow": "公共样式与基础组件",
    "title": "清楚、一致的操作体验。",
    "description": "在这里检查按钮、输入与反馈的外观和交互。所有操作仅作用于本页预览。",
    "categories": "组件分类",
    "actions": {
      "title": "按钮与状态",
      "description": "操作层级清晰，加载与禁用状态明确。",
      "interactive": "可交互预览",
      "primary": "主要操作",
      "secondary": "次要操作",
      "ghost": "文字操作",
      "danger": "危险操作",
      "small": "小号按钮",
      "medium": "标准按钮",
      "large": "大号按钮",
      "loading": "演示加载",
      "disabled": "不可操作",
      "statusLabel": "状态标签示例"
    },
    "feedback": {
      "initial": "点击按钮，查看交互反馈。",
      "primary": "已点击主要操作。",
      "secondary": "已点击次要操作。",
      "ghost": "已点击文字操作。",
      "danger": "危险操作样式预览，没有执行删除。",
      "small": "已点击小号按钮。",
      "medium": "已点击标准按钮。",
      "large": "已点击大号按钮。",
      "loading": "正在演示加载状态，按钮暂时不可重复点击。",
      "done": "加载演示完成。"
    },
    "status": {
      "neutral": "默认",
      "info": "提示",
      "success": "已完成",
      "warning": "待处理",
      "error": "失败"
    },
    "form": {
      "title": "表单输入",
      "description": "标签、帮助和错误消息与输入框关联。",
      "label": "显示名称",
      "placeholder": "怎么称呼你？",
      "hint": "试试留空后检查，再填写一个名称。",
      "required": "请填写显示名称。",
      "disabledValue": "此项暂不可编辑",
      "disabledLabel": "禁用的输入框",
      "disabledHint": "禁用状态不接受输入。",
      "success": "输入检查通过",
      "successDescription": "只更新当前预览，不会保存到服务器。"
    },
    "messages": {
      "title": "加载与反馈",
      "description": "说明正在发生什么，并提供下一步操作。",
      "loading": "正在加载预览内容…",
      "infoTitle": "消息提示",
      "infoDescription": "普通说明保持简短，不打断当前操作。",
      "warningTitle": "需要留意",
      "warningDescription": "用明确文字说明状态，颜色只作为辅助。",
      "errorTitle": "加载失败示例",
      "errorDescription": "可以重新尝试。这里不会发起实际网络请求。",
      "successTitle": "重试演示完成",
      "successDescription": "成功状态使用相同的消息结构。",
      "restore": "恢复错误示例"
    },
    "visual": {
      "title": "统一的视觉基础",
      "description": "颜色、圆角和间距由公共样式维护。",
      "primary": "主色",
      "soft": "浅色背景",
      "success": "成功",
      "warning": "警告",
      "error": "错误",
      "footer": "输入框与按钮使用统一尺寸；组件文案统一来自中英文字典。"
    },
    "footer": "组件预览仅在开发环境开放 · 不连接认证或预约服务"
  },
  "auth": {
    "login": "登录",
    "register": "注册",
    "toLogin": "已有账号？登录",
    "toRegister": "注册账号",
    "fields": {
      "account": "账号",
      "password": "密码",
      "confirmation": "确认密码",
      "accountHint": "3–32位字母、数字、点、下划线或短横线",
      "passwordHint": "8–64位字符"
    },
    "actions": {
      "signOut": "退出登录"
    },
    "loading": "正在检查登录状态…",
    "session": {
      "account": "账号",
      "role": "角色"
    },
    "roles": {
      "student": "普通用户",
      "admin": "系统管理员",
      "maintainer": "维护人员"
    },
    "errors": {
      "title": "操作未完成",
      "csrf": "安全校验已失效，请重试。",
      "password": "密码需为8–64位，且不能全部为空白。",
      "credentials": "账号或密码错误。",
      "taken": "账号已被使用。",
      "mismatch": "两次密码不一致。",
      "disabled": "账号或角色已停用，请联系管理员。",
      "unconfirmed": "登录状态未确认，请重试。"
    }
  },
  "shell": {
    "navigation": {
      "workspace": "工作台",
      "rooms": "座位预约",
      "reservations": "我的预约",
      "roomRequests": "教室申请",
      "repairs": "我的报修",
      "manageRooms": "教室与座位",
      "manageReservations": "预约管理",
      "approvals": "教室审批",
      "manageRepairs": "报修管理",
      "users": "账号管理",
      "settings": "系统设置",
      "statistics": "运营统计",
      "audit": "审计日志",
      "maintenanceTasks": "维修任务",
      "maintenanceHistory": "维修记录",
      "notifications": "消息"
    },
    "navigationLabel": "主导航",
    "account": "个人信息",
    "name": "姓名",
    "skip": "跳到主要内容",
    "empty": "暂无内容",
    "pending": "暂未开放",
    "forbidden": "无访问权限",
    "notFound": "页面不存在",
    "returnWorkspace": "返回工作台",
    "menu": "导航"
  }
}
