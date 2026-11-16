export default {
  "title": "教室",
  "manage": "教室管理",
  "actions": {
    "addRoom": "新增教室",
    "editRoom": "修改教室",
    "addSeat": "新增座位",
    "editSeat": "修改座位",
    "save": "保存",
    "cancel": "取消",
    "retry": "重试",
    "reload": "重新加载",
    "query": "查询",
    "reserve": "预约",
    "request": "整室申请",
    "repair": "报修",
    "code": "今日签到码",
    "refresh": "刷新",
    "back": "返回教室"
  },
  "fields": {
    "room": "教室",
    "roomCode": "教室编号",
    "roomName": "教室名称",
    "campus": "校区",
    "building": "楼宇",
    "capacity": "容量",
    "department": "限定院系（可空）",
    "opens": "开放时间",
    "closes": "关闭时间",
    "seatCode": "座位编号",
    "socket": "插座",
    "state": "状态",
    "actions": "操作",
    "starts": "开始时间",
    "ends": "结束时间"
  },
  "status": {
    "enabled": "启用",
    "disabled": "停用",
    "bookingEnabled": "开放预约",
    "bookingDisabled": "未开放预约",
    "public": "公共教室",
    "department": "限定院系：{code}",
    "allDay": "全天",
    "loading": "加载中",
    "saving": "保存中",
    "noRooms": "暂无教室",
    "noSeats": "暂无座位",
    "noAvailable": "该时段无可用座位",
    "all": "全部",
    "withSocket": "有插座",
    "withoutSocket": "无插座",
    "shanghai": "上海时间"
  },
  "sockets": {
    "NONE": "无插座",
    "FIXED": "固定插座",
    "RAIL": "轨道插座"
  },
  "errors": {
    "load": "加载失败",
    "save": "保存失败",
    "changed": "资料已更新，请重新加载",
    "capacity": "容量与启用座位数量不符",
    "disabled": "教室已停用",
    "time": "请选择有效的起止时间"
  },
  "availability": "时段查询",
  "code": {
    "title": "今日签到码",
    "date": "日期",
    "unavailable": "签到码暂不可用"
  }
} as const
