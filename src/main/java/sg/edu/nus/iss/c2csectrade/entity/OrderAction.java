package sg.edu.nus.iss.c2csectrade.entity;

/** What someone can ask an order to do. */
public enum OrderAction {
    PAY,
    SHIP,
    CONFIRM_RECEIPT,
    CANCEL,
    EXPIRE
}
