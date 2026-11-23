
package com.irfan;

import com.irfan.telegram.TelegramBot;

public class Main {

    public static void main(String[] args) {

        String token = System.getenv("TELEGRAM_BOT_TOKEN");

        if (token == null || token.isBlank()) {
            System.out.println(
                    "ERROR: TELEGRAM_BOT_TOKEN is missing!"
            );
            return;
        }

        TelegramBot bot = new TelegramBot(token);

        System.out.println("================================");
        System.out.println(" BOOKMYSHOW TELEGRAM BOT");
        System.out.println("================================");

        bot.start();
    }
}
