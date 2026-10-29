package com.example.silverbusinessapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class JavaMailAPI {

    private final WeakReference<Context> contextRef;
    private final String email;
    private final String subject;
    private final String message;

    public JavaMailAPI(Context context, String email, String subject, String message) {
        this.contextRef = new WeakReference<>(context);
        this.email = email;
        this.subject = subject;
        this.message = message;
    }

    public void sendEmail() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                final String senderEmail = "ukkvdon@gmail.com"; // your email   sathishsilver7619@gmail.com
                final String senderPassword = "lgxfaqanbscnwtjf"; // your app password    tynmmcsnrisntwns

                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", "465");

                Session session = Session.getInstance(props, new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(senderEmail, senderPassword);
                    }
                });

                Message mimeMessage = new MimeMessage(session);
                mimeMessage.setFrom(new InternetAddress(senderEmail));
                mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
                mimeMessage.setSubject(subject);
                mimeMessage.setText(message);
                Transport.send(mimeMessage);

                handler.post(() -> {
                    Context context = contextRef.get();
                    if (context != null) {
                        Toast.makeText(context, "OTP sent to your email", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e("JavaMailAPI", "Email send error: " + e.getMessage(), e);
                handler.post(() -> {
                    Context context = contextRef.get();
                    if (context != null) {
                        Toast.makeText(context, "Failed to send email: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
}
