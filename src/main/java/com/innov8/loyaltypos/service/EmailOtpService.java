package com.innov8.loyaltypos.service;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.db.Database;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory 6-digit OTP generator + verifier with optional email delivery via
 * raw SMTPS. Configured by these settings keys:
 *
 *   smtp_host          (e.g. smtp.gmail.com)
 *   smtp_port          (default 465 — SMTPS)
 *   smtp_user          (login username, usually the from address)
 *   smtp_pass          (app password — never the account's main password)
 *   smtp_from          (visible sender, defaults to smtp_user)
 *
 * When SMTP is not configured the service falls back to "display the code on
 * screen" mode — the OTP modal will surface the code locally with a clear
 * "DEV MODE — configure SMTP to deliver via email" note. This keeps the
 * feature functional out-of-the-box without leaking credentials.
 */
public final class EmailOtpService {
    private EmailOtpService() {}

    private static final SecureRandom RND = new SecureRandom();
    private static final long TTL_SECONDS = 5 * 60; // 5 minutes

    private static final class Entry {
        final String code;
        final long expiresAt;
        Entry(String code, long expiresAt) { this.code = code; this.expiresAt = expiresAt; }
    }
    private static final Map<Integer, Entry> codes = new HashMap<>();

    public static String userEmail(int userId) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT email FROM users WHERE id=?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String e = rs.getString(1);
                return e == null ? "" : e.trim();
            }
        } catch (Exception ignore) {}
        return "";
    }

    public static class IssueResult {
        public final String code;
        public final String email;
        public final boolean delivered;
        public final String deliveryError;
        IssueResult(String code, String email, boolean delivered, String err) {
            this.code = code; this.email = email; this.delivered = delivered; this.deliveryError = err;
        }
    }

    /** Generate, store, and (best-effort) send a 6-digit code for the user. */
    public static IssueResult issue(int userId) {
        String email = userEmail(userId);
        String code = String.format("%06d", RND.nextInt(1_000_000));
        long expires = Instant.now().getEpochSecond() + TTL_SECONDS;
        synchronized (codes) { codes.put(userId, new Entry(code, expires)); }

        if (email == null || email.isEmpty()) {
            // No email on file — code stays in memory; caller surfaces it locally.
            return new IssueResult(code, "", false, "No email address on file for this user.");
        }
        String host = settingString("smtp_host");
        String user = settingString("smtp_user");
        String pass = settingString("smtp_pass");
        int port = settingInt("smtp_port", 465);
        String from = settingString("smtp_from");
        if (from == null || from.isEmpty()) from = user;
        if (host == null || host.isEmpty() || user == null || user.isEmpty() || pass == null || pass.isEmpty()) {
            return new IssueResult(code, email, false, "SMTP is not configured in Settings — code will be shown locally.");
        }
        try {
            sendSmtps(host, port, user, pass, from, email,
                    "Loyalty POS — your sign-in code",
                    "Your verification code is " + code + ".\n\nThis code expires in 5 minutes.\n\nIf you did not request a sign-in, ignore this email.");
            return new IssueResult(code, email, true, null);
        } catch (Exception ex) {
            return new IssueResult(code, email, false, ex.getMessage());
        }
    }

    /** Returns true if the code matches and is still within TTL. Consumes on success. */
    public static boolean verify(int userId, String code) {
        if (code == null) return false;
        synchronized (codes) {
            Entry e = codes.get(userId);
            if (e == null) return false;
            if (Instant.now().getEpochSecond() > e.expiresAt) { codes.remove(userId); return false; }
            if (!e.code.equals(code.trim())) return false;
            codes.remove(userId); // single-use
            return true;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // SMTP over SSL (port 465) — self-contained, no JavaMail dependency.
    // ──────────────────────────────────────────────────────────────────────
    private static void sendSmtps(String host, int port, String user, String pass,
                                  String from, String to, String subject, String body) throws Exception {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket sock = (SSLSocket) factory.createSocket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8), false)) {
            sock.setSoTimeout(15000);
            read(in, 220);
            write(out, "EHLO loyalty-pos"); read(in, 250);
            write(out, "AUTH LOGIN"); read(in, 334);
            write(out, b64(user)); read(in, 334);
            write(out, b64(pass)); read(in, 235);
            write(out, "MAIL FROM:<" + from + ">"); read(in, 250);
            write(out, "RCPT TO:<" + to + ">"); read(in, 250);
            write(out, "DATA"); read(in, 354);
            out.print("From: " + from + "\r\n");
            out.print("To: " + to + "\r\n");
            out.print("Subject: " + subject + "\r\n");
            out.print("MIME-Version: 1.0\r\n");
            out.print("Content-Type: text/plain; charset=UTF-8\r\n");
            out.print("\r\n");
            for (String line : body.split("\n")) {
                if (line.startsWith(".")) line = "." + line; // dot-stuffing
                out.print(line + "\r\n");
            }
            out.print(".\r\n"); out.flush();
            read(in, 250);
            write(out, "QUIT"); // ignore quit response
        }
    }

    private static void write(PrintWriter out, String cmd) {
        out.print(cmd + "\r\n");
        out.flush();
    }
    private static String read(BufferedReader in, int expectedCode) throws Exception {
        StringBuilder all = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            all.append(line).append('\n');
            if (line.length() >= 4 && line.charAt(3) != '-') break; // last line of multi-line reply
        }
        if (all.length() < 3) throw new Exception("SMTP empty response");
        int code;
        try { code = Integer.parseInt(all.substring(0, 3)); }
        catch (NumberFormatException e) { throw new Exception("SMTP malformed response: " + all); }
        if (code != expectedCode) throw new Exception("SMTP expected " + expectedCode + " got: " + all.toString().trim());
        return all.toString();
    }
    private static String b64(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }

    private static String settingString(String key) {
        Object v = App.ctx.settings.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }
    private static int settingInt(String key, int dflt) {
        Object v = App.ctx.settings.get(key);
        if (v == null) return dflt;
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception e) { return dflt; }
    }
}
