package com.innov8.loyaltypos.service;

import com.innov8.loyaltypos.model.Payment;
import com.innov8.loyaltypos.model.Transaction;
import com.innov8.loyaltypos.model.TransactionItem;
import com.innov8.loyaltypos.model.RevenueRow;
import com.innov8.loyaltypos.model.VoidLogEntry;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;

public final class PrinterService {
    private static final int ESC = 0x1b;
    private static final int GS = 0x1d;
    private static final int LF = 0x0a;
    private static final int WIDTH = 32;

    private PrinterService() {}

    public static List<String> listPrinters() {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        List<String> out = new ArrayList<>();
        for (PrintService p : services) out.add(p.getName());
        return out;
    }

    public static void printReceipt(Transaction tx, Map<String, Object> settings, String printerName) throws IOException {
        if (printerName == null || printerName.trim().isEmpty())
            throw new IOException("No thermal printer configured. Set the Thermal Printer Name in Settings.");
        byte[] buf = buildReceiptBuffer(tx, settings);
        rawPrint(buf, printerName.trim(), "Receipt");
    }

    public static void printReport(String period, Map<String, String> range, List<RevenueRow> rows,
                                   Map<String, Double> totals, Map<String, Object> settings, String printerName) throws IOException {
        if (printerName == null || printerName.trim().isEmpty())
            throw new IOException("No thermal printer configured. Set the Thermal Printer Name in Settings.");
        byte[] buf = buildReportBuffer(period, range, rows, totals, settings);
        rawPrint(buf, printerName.trim(), "Report");
    }

    public static void printDayLog(String date, List<Transaction> txs, List<VoidLogEntry> voidLog,
                                   Map<String, Object> settings, String printerName) throws IOException {
        if (printerName == null || printerName.trim().isEmpty())
            throw new IOException("No thermal printer configured. Set the Thermal Printer Name in Settings.");
        byte[] buf = buildDayLogBuffer(date, txs, voidLog, settings);
        rawPrint(buf, printerName.trim(), "DayLog");
    }

