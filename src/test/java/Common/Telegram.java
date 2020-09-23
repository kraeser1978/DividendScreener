package Common;

import org.junit.Test;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Logger;

public class Telegram {
    private static Logger logger = Logger.getLogger(Telegram.class.getSimpleName());

    @Test
//    public void sendToTelegram() {
    public void sendToTelegram(String tickers) {
        String urlString = "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s";
        String apiToken = "723054247:AAErT50-HGdc0eiq53t0GyNK6opzG5h5X0Y";
        //Add chatId (given chatId is fake)
        String chatId = "@test_kraeser";
        String text = "AAPL-RM";
        urlString = String.format(urlString, apiToken, chatId, tickers);
        try {
            URL url = new URL(urlString);
            URLConnection conn = url.openConnection();
            InputStream is = new BufferedInputStream(conn.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
