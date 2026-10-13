package sg.edu.nus.iss.c2csectrade.entity;

/**
 * Order lifecycle. Sprint 2 only creates orders in PENDING_PAYMENT and moves
 * them to CANCELLED or EXPIRED; the remaining transitions land in Sprint 3.
 */
public enum OrderStatus {

    /** Awaiting payment. Holds a stock reservation until it is paid or closed. */
    PENDING_PAYMENT {
        @Override
        public OrderStatus after(OrderAction action) {
            return switch (action) {
                case PAY -> PAID;
                case CANCEL -> CANCELLED;
                case EXPIRE -> EXPIRED;
                default -> null;
            };
        }

        @Override
        public Actor actor(OrderAction action) {
            return action == OrderAction.CANCEL ? Actor.BUYER : Actor.SYSTEM;
        }
    },

    /**
     * Paid and waiting for the seller to dispatch. The seller may also refund
     * instead of dispatching, which is the only way money flows back out.
     */
    PAID {
        @Override
        public OrderStatus after(OrderAction action) {
            return switch (action) {
                case SHIP -> SHIPPED;
                case REFUND -> REFUNDED;
                default -> null;
            };
        }

        @Override
        public Actor actor(OrderAction action) {
            return Actor.SELLER;
        }
    },

    /** Dispatched and waiting for the buyer to confirm receipt. */
    SHIPPED {
        @Override
        public OrderStatus after(OrderAction action) {
            return action == OrderAction.CONFIRM_RECEIPT ? COMPLETED : null;
        }

        @Override
        public Actor actor(OrderAction action) {
            return Actor.BUYER;
        }
    },

    COMPLETED,
    CANCELLED,
    EXPIRED,

    /**
     * Paid, then refunded. Terminal: the ledger keeps both the payment and the
     * refund as separate rows, so this state is the end of the order rather
     * than a return to PENDING_PAYMENT.
     */
    REFUNDED;

    /** Who is allowed to ask for an action in this state. */
    public enum Actor { BUYER, SELLER, SYSTEM }

    /**
     * The state this one moves to for the given action, or null when the action
     * is not permitted here. Terminal states inherit this default and permit
     * nothing.
     */
    public OrderStatus after(OrderAction action) {
        return null;
    }

    /** Who may perform the action. Meaningless when {@link #after} returns null. */
    public Actor actor(OrderAction action) {
        return Actor.SYSTEM;
    }

    public boolean permits(OrderAction action) {
        return after(action) != null;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == EXPIRED || this == REFUNDED;
    }

    /** Stock is still only reserved, not yet deducted, while the order is unpaid. */
    public boolean holdsReservation() {
        return this == PENDING_PAYMENT;
    }

    /**
     * The buyer has paid, but the money is held against the order rather than
     * being the seller's yet. Leaving this group is what settles it: to the
     * seller when the buyer confirms receipt, back to the buyer on a refund.
     */
    public boolean holdsPayment() {
        return this == PAID || this == SHIPPED;
    }
}
