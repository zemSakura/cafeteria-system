package model;

/**
 * 学生在食堂中的实时状态
 * 方便可视化模块根据状态改变图标颜色或位置
 */
public enum StudentStatus {
    ARRIVING,   // 刚到门口/进入食堂
    QUEUING,    // 正在窗口排队
    DINING,     // 正在桌子就餐
    LEAVING,    // 已正常结束并离开
    BALKED      // 嫌人多没排队直接离开（放弃）
}