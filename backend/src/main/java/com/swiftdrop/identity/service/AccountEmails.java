package com.swiftdrop.identity.service;

import org.springframework.web.util.HtmlUtils;

/** Text + HTML bodies for account emails (verification, password reset). User-supplied values are HTML-escaped. */
final class AccountEmails {

    static final String RESET_SUBJECT = "Reset your SwiftDrop password";
    static final String CHANGED_SUBJECT = "Your SwiftDrop password was changed";
    static final String VERIFY_SUBJECT = "Confirm your email for SwiftDrop";

    record Body(String text, String html) {
    }

    private AccountEmails() {
    }

    static Body reset(String fullName, String link, long validMinutes) {
        String text = """
                Hi %s,

                We received a request to reset the password for your SwiftDrop account.
                Open this link within %d minutes to choose a new password:

                %s

                The link works once. If you didn't ask for this, you can ignore this email:
                your password will not change.

                The SwiftDrop team""".formatted(fullName, validMinutes, link);

        String html = layout("""
                <p style="margin:0 0 16px">Hi %s,</p>
                <p style="margin:0 0 24px">We received a request to reset the password for your SwiftDrop account.
                  Click the button below within <strong>%d minutes</strong> to choose a new password.</p>
                <p style="margin:0 0 24px;text-align:center">
                  <a href="%s" style="display:inline-block;background:#188048;color:#ffffff;text-decoration:none;
                     font-weight:600;padding:12px 28px;border-radius:8px">Reset password</a>
                </p>
                <p style="margin:0 0 8px;font-size:13px;color:#64748b">Or paste this link into your browser:</p>
                <p style="margin:0 0 24px;font-size:13px;word-break:break-all"><a href="%s" style="color:#188048">%s</a></p>
                <p style="margin:0;font-size:13px;color:#64748b">The link works once. If you didn't ask for this,
                  you can ignore this email: your password will not change.</p>
                """.formatted(escape(fullName), validMinutes, escape(link), escape(link), escape(link)));
        return new Body(text, html);
    }

    static Body verify(String fullName, String link, long validHours) {
        String text = """
                Hi %s,

                Welcome to SwiftDrop! Please confirm that this is your email address by opening this
                link within %d hours:

                %s

                If you didn't create a SwiftDrop account, you can ignore this email.

                The SwiftDrop team""".formatted(fullName, validHours, link);

        String html = layout("""
                <p style="margin:0 0 16px">Hi %s,</p>
                <p style="margin:0 0 24px">Welcome to SwiftDrop! Please confirm that this is your email address.
                  The link is valid for <strong>%d hours</strong>.</p>
                <p style="margin:0 0 24px;text-align:center">
                  <a href="%s" style="display:inline-block;background:#188048;color:#ffffff;text-decoration:none;
                     font-weight:600;padding:12px 28px;border-radius:8px">Confirm email</a>
                </p>
                <p style="margin:0 0 8px;font-size:13px;color:#64748b">Or paste this link into your browser:</p>
                <p style="margin:0 0 24px;font-size:13px;word-break:break-all"><a href="%s" style="color:#188048">%s</a></p>
                <p style="margin:0;font-size:13px;color:#64748b">If you didn't create a SwiftDrop account, you can
                  ignore this email.</p>
                """.formatted(escape(fullName), validHours, escape(link), escape(link), escape(link)));
        return new Body(text, html);
    }

    static Body changed(String fullName, String forgotPasswordLink) {
        String text = """
                Hi %s,

                The password for your SwiftDrop account was just changed, and you have been signed out
                on all your devices.

                If this was you, there's nothing else to do.
                If it wasn't, reset your password straight away: %s

                The SwiftDrop team""".formatted(fullName, forgotPasswordLink);

        String html = layout("""
                <p style="margin:0 0 16px">Hi %s,</p>
                <p style="margin:0 0 16px">The password for your SwiftDrop account was just changed, and you have
                  been signed out on all your devices.</p>
                <p style="margin:0 0 16px">If this was you, there's nothing else to do.</p>
                <p style="margin:0">If it wasn't, <a href="%s" style="color:#188048;font-weight:600">reset your
                  password straight away</a>.</p>
                """.formatted(escape(fullName), escape(forgotPasswordLink)));
        return new Body(text, html);
    }

    private static String layout(String content) {
        return """
                <!doctype html>
                <html><body style="margin:0;padding:24px;background:#f1f5f9;font-family:Segoe UI,Roboto,Arial,sans-serif;
                  color:#0f172a;font-size:15px;line-height:1.6">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                           style="max-width:520px;background:#ffffff;border-radius:12px;border:1px solid #e2e8f0">
                      <tr><td style="padding:24px 32px;border-bottom:1px solid #e2e8f0">
                        <span style="font-size:20px;font-weight:700;color:#13673b">SwiftDrop</span>
                      </td></tr>
                      <tr><td style="padding:28px 32px">%s</td></tr>
                    </table>
                    <p style="margin:16px 0 0;font-size:12px;color:#94a3b8">SwiftDrop · Fast, secure delivery across Sri Lanka</p>
                  </td></tr></table>
                </body></html>""".formatted(content);
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
