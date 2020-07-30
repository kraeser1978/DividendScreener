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

import static Common.DivsCoreData.props;

public class RapidAPIData {
    private static Logger logger = Logger.getLogger(RapidAPIData.class.getSimpleName());
    public ArrayList<String> tickers = new ArrayList<String>();
    final String API_URL = props.APIURL();final String rapidHost = props.rapidHost();final String rapidHostValue = props.rapidHostValue();
    final String rapidKey = props.rapidKey();final String rapidKeyValue = props.rapidKeyValue();

    public boolean compareCompanyQuotes(ArrayList<Double> quotes){
        //метод выполняет сравнение каждого последующего значения элемента массива с предыдущим
        //если значение какого-то элемента меньше предыдущего, компания исключается из выборки
        boolean flag = false;
        Collections.reverse(quotes);
        for (int t=1; t < quotes.size(); t++){
            double currentValue = quotes.get(t-1);
            double nextValue = quotes.get(t);
            if (nextValue < currentValue) {
                flag = false;
                break;
            } else flag = true;
        }
        return flag;
    }

    public ArrayList<String> checkIncomeGrowth() throws UnirestException, IOException {
        //метод проверяет актив на наличие поступательного роста показателей Net Income и Operating Income за 4 последних года
        //если значение показателя сокращалось внутри 4х летнего интервала , то компания исключается из выборки
        ArrayList<String> tickersFiltered = new ArrayList<String>();
        boolean flag = false;
        for (int i = 0; i < tickers.size(); i++){
            logger.log(Level.INFO, "считываем показатели Net Income и Operating Income по " + tickers.get(i) + " за 4 последних года...");
            JSONArray incomeStatementHistory = getStockIncomeStatementData(tickers.get(i));
            ArrayList<Double> netIncVals = getNetIncomeValues(incomeStatementHistory);
            ArrayList<Double> opIncVals = getOperatingIncomeValues(incomeStatementHistory);
            logger.log(Level.INFO, "выполняем проверку на то, что показатели Net Income и Operating Income за выбранный период поступательно росли...");
            boolean netIncomeResult = compareCompanyQuotes(netIncVals);
            boolean operatingIncomeResult = compareCompanyQuotes(opIncVals);
            if (netIncomeResult && operatingIncomeResult) {
                logger.log(Level.INFO, "Net Income или Operating Income компании " + tickers.get(i) + " поступательно росли все время в течение выбранного периода - оставляем ее в выборке");
                tickersFiltered.add(tickers.get(i));
            } else logger.log(Level.INFO, "Net Income или Operating Income компании " + tickers.get(i) + " в течение выбранного периода некоторое время снижались - исключаем ее из выборки");
        }
        //очищаем исходный массив
        tickers.clear();
        //копируем значения из отфильтрованного массива в исходный
        tickers = (ArrayList<String>)tickersFiltered.clone();
        return tickers;
    }

    public ArrayList<Double> getNetIncomeValues(JSONArray incomeStatementHistory){
        ArrayList<Double> netIncome = new ArrayList<>();
        JSONObject isdObject;
        for (int i=0; i < incomeStatementHistory.length();i++){
            isdObject = incomeStatementHistory.getJSONObject(i);
            JSONObject netIncomeObj = isdObject.getJSONObject("netIncome");
            double netIncomeValue = netIncomeObj.optDouble("raw");
            netIncome.add(netIncomeValue);
        }
        return netIncome;
    }

    public ArrayList<Double> getOperatingIncomeValues(JSONArray incomeStatementHistory){
        ArrayList<Double> operatingIncome = new ArrayList<>();
        JSONObject isdObject;
        for (int i=0; i < incomeStatementHistory.length();i++){
            isdObject = incomeStatementHistory.getJSONObject(i);
            JSONObject netIncomeObj = isdObject.getJSONObject("operatingIncome");
            double operatingIncomeValue = netIncomeObj.optDouble("raw");
            operatingIncome.add(operatingIncomeValue);
        }
        return operatingIncome;
    }

    public JSONArray getStockIncomeStatementData(String ticker) throws UnirestException {
        HttpResponse<JsonNode> response = Unirest.get(API_URL + "/stock/v2/get-financials?symbol=" + ticker)
                .header(rapidHost, rapidHostValue)
                .header(rapidKey, rapidKeyValue)
                .asJson();
        logger.log(Level.INFO, "запрос отработал");
        JSONObject jsonObject = response.getBody().getObject().getJSONObject("incomeStatementHistory");
        JSONArray incomeStatementHistory = jsonObject.getJSONArray("incomeStatementHistory");
        return incomeStatementHistory;
    }

    public String getDateAsEpoch(int datePeriodType, int dateShiftInveral) {
        Calendar cal = Calendar.getInstance();
        cal.add(datePeriodType, dateShiftInveral);
        long epochDate = cal.getTimeInMillis() / 1000;
        String epochStr = String.valueOf(epochDate);
        return epochStr;
    }