    private static void rawPrint(byte[] data, String printer, String docName) throws IOException {
        Path tmp = Files.createTempFile(docName.toLowerCase() + "_", ".bin");
        Files.write(tmp, data);
        try {
            String script = buildPsScript(printer, tmp.toString(), docName);
            ProcessBuilder pb = new ProcessBuilder("powershell.exe",
                    "-NonInteractive", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            proc.getInputStream().transferTo(baos);
            int code;
            try { code = proc.waitFor(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IOException(ie); }
            if (code != 0) throw new IOException("Print failed: " + baos.toString().trim());
        } finally {
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
        }
    }

    private static String buildPsScript(String printerName, String filePath, String docName) {
        String safePrinter = printerName.replace("'", "''");
        String safeFile = filePath.replace("\\", "\\\\");
        return ""
                + "Add-Type -Language CSharp -TypeDefinition @'\n"
                + "using System;\n"
                + "using System.Runtime.InteropServices;\n"
                + "public class RawPrinter {\n"
                + "  [DllImport(\"winspool.Drv\", EntryPoint=\"OpenPrinterA\")] public static extern bool OpenPrinter(string p, out IntPtr h, IntPtr d);\n"
                + "  [DllImport(\"winspool.Drv\", EntryPoint=\"ClosePrinter\")] public static extern bool ClosePrinter(IntPtr h);\n"
                + "  [DllImport(\"winspool.Drv\", EntryPoint=\"StartDocPrinterA\")] public static extern Int32 StartDocPrinter(IntPtr h, Int32 lvl, IntPtr di);\n"
                + "  [DllImport(\"winspool.Drv\", EntryPoint=\"EndDocPrinter\")] public static extern bool EndDocPrinter(IntPtr h);\n"
                + "  [DllImport(\"winspool.Drv\", EntryPoint=\"StartPagePrinter\")] public static extern bool StartPagePrinter(IntPtr h);\n"
                + "  [DllImport(\"winspool.Drv\", EntryPoint=\"EndPagePrinter\")] public static extern bool EndPagePrinter(IntPtr h);\n"
                + "  [DllImport(\"winspool.Drv\", EntryPoint=\"WritePrinter\")] public static extern bool WritePrinter(IntPtr h, IntPtr p, Int32 n, out Int32 w);\n"
                + "}\n'@\n"
                + "$bytes = [System.IO.File]::ReadAllBytes('" + safeFile + "')\n"
                + "$hPrinter = [IntPtr]::Zero\n"
                + "$opened = [RawPrinter]::OpenPrinter('" + safePrinter + "', [ref]$hPrinter, [IntPtr]::Zero)\n"
                + "if (-not $opened) { throw \"Cannot open printer: " + safePrinter + "\" }\n"
                + "$pDocName = [System.Runtime.InteropServices.Marshal]::StringToHGlobalAnsi(\"" + docName + "\")\n"
                + "$pDataType = [System.Runtime.InteropServices.Marshal]::StringToHGlobalAnsi(\"RAW\")\n"
                + "$diSize = [IntPtr]::Size * 3\n"
                + "$di = [System.Runtime.InteropServices.Marshal]::AllocHGlobal($diSize)\n"
                + "[System.Runtime.InteropServices.Marshal]::WriteIntPtr($di, 0, $pDocName)\n"
                + "[System.Runtime.InteropServices.Marshal]::WriteIntPtr($di, [IntPtr]::Size, [IntPtr]::Zero)\n"
                + "[System.Runtime.InteropServices.Marshal]::WriteIntPtr($di, [IntPtr]::Size * 2, $pDataType)\n"
                + "[RawPrinter]::StartDocPrinter($hPrinter, 1, $di) | Out-Null\n"
                + "[System.Runtime.InteropServices.Marshal]::FreeHGlobal($di) | Out-Null\n"
                + "[System.Runtime.InteropServices.Marshal]::FreeHGlobal($pDocName) | Out-Null\n"
                + "[System.Runtime.InteropServices.Marshal]::FreeHGlobal($pDataType) | Out-Null\n"
                + "[RawPrinter]::StartPagePrinter($hPrinter) | Out-Null\n"
                + "$ptr = [System.Runtime.InteropServices.Marshal]::AllocCoTaskMem($bytes.Length)\n"
                + "[System.Runtime.InteropServices.Marshal]::Copy($bytes, 0, $ptr, $bytes.Length)\n"
                + "$written = 0\n"
                + "[RawPrinter]::WritePrinter($hPrinter, $ptr, $bytes.Length, [ref]$written) | Out-Null\n"
                + "[System.Runtime.InteropServices.Marshal]::FreeCoTaskMem($ptr) | Out-Null\n"
                + "[RawPrinter]::EndPagePrinter($hPrinter) | Out-Null\n"
                + "[RawPrinter]::EndDocPrinter($hPrinter) | Out-Null\n"
                + "[RawPrinter]::ClosePrinter($hPrinter) | Out-Null\n";
    }

    // ─── Buffer builders ──────────────────────────────────────────────────────
    private static byte[] buildReceiptBuffer(Transaction tx, Map<String, Object> settings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DecimalFormat df = new DecimalFormat("#,##0.00");
        String dash = repeat("-", WIDTH);
        String dbl = repeat("=", WIDTH);
        String biz = up(getStr(settings, "business_name", "LOYALTY POS"));
        String addr = getStr(settings, "business_address", "");
        String contact = getStr(settings, "business_contact", "");

        write(out, ESC, 0x40);                  // INIT
        write(out, ESC, 0x61, 0x01);            // CENTER
        write(out, ESC, 0x45, 0x01); write(out, GS, 0x21, 0x01); // BOLD ON, DBL HEIGHT
        ln(out, truncate(biz, WIDTH));
        write(out, GS, 0x21, 0x00); write(out, ESC, 0x45, 0x00); // NORMAL, BOLD OFF
        if (!addr.isEmpty()) ln(out, truncate(center(addr, WIDTH), WIDTH));
        if (!contact.isEmpty()) ln(out, truncate(center(contact, WIDTH), WIDTH));
        ln(out, dbl);

        write(out, ESC, 0x61, 0x00);            // LEFT
        ln(out, "Invoice: " + (tx.invoiceNo == null ? "-" : tx.invoiceNo));

        OffsetDateTime d;
        try { d = OffsetDateTime.parse(tx.date); } catch (Exception e) { d = OffsetDateTime.now(); }
        ln(out, "Date   : " + d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a")));
        ln(out, "Cashier: " + truncate(nz(tx.cashierName), WIDTH - 9));
        if (tx.customerName != null) ln(out, "Customer: " + truncate(tx.customerName, WIDTH - 10));
        if (tx.plateNo != null) ln(out, "Plate  : " + tx.plateNo);
        ln(out, dash);

        write(out, ESC, 0x45, 0x01);
        ln(out, pad("ITEM", "AMOUNT", WIDTH));
        write(out, ESC, 0x45, 0x00);
        ln(out, dash);

        for (TransactionItem it : tx.items) {
            String name = truncate(nz(it.productName), WIDTH);
            double price = it.customPrice != null ? it.customPrice : it.unitPrice;
            String qtyStr = "  " + df.format(it.quantity) + " x " + df.format(price);
            String amtStr = df.format(it.amount);
            ln(out, name);
            ln(out, pad(qtyStr, amtStr, WIDTH));
        }
        ln(out, dash);

        ln(out, pad("Subtotal", df.format(tx.subtotal), WIDTH));
        if (tx.deliveryCharge > 0) ln(out, pad("Delivery", "+" + df.format(tx.deliveryCharge), WIDTH));
        if (tx.discount > 0) ln(out, pad("Discount", "-" + df.format(tx.discount), WIDTH));

        write(out, ESC, 0x45, 0x01); write(out, GS, 0x21, 0x01);
        ln(out, pad("TOTAL", df.format(tx.totalAmount), WIDTH));
        write(out, GS, 0x21, 0x00); write(out, ESC, 0x45, 0x00);
        ln(out, dash);

        double totalPaid = 0;
        for (Payment p : tx.payments) {
            totalPaid += p.amount;
            String label = switch (p.method) {
                case "cash" -> "Cash";
                case "gcash" -> "GCash";
                case "maya" -> "Maya";
                case "po" -> "PO Account";
                default -> p.method;
            };
            ln(out, pad(label, df.format(p.amount), WIDTH));
            if (p.referenceNo != null && !p.referenceNo.isEmpty()) ln(out, "  Ref: " + p.referenceNo);
            if (p.senderName != null && !p.senderName.isEmpty()) ln(out, "  From: " + p.senderName);
        }
        double change = totalPaid - tx.totalAmount;
        if (change > 0.005) ln(out, pad("Change", df.format(change), WIDTH));

        ln(out, dbl);
        write(out, ESC, 0x61, 0x01);
        ln(out, center("Thank you!", WIDTH));
        ln(out, center("Please come again.", WIDTH));
        ln(out, "");
        write(out, ESC, 0x70, 0x00, 0x19, 0xFA); // CASH DRAWER
        write(out, ESC, 0x64, 0x04);             // FEED
        write(out, GS, 0x56, 0x41, 0x10);        // CUT

        return out.toByteArray();
    }

    private static byte[] buildReportBuffer(String period, Map<String, String> range, List<RevenueRow> rows,
                                             Map<String, Double> totals, Map<String, Object> settings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DecimalFormat df = new DecimalFormat("#,##0.00");
        String dash = repeat("-", WIDTH);
        String dbl = repeat("=", WIDTH);
        String biz = up(getStr(settings, "business_name", "LOYALTY POS"));
        String rangeLabel = range.get("from").equals(range.get("to")) ? range.get("from") : range.get("from") + " - " + range.get("to");

        write(out, ESC, 0x40);
        write(out, ESC, 0x61, 0x01);
        write(out, ESC, 0x45, 0x01); write(out, GS, 0x21, 0x01);
        ln(out, truncate(biz, WIDTH));
        write(out, GS, 0x21, 0x00); write(out, ESC, 0x45, 0x00);
        ln(out, dbl);

        write(out, ESC, 0x45, 0x01);
        ln(out, center(period.toUpperCase() + " REPORT", WIDTH));
        write(out, ESC, 0x45, 0x00);
        ln(out, center(rangeLabel, WIDTH));
        ln(out, dash);

        write(out, ESC, 0x61, 0x00);
        write(out, ESC, 0x45, 0x01);
        ln(out, "SUMMARY");
        write(out, ESC, 0x45, 0x00);
        ln(out, pad("Total Revenue", df.format(totals.getOrDefault("total", 0.0)), WIDTH));
        ln(out, pad("  Cash", df.format(totals.getOrDefault("cash", 0.0)), WIDTH));
        ln(out, pad("  GCash", df.format(totals.getOrDefault("gcash", 0.0)), WIDTH));
        ln(out, pad("  Maya", df.format(totals.getOrDefault("maya", 0.0)), WIDTH));
        ln(out, pad("  PO", df.format(totals.getOrDefault("po", 0.0)), WIDTH));
        ln(out, pad("  Delivery", df.format(totals.getOrDefault("delivery", 0.0)), WIDTH));
        ln(out, dash);

        write(out, ESC, 0x45, 0x01);
        ln(out, "BREAKDOWN");
        write(out, ESC, 0x45, 0x00);
        for (RevenueRow r : rows) {
            if (r.total > 0) ln(out, pad(r.day, df.format(r.total), WIDTH));
        }
        ln(out, dash);
        write(out, ESC, 0x45, 0x01);
        ln(out, pad("TOTAL", df.format(totals.getOrDefault("total", 0.0)), WIDTH));
        write(out, ESC, 0x45, 0x00);
        ln(out, dbl);
        write(out, ESC, 0x61, 0x01);
        ln(out, center("Generated: " + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), WIDTH));
        ln(out, "");
        write(out, ESC, 0x64, 0x04);
        write(out, GS, 0x56, 0x41, 0x10);
        return out.toByteArray();
    }

    private static byte[] buildDayLogBuffer(String date, List<Transaction> txs, List<VoidLogEntry> voidLog, Map<String, Object> settings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DecimalFormat df = new DecimalFormat("#,##0.00");
        String dash = repeat("-", WIDTH);
        String dbl = repeat("=", WIDTH);
        String biz = up(getStr(settings, "business_name", "LOYALTY POS"));

        write(out, ESC, 0x40);
        write(out, ESC, 0x61, 0x01);
        write(out, ESC, 0x45, 0x01); write(out, GS, 0x21, 0x01);
        ln(out, truncate(biz, WIDTH));
        write(out, GS, 0x21, 0x00); write(out, ESC, 0x45, 0x00);
        ln(out, dbl);
        write(out, ESC, 0x45, 0x01);
        ln(out, center("END OF DAY LOG", WIDTH));
        write(out, ESC, 0x45, 0x00);
        ln(out, center(date, WIDTH));
        ln(out, dash);

        write(out, ESC, 0x61, 0x00);
        write(out, ESC, 0x45, 0x01);
        ln(out, "TRANSACTIONS (" + txs.size() + ")");
        write(out, ESC, 0x45, 0x00);
        ln(out, dash);

        double totalRev = 0; int voidedCount = 0;
        for (Transaction tx : txs) {
            String tag = "voided".equals(tx.paymentStatus) ? "[VOID]" : "[OK]  ";
            ln(out, pad(truncate(tag + " " + tx.invoiceNo, 20), df.format(tx.totalAmount), WIDTH));
            String t;
            try { t = OffsetDateTime.parse(tx.date).format(DateTimeFormatter.ofPattern("hh:mm a")); }
            catch (Exception e) { t = ""; }
            ln(out, "  " + t + " " + truncate(nz(tx.cashierName), WIDTH - 10));
            if (tx.customerName != null) ln(out, "  " + truncate(tx.customerName, WIDTH - 2));
            if (!"voided".equals(tx.paymentStatus)) totalRev += tx.totalAmount;
            else voidedCount++;
        }
        ln(out, dash);
        write(out, ESC, 0x45, 0x01);
        ln(out, pad("TOTAL REVENUE", df.format(totalRev), WIDTH));
        write(out, ESC, 0x45, 0x00);
        if (voidedCount > 0) ln(out, pad("Voided txns", String.valueOf(voidedCount), WIDTH));

        if (!voidLog.isEmpty()) {
            ln(out, dbl);
            write(out, ESC, 0x45, 0x01);
            ln(out, "VOID LOG (" + voidLog.size() + ")");
            write(out, ESC, 0x45, 0x00);
            ln(out, dash);
            for (VoidLogEntry v : voidLog) {
                String t;
                try { t = OffsetDateTime.parse(v.createdAt).format(DateTimeFormatter.ofPattern("hh:mm a")); } catch (Exception e) { t = ""; }
                ln(out, truncate(v.invoiceNo, WIDTH));
                String by = v.performedByName != null ? " by " + truncate(v.performedByName, 12) : "";
                ln(out, "  " + t + by);
                if (v.reason != null && !v.reason.isEmpty()) ln(out, "  Reason: " + truncate(v.reason, WIDTH - 10));
            }
        }
        ln(out, dbl);
        write(out, ESC, 0x64, 0x04);
        write(out, GS, 0x56, 0x41, 0x10);
        return out.toByteArray();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────
    private static void write(ByteArrayOutputStream out, int... bytes) {
        for (int b : bytes) out.write(b & 0xff);
    }
    private static void ln(ByteArrayOutputStream out, String s) {
        for (char c : s.toCharArray()) {
            int code = c;
            if (code == 0x20b1) { out.write('P'); out.write('h'); out.write('p'); }
            else if (code < 0x80 || code == 0xa0) out.write(code);
            else out.write('?');
        }
        out.write(LF);
    }
    private static String repeat(String s, int n) { return s.repeat(n); }
    private static String pad(String left, String right, int w) {
        int gap = w - left.length() - right.length();
        return left + " ".repeat(Math.max(1, gap)) + right;
    }
    private static String center(String s, int w) {
        int spaces = Math.max(0, w - s.length());
        return " ".repeat(spaces / 2) + s;
    }
    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "." : s;
    }
    private static String up(String s) { return s == null ? "" : s.toUpperCase(); }
    private static String nz(String s) { return s == null ? "" : s; }
    private static String getStr(Map<String, Object> m, String k, String d) {
        Object v = m == null ? null : m.get(k);
        return v == null ? d : v.toString();
    }
}
