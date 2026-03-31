package model;

/**
 * 学生在食堂中的实时状态
 * 旧状态保留，新增状态只增不减，方便和后续模块安全对接
 */
public enum StudentStatus {
    ARRIVING,          // 刚到门口/进入食堂
    QUEUING,           // 正在窗口排队
    DINING,            // 正在桌子就餐
    LEAVING,           // 正在离开流程中
    BALKED,            // 嫌人多没排队直接离开（放弃）

    SELECTING_WINDOW,  // 正在选择窗口
    SERVING,           // 正在被窗口服务
    WAITING_SEAT,      // 打完饭后正在找座位
    LEFT_NORMAL,       // 正常完成就餐并离开
    LEFT_NO_SEAT       // 因无座位离开
}