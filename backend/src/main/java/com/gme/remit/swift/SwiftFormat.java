package com.gme.remit.swift;

import com.gme.remit.common.money.Currencies;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** SWIFT FIN formatting helpers (ported from the reference swift_mt module). */
public final class SwiftFormat {

    private static final Pattern X_CHARSET = Pattern.compile("^[A-Za-z0-9/\\-?:().,'+ ]*$");
    private static final Pattern BIC = Pattern.compile("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");
    private static final Pattern NON_REF = Pattern.compile("[^A-Za-z0-9/\\-?:().,'+ ]");
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    private SwiftFormat() {
    }

    /** SWIFT amount: digits with a mandatory decimal comma, no thousands separators. */
    public static String amount(BigDecimal major, String currency) {
        int dp = Currencies.minorUnits(currency);
        BigDecimal q = major.setScale(dp, java.math.RoundingMode.HALF_UP);
        if (q.signum() < 0) {
            throw new SwiftFormatException("amount must be non-negative");
        }
        String s = q.toPlainString();
        int dot = s.indexOf('.');
        String intPart = dot < 0 ? s : s.substring(0, dot);
        String frac = dot < 0 ? "" : s.substring(dot + 1);
        String out = intPart + "," + frac; // comma decimal separator; trailing comma if dp==0
        if (out.replace(",", "").length() > 15) {
            throw new SwiftFormatException("amount exceeds 15 digits");
        }
        return out;
    }

    public static String valueDate(LocalDate d) {
        return d.format(YYMMDD);
    }

    public static String checkCharset(String text, String field) {
        for (String line : text.split("\n")) {
            if (!X_CHARSET.matcher(line).matches()) {
                throw new SwiftFormatException(field + ": chars not in SWIFT x-set");
            }
        }
        return text;
    }

    public static String checkBic(String bic, String field) {
        String b = bic.strip().toUpperCase();
        if (!BIC.matcher(b).matches()) {
            throw new SwiftFormatException(field + ": invalid BIC '" + bic + "' (need 8 or 11 chars)");
        }
        return b;
    }

    /** Coerce free-text lines into n*width party/narrative blocks. */
    public static List<String> truncateLines(List<String> lines, int maxLines, int width) {
        List<String> out = new ArrayList<>();
        for (String raw : lines) {
            String ln = raw == null ? "" : raw.strip();
            while (!ln.isEmpty() && out.size() < maxLines) {
                out.add(ln.substring(0, Math.min(width, ln.length())));
                ln = ln.length() > width ? ln.substring(width) : "";
            }
            if (out.size() >= maxLines) {
                break;
            }
        }
        return out;
    }

    /** Field 20/21 reference: &lt;=16x, no leading/trailing '/', no '//'. */
    public static String ref16(String value) {
        String v = NON_REF.matcher(value).replaceAll("");
        v = v.replaceAll("^/+|/+$", "");
        if (v.length() > 16) {
            v = v.substring(0, 16);
        }
        v = v.replace("//", "/");
        if (v.isEmpty()) {
            throw new SwiftFormatException("reference (:20:/:21:) is empty after sanitisation");
        }
        return v;
    }

    public static String ltAddress(String bic) {
        String b8 = checkBic(bic, "header-bic").substring(0, 8);
        return b8 + "A" + "XXX";
    }
}
