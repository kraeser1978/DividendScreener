package Common;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RapidAPIData {
    private static Logger logger = Logger.getLogger(RapidAPIData.class.getSimpleName());
    public ArrayList<String> tickers = new ArrayList<String>();
    final String API_URL = "https://apidojo-yahoo-finance-v1.p.rapidapi.com";
    final String rapidHost = "x-rapidapi-host";final String rapidHostValue = "apidojo-yahoo-finance-v1.p.rapidapi.com";
    final String rapidKey = "x-rapidapi-key";final String rapidKeyValue = "";

    public String getDateAsEpoch(int datePeriodType, int dateShiftInveral) {
        Calendar cal = Calendar.getInstance();
        cal.add(datePeriodType, dateShiftInveral);
        long epochDate = cal.getTimeInMillis() / 1000;
        String epochStr = String.valueOf(epochDate);
        return epochStr;
    }

    @Test
    public void run() throws UnirestException, IOException {
        String startDate = getDateAsEpoch(Calendar.YEAR,-10);
        String endDate = getDateAsEpoch(Calendar.YEAR,0);
        getDividendHistoryData(startDate,endDate,"GD");
//        getPriceChartData("ADM");
    }

    public void getDividendHistoryData(String startDate, String endDate, String ticker) throws UnirestException, IOException {
        HttpResponse<JsonNode> response = Unirest.get(API_URL + "/stock/v2/get-historical-data?frequency=1wk&filter=div" +
                "&period1=" + startDate + "&period2=" + endDate + "&symbol=" + ticker)
                .header(rapidHost, rapidHostValue)
                .header(rapidKey, rapidKeyValue)
                .asJson();
        logger.log(Level.INFO, "запрос отработал");
        JSONArray eventsDataArray = response.getBody().getObject().getJSONArray("eventsData");
        JSONObject jObject;
        ArrayList<Double> dividends = new ArrayList<>();
        for (int i=0; i < eventsDataArray.length();i++){
            jObject = eventsDataArray.getJSONObject(i);
            double divValue = jObject.optDouble("amount");
            dividends.add(divValue);
        }
        //выполняем проверку на то, что дивиденды за выбранный период поступательно росли
        boolean flag;
        Collections.sort(dividends);
        for (int t=1; t < dividends.size(); t++){
            double currentValue = dividends.get(t-1);
            double nextValue = dividends.get(t);
            if (nextValue < currentValue) {
                logger.log(Level.INFO, "размер дивиденда компании " + ticker + " некоторое время снижался, поэтом исключаем ее из выборки");
                flag = false;
                break;
            } else flag = true;
        }

        Unirest.shutdown();
    }

    public void getPriceChartData(String ticker) throws UnirestException, IOException {
        HttpResponse<JsonNode> response = Unirest.get(API_URL + "/market/get-charts" +
                "?comparisons=SDY&region=US&lang=en&symbol=" + ticker + "&interval=1wk&range=10y")
                .header(rapidHost, rapidHostValue)
                .header(rapidKey, rapidKeyValue)
                .asJson();
        logger.log(Level.INFO, "запрос отработал");
        JSONObject chart = response.getBody().getObject().getJSONObject("chart");
        JSONArray result = chart.getJSONArray("result");
        JSONObject jObject = result.getJSONObject(0);

        //проверка на то, что прирост стоимости отобранной акции больше роста эталонного ETF SDY
        //считываем стоимость актива
        JSONObject indicators = jObject.getJSONObject("indicators");
        JSONArray adj = indicators.getJSONArray("adjclose");
        JSONObject quotesObject = adj.getJSONObject(0);
        JSONArray adjcloseQuotes = quotesObject.getJSONArray("adjclose");
        double firstClose = adjcloseQuotes.optDouble(0);
        //считываем стоимость SDY на предыдущий день
        double lastClose = adjcloseQuotes.optDouble(adjcloseQuotes.length()-1);
        //вычисляем прирост стоимости в %
        double increase = (lastClose * 100 / firstClose) - 100;

        //считываем стоимость SDY на первый день заданного временного периода
        JSONArray comparisons = jObject.getJSONArray("comparisons");
        JSONObject comparisonsObject = comparisons.getJSONObject(0);
        JSONArray SDYclosePrices = comparisonsObject.getJSONArray("close");
        double SDYFirstClose = SDYclosePrices.optDouble(0);
        //считываем стоимость SDY на предыдущий день
        double SDYLastClose = SDYclosePrices.optDouble(SDYclosePrices.length()-1);
        //вычисляем прирост стоимости в %
        double SDYIncrease = ((SDYLastClose * 100 / SDYFirstClose) - 100) * 2;

        Unirest.shutdown();
    }
}
