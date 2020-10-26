package Common;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.TelegramBotAdapter;
import com.pengrad.telegrambot.model.request.InputFile;
import com.pengrad.telegrambot.model.request.InputFileBytes;
import org.junit.Test;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiValidationException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Logger;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;

public class Telegram {
    private static Logger logger = Logger.getLogger(Telegram.class.getSimpleName());
    public static DivsCoreData divsCoreData;

    @Test
    public void sendString(String tickers) {
        String urlString = "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s";
        String apiToken = "723054247:AAErT50-HGdc0eiq53t0GyNK6opzG5h5X0Y";
        //Add chatId (given chatId is fake)
        String chatId = "@test_kraeser";
        urlString = String.format(urlString, apiToken, chatId, tickers);
        try {
            URL url = new URL(urlString);
            URLConnection conn = url.openConnection();
            InputStream is = new BufferedInputStream(conn.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendText(String tickers) {
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.post("https://api.telegram.org/bot723054247:AAErT50-HGdc0eiq53t0GyNK6opzG5h5X0Y" +
                    "/sendMessage?chat_id=@test_kraeser&text=" + tickers)
                    .asJson();
        } catch (Exception e) {
            logger.log(SEVERE, "ошибка");
            response = null;
        }
        logger.log(INFO, "успешно");
    }

    public void sendFile(String folder, String pdfFile) throws Exception {
        divsCoreData = new DivsCoreData();
        divsCoreData.getProps(folder);
        String token = divsCoreData.props.telegramBotTtoken();
        TelegramBot bot = TelegramBotAdapter.build(token);
        InputFile inputFile = new InputFile("text/plain",new File(pdfFile));
        bot.sendDocument("@test_kraeser",inputFile,null,null);
    }
}
