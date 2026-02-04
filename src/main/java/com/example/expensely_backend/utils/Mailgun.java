package com.example.expensely_backend.utils;

import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

@Component
public class Mailgun {

    public void sendSimpleMessage(String to, String subject, String text) {
        String apiKey = System.getenv("MAILGUN_API_KEY");
        String domain = "expensely.store"; // e.g., sandboxXXX.mailgun.org
        String from = "Expensely <notifications@" + domain + ">";

        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("Mailgun API key is not set.");
            return;
        }
        if (to == null || to.isEmpty()) {
            System.out.println("Recipient email is not set.");
            return;
        }
        if (subject == null) {
            System.out.println("Email subject is not set.");
            return;
        }
        if (text == null) {
            System.out.println("Email text is not set.");
            return;
        }


        try {
            URL url = new URL("https://api.mailgun.net/v3/" + domain + "/messages");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");

            // Basic Auth
            String auth = "api:" + apiKey;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);

            // Form parameters
            String data = "from=" + from +
                    "&to=" + to +
                    "&subject=" + subject +
                    "&text=" + text;

            // Send request
            OutputStream os = conn.getOutputStream();
            os.write(data.getBytes());
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            if (responseCode == 200) {
                System.out.println("Email sent successfully!");
            } else {
                System.out.println("Failed to send email.");
                System.out.println("Response Message: " + conn.getResponseMessage());
            }

        } catch (Exception e) {
            System.out.println("Error sending email: " + e.getMessage());
        }

    }
}