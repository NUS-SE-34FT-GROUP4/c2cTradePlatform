package sg.edu.nus.iss.c2csectrade.entity;

import java.util.Arrays;
import java.util.Optional;

/** The fixed reasons a report can give; GET /api/reports/reasons serves them to the dropdown. */
public enum ReportReason {
    FRAUD("Scam or fraud"),
    PROHIBITED_ITEM("Prohibited item"),
    MISLEADING("Misleading listing"),
    HARASSMENT("Harassment or abuse"),
    SPAM("Spam"),
    OTHER("Other");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Optional<ReportReason> parse(String code) {
        return Arrays.stream(values()).filter(r -> r.name().equals(code)).findFirst();
    }
}