    public ArrayList<String> checkDividendsGrowth() throws UnirestException, IOException {
        //метод проверяет актив на наличие поступательного роста его дивидендов в течение 10 прошедших лет
        //если компания на каком-либо участке исторических данных снижала выплаты по дивидендам, она исключается из выборки
        String startDate = getDateAsEpoch(Calendar.YEAR,-10);
        String endDate = getDateAsEpoch(Calendar.YEAR,0);
        ArrayList<String> tickersFiltered = new ArrayList<String>();
        boolean flag = false;
        for (int i = 0; i < tickers.size(); i++){
            logger.log(Level.INFO, "считываем дивиденды по " + tickers.get(i) + " за последние 10 лет...");
            ArrayList<Double> dividends = getDividendHistoryData(startDate,endDate,tickers.get(i));
            logger.log(Level.INFO, "выполняем проверку на то, что дивиденды за выбранный период поступательно росли...");
            flag = compareCompanyQuotes(dividends);
            if (flag) {
                logger.log(Level.INFO, "размер дивиденда компании " + tickers.get(i) + " поступательно рос все время в течение выбранного периода - оставляем ее в выборке");
                tickersFiltered.add(tickers.get(i));
            } else logger.log(Level.INFO, "размер дивиденда компании " + tickers.get(i) + " в течение выбранного периода некоторое время снижался - исключаем ее из выборки");
        }
        //очищаем исходный массив
        tickers.clear();
        //копируем значения из отфильтрованного массива в исходный
        tickers = (ArrayList<String>)tickersFiltered.clone();
        return tickers;
    }

    public ArrayList<String> compareStockAgainstEthalonETF() throws IOException, UnirestException {
        //метод сравнивает прирост стоимости акции компании с приростом стоимости эталонного ETF SDY за прошедшие 10 лет
        //если актив вырос меньше SDY, компания исключается из выборки
        ArrayList<String> tickersFiltered = new ArrayList<String>();
        double ethalonValue = 0;
        for (int i = 0; i < tickers.size(); i++){
            logger.log(Level.INFO, "считываем котировки " + tickers.get(i) + " за последние 10 лет...");
            JSONObject chartsData = getPriceChartData(tickers.get(i));
            JSONArray assetQuotes = getStockQuotes(chartsData);
            logger.log(Level.INFO, "рассчитываем рост стоимости " + tickers.get(i) + " за период...");
            double assetValue = getPriceIncrease(assetQuotes);
            if (ethalonValue == 0){
                logger.log(Level.INFO, "считываем котировки эталонного ETF SDY за последние 10 лет...");
                JSONArray SDYQuotes = getEthalonETFQuotes(chartsData);
                logger.log(Level.INFO, "рассчитываем рост стоимости эталонного ETF SDY за период...");
                ethalonValue = getPriceIncrease(SDYQuotes);
            }
            logger.log(Level.INFO, "сравниваем прирост стоимости " + tickers.get(i) + " с приростом эталонного ETF SDY...");
            if (assetValue > ethalonValue * 2) {
                logger.log(Level.INFO, tickers.get(i) + " вырос сильнее эталона - оставляем его в выборке");
                tickersFiltered.add(tickers.get(i));
            } else logger.log(Level.INFO, tickers.get(i) + " вырос слабее эталона - исклкючаем его из выборки");
        }
        //очищаем исходный массив
        tickers.clear();
        //копируем значения из отфильтрованного массива в исходный
        tickers = (ArrayList<String>)tickersFiltered.clone();
        return tickers;
    }

    public ArrayList<Double> getDividendHistoryData(String startDate, String endDate, String ticker) throws UnirestException, IOException {
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
        return dividends;
    }

    public JSONArray getStockQuotes(JSONObject jObject){
        //считываем стоимость актива
        JSONObject indicators = jObject.getJSONObject("indicators");
        JSONArray adj = indicators.getJSONArray("adjclose");
        JSONObject quotesObject = adj.getJSONObject(0);
        JSONArray adjcloseQuotes = quotesObject.getJSONArray("adjclose");
        return adjcloseQuotes;
    }

    public JSONArray getEthalonETFQuotes(JSONObject jObject){
        //считываем стоимость SDY
        JSONArray comparisons = jObject.getJSONArray("comparisons");
        JSONObject comparisonsObject = comparisons.getJSONObject(0);
        JSONArray SDYclosePrices = comparisonsObject.getJSONArray("close");
        return SDYclosePrices;
    }

    public double getPriceIncrease(JSONArray quotes){
        //считываем стоимость актива на первый день заданного периода
        double firstClose = quotes.optDouble(0);
        //считываем стоимость актива на последний день заданного периода
        double lastClose = quotes.optDouble(quotes.length()-1);
        //вычисляем прирост стоимости в %
        double increase = (lastClose * 100 / firstClose) - 100;
        return increase;
    }

    public JSONObject getPriceChartData(String ticker) throws UnirestException, IOException {
        HttpResponse<JsonNode> response = Unirest.get(API_URL + "/market/get-charts" +
                "?comparisons=SDY&region=US&lang=en&symbol=" + ticker + "&interval=1wk&range=10y")
                .header(rapidHost, rapidHostValue)
                .header(rapidKey, rapidKeyValue)
                .asJson();
        logger.log(Level.INFO, "запрос отработал");
        JSONObject chart = response.getBody().getObject().getJSONObject("chart");
        JSONArray result = chart.getJSONArray("result");
        JSONObject jObject = result.getJSONObject(0);
        return jObject;
    }
}
