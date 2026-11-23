
package com.irfan.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TelegramBot {

    private final String token;
    private final String apiUrl;
    private final String webAppUrl;

    private final HttpClient client;
    private final ObjectMapper mapper;

    private long offset = 0;

    public TelegramBot(String token) {

        this.token = token;
        this.apiUrl = "https://api.telegram.org/bot" + token + "/";

        this.webAppUrl = System.getenv("BOOKING_WEBAPP_URL");

        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        this.mapper = new ObjectMapper();
    }

    public void start() {

        System.out.println("Telegram Mini App Bot started!");

        while (!Thread.currentThread().isInterrupted()) {

            try {

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl
                                + "getUpdates?offset=" + offset
                                + "&timeout=30"))
                        .timeout(Duration.ofSeconds(40))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                JsonNode result = mapper.readTree(response.body());

                if (!result.path("ok").asBoolean()) {
                    System.out.println("Telegram API error: "
                            + response.body());
                    Thread.sleep(3000);
                    continue;
                }

                for (JsonNode update : result.path("result")) {

                    offset = update.path("update_id").asLong() + 1;

                    JsonNode message = update.path("message");

                    if (message.isMissingNode()) {
                        continue;
                    }

                    String chatId = message.path("chat")
                            .path("id").asText();

                    // Mini App form submission
                    JsonNode webAppData =
                            message.path("web_app_data");

                    if (!webAppData.isMissingNode()) {

                        String data = webAppData.path("data").asText();

                        handleBookingSubmission(chatId, data);
                        continue;
                    }

                    String text = message.path("text").asText("");

                    if (!text.isBlank()) {
                        handleCommand(chatId, text.trim());
                    }
                }

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                break;

            } catch (Exception e) {

                System.out.println("Bot error: " + e.getMessage());

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void handleCommand(String chatId, String text) {

        String command = text.split("\\s+")[0]
                .split("@")[0]
                .toLowerCase();

        switch (command) {

            case "/start":

                sendMessage(chatId,
                        "Welcome to Movie Booking Bot!\n\n"
                        + "Use the commands below to continue:\n\n"
                        + "/book - Running movie booking\n"
                        + "/upcoming - Upcoming movie monitoring\n"
                        + "/help - Help");

                break;

            case "/book":

                sendBookingForm(chatId, "RUNNING");
                break;

            case "/upcoming":

                sendBookingForm(chatId, "UPCOMING");
                break;

            case "/help":

                sendMessage(chatId,
                        "MOVIE BOOKING BOT\n\n"
                        + "/book - Book a running movie\n"
                        + "/upcoming - Monitor upcoming movies\n"
                        + "/help - Show help");

                break;

            default:

                sendMessage(chatId,
                        "Please use /book or /upcoming to open the booking form.");
        }
    }

    // Send Telegram Mini App launch button
    private void sendBookingForm(String chatId, String type) {

        if (webAppUrl == null || webAppUrl.isBlank()
                || !webAppUrl.startsWith("https://")) {

            sendMessage(chatId,
                    "Booking interface is not deployed yet.\n"
                    + "Please configure BOOKING_WEBAPP_URL first.");

            return;
        }

        try {

            ObjectNode keyboardButton = mapper.createObjectNode();

            keyboardButton.put("text", "🎬 Open Movie Booking Form");

            ObjectNode webApp = mapper.createObjectNode();

            webApp.put("url", webAppUrl + "?type=" + type);

            keyboardButton.set("web_app", webApp);

            ArrayNode buttonRow = mapper.createArrayNode();
            buttonRow.add(keyboardButton);

            ArrayNode keyboard = mapper.createArrayNode();
            keyboard.add(buttonRow);

            ObjectNode replyMarkup = mapper.createObjectNode();

            replyMarkup.set("keyboard", keyboard);
            replyMarkup.put("resize_keyboard", true);
            replyMarkup.put("one_time_keyboard", true);

            sendMessageWithKeyboard(
                    chatId,
                    "Select the button below to open your booking form.",
                    replyMarkup
            );

        } catch (Exception e) {

            System.out.println("Form button error: " + e.getMessage());
        }
    }

    // Receive Mini App form data
    private void handleBookingSubmission(String chatId, String data) {

        try {

            JsonNode booking = mapper.readTree(data);

            String type = booking.path("bookingType").asText();
            String movie = booking.path("movie").asText().trim();
            String location = booking.path("location").asText().trim();
            int tickets = booking.path("tickets").asInt();
            String showTime = booking.path("showTime").asText();

            boolean validType = type.equals("RUNNING")
                    || type.equals("UPCOMING");

            boolean validTime = showTime.equals("Morning")
                    || showTime.equals("Afternoon")
                    || showTime.equals("Night");

            if (!validType || movie.length() < 2
                    || location.length() < 2
                    || tickets < 1 || tickets > 5
                    || !validTime) {

                sendMessage(chatId,
                        "Invalid booking request. Please submit the form again.");

                return;
            }

            String summary =
                    "YOUR BOOKING REQUEST\n\n"
                    + "Type: " + type
                    + "\nMovie: " + movie
                    + "\nLocation: " + location
                    + "\nTickets: " + tickets
                    + "\nShowtime: " + showTime
                    + "\n\n"
                    + "Request received successfully!\n"
                    + "Next, we'll check BookMyShow availability.\n\n"
                    + "No tickets have been booked or paid for yet.";

            sendMessage(chatId, summary);

            System.out.println("New booking request:");
            System.out.println(data);

        } catch (Exception e) {

            System.out.println("Invalid form data: " + e.getMessage());

            sendMessage(chatId,
                    "Unable to process your request. Please try again.");
        }
    }

    private void sendMessage(String chatId, String message) {

        sendMessageWithKeyboard(chatId, message, null);
    }

    private void sendMessageWithKeyboard(
            String chatId,
            String message,
            ObjectNode keyboard) {

        try {

            ObjectNode body = mapper.createObjectNode();

            body.put("chat_id", chatId);
            body.put("text", message);

            if (keyboard != null) {
                body.set("reply_markup", keyboard);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "sendMessage"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            JsonNode result = mapper.readTree(response.body());

            if (!result.path("ok").asBoolean()) {
                System.out.println("Telegram error: " + response.body());
            }

        } catch (Exception e) {

            System.out.println("Message error: " + e.getMessage());
        }
    }
}
