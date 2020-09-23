package Common;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import org.apache.commons.math3.util.Precision;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;

public class MOEXData {
    private static Logger logger = Logger.getLogger(MOEXData.class.getSimpleName());
    public LinkedHashMap<String,String> allMOEXStocksMap = new LinkedHashMap<String,String>();
    public ArrayList<ArrayList<String>> allMOEXStocks = new ArrayList<ArrayList<String>>();
    public ArrayList<ArrayList<String>> outPerformingStocks = new ArrayList<>();
    public ArrayList<Double> imoexIndexPrices = new ArrayList<>();
    public ArrayList<Double> stockPrices = new ArrayList<>();
    String someTimeAgoStr,prevDateStr = null;
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Test
    public void filterStocksFor3MonthsTimeFrame() throws ParseException {
        //считываем котировки индекса ММВБ по границам периода
        getIMOEXHistoricalPrices(Calendar.MONTH,-3);
        getIMOEXHistoricalPrices(Calendar.DATE,-1);
        //фильтруем по капитализации
        filterByCompanyCap();
        //фильтруем по месячному объему торгов
        filterByMonthlyTradeVolume();
        //считываем котировки каждой акции из списка и фильтруем по приросту стоимости к ММВБ
        for (int i = 0; i< allMOEXStocks.size(); i++){
            String ticker = allMOEXStocks.get(i).get(0);
            stockPrices.clear();
            getStockHistoricalPrices(ticker,Calendar.MONTH,-3);
            getStockHistoricalPrices(ticker,Calendar.DATE,-1);
            logger.log(INFO,imoexIndexPrices.toString());
            String filteredTicker = filterOutPerformingStocks(ticker,"3 месяца");
            //добавляем отобранную акцию в отдельный массив
            if (filteredTicker != null)
                outPerformingStocks.add(allMOEXStocks.get(i));
        }
        sendFilteredTickersToTelegram();
        //считываем список всех компаний
//        getMOEXStocksList();
    }

    private void sendFilteredTickersToTelegram(){
        Telegram telegram = new Telegram();
        ArrayList<String> tickers = new ArrayList<>();
        String totalList = "";
        //извлекаем тикеры из массива результатов
        for (int i = 0; i< outPerformingStocks.size(); i++){
            String filteredTicker = outPerformingStocks.get(i).get(0);
            tickers.add(filteredTicker);
        }
        //собираем список тикеров в одну строку через запятую
        for (int t = 0; t< tickers.size(); t++){
            String currentTicker = tickers.get(t);
            totalList = totalList + "," + currentTicker;
        }
        //отправляем список в телеграм канал
        telegram.sendToTelegram(totalList);
    }

    public void filterByMonthlyTradeVolume(){
        HttpResponse<JsonNode> response = null;String result = null;
        ArrayList<ArrayList<String>> copyOfallMOEXStocks = new ArrayList<ArrayList<String>>();
        JSONObject jsonObject = null;JSONArray jsonArray = null,mData = null;
        String ticker = null; double tradeVolume = 0; double monthlyVolume = 0;
        double value = 0;
        //определяем начальную дату
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();
        cal.add(Calendar.MONTH,-1);
        Date oneMonthAgo = cal.getTime();
        String oneMonthStr = dateFormat.format(oneMonthAgo);
        String todayStr = dateFormat.format(today);
        for (int n = 0; n < allMOEXStocks.size(); n++){
            ticker = allMOEXStocks.get(n).get(0);
            response = getCompanyTradeVolumeData(ticker,oneMonthStr,todayStr);
            jsonObject = response.getBody().getObject().getJSONObject("history");
            jsonArray = jsonObject.getJSONArray("data");
            if (jsonArray.toString().equals("[]"))
                continue;
            for (int i = 0; i< jsonArray.length(); i++){
                mData = jsonArray.getJSONArray(i);
                tradeVolume = mData.getDouble(12);
                monthlyVolume = monthlyVolume + tradeVolume;
            }
            if (monthlyVolume > 200000000){
                String vol = Double.toString(monthlyVolume);
                allMOEXStocks.get(n).add(vol);
                copyOfallMOEXStocks.add(allMOEXStocks.get(n));
            }
        }
        logger.log(INFO,"список компаний с ежемесячным оборотом свыше 200 млн.руб. составлен");
        allMOEXStocks.clear();
        allMOEXStocks = (ArrayList<ArrayList<String>>) copyOfallMOEXStocks.clone();
    }

    @Test
    public void filterByCompanyCap(){
        HttpResponse<JsonNode> response = null;String result = null;
        JSONObject jsonObject,jsonObject1 = null;JSONArray jsonArray = null,mData = null;
        String marketType,ticker = null; Long cap = null;
        response = getCompanyTotalsData();
        jsonObject = response.getBody().getObject().getJSONObject("securities");
        jsonArray = jsonObject.getJSONArray("data");
        for (int i = 0; i< jsonArray.length(); i++){
            mData = jsonArray.getJSONArray(i);
            marketType = mData.getString(2);
            if (marketType.equals("MRKT")){
                ticker = mData.getString(0);
                cap = mData.getLong(22);
                //оставляем только те компании, чья среднемесячная капитализация свыше 500 млн.руб.
                if (cap > 500000000){
                    ArrayList<String> russianStock = new ArrayList<>();
                    russianStock.add(ticker);
                    russianStock.add(cap.toString());
                    allMOEXStocks.add(russianStock);
                }
            }
        }
        logger.log(INFO,"список компаний с капитализацией свыше 500 млн.руб. составлен");
    }

    public String filterOutPerformingStocks(String ticker, String timeFrame){
        //рассчитываем коэффициент роста индекса ММВБ за период
        double imoexIndexGrowthRate = Precision.round(imoexIndexPrices.get(1) * 100 / imoexIndexPrices.get(0) - 100,1);
        //рассчитываем коэффициент роста акции за период
        double currentStockGrowthRate = Precision.round(stockPrices.get(1) * 100 / stockPrices.get(0) - 100,1);
        //определяем, если акция обгоняла рост ММВБ
        int dev = Precision.compareTo(currentStockGrowthRate,imoexIndexGrowthRate,-1);
        if (dev > 0 )
            logger.log(INFO,"акция компании " + ticker + " обгоняла рост индекса ММВБ за " + timeFrame);
        else {
            logger.log(INFO,"акция компании " + ticker + " росла медленее индекса ММВБ за " + timeFrame);
            ticker = null;
        }
        logger.log(INFO,"рост индекса ММВБ за " + timeFrame + ": " + imoexIndexGrowthRate + "%");
        logger.log(INFO,"рост акции за " + timeFrame + ": " + currentStockGrowthRate + "%");
        return ticker;
    }

    public void getStockHistoricalPrices(String ticker, int timeFrame,int timeShift) throws ParseException {
        HttpResponse<JsonNode> response = null;String result = null;
        JSONObject jsonObject = null;JSONArray jsonArray = null,mData = null;
        double value = 0;
        //определяем начальную дату
        Calendar cal = Calendar.getInstance();
        if (timeFrame !=0 || timeShift !=0)
            cal.add(timeFrame,timeShift);
        Date someTimeAgo = cal.getTime();
//        Date someTimeAgo = dateFormat.parse("2017-09-24");
        //проверка на выходные/праздничные дни
        Calendar cl = Calendar.getInstance();
        cl.setTime(someTimeAgo);
        if ((cl.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) || (cl.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)){
            do {
                //берем предыдущий день и шлем запрос на него в ММВБ
                //отступаем на день назад до тех пор, пока запрос не вернет данные
                cl.add(Calendar.DATE,-1);
                calcTimeFrameDates(cl,someTimeAgo);
                jsonArray = getDataForPrevDate2(ticker,response);
                result = jsonArray.toString();
            }
            while (result.equals("[]"));
        } else{
            calcTimeFrameDates(cl,someTimeAgo);
            jsonArray = getDataForPrevDate2(ticker,response);
        }

        try {
            mData = jsonArray.getJSONArray(0);
        } catch (Exception e){
            logger.log(SEVERE,"нет данных по close price для " + ticker);
        }
        //берем close price по индексу ММВБ за выбранную дату
        try{
            value = mData.getDouble(11);
        } catch (Exception e){
            logger.log(SEVERE,"нет данных по close price для " + ticker);
        }
//        if (value > 0)
            stockPrices.add(value);
    }

    public void getIMOEXHistoricalPrices(int timeFrame,int timeShift) throws ParseException {
        HttpResponse<JsonNode> response = null;String result = null;
        JSONObject jsonObject = null;JSONArray jsonArray = null,mData = null;
        //определяем начальную дату
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance();
        if (timeFrame !=0 || timeShift !=0)
            cal.add(timeFrame,timeShift);
        Date someTimeAgo = cal.getTime();
//        Date someTimeAgo = dateFormat.parse("2017-09-24");
        //проверка на выходные/праздничные дни
        Calendar cl = Calendar.getInstance();
        cl.setTime(someTimeAgo);
        if ((cl.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) || (cl.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)){
            do {
                //берем предыдущий день и шлем запрос на него в ММВБ
                //отступаем на день назад до тех пор, пока запрос не вернет данные
                cl.add(Calendar.DATE,-1);
                calcTimeFrameDates(cl,someTimeAgo);
                jsonArray = getDataForPrevDate(response);
                result = jsonArray.toString();
            }
            while (result.equals("[]"));
        } else{
            calcTimeFrameDates(cl,someTimeAgo);
            jsonArray = getDataForPrevDate(response);
        }
        mData = jsonArray.getJSONArray(0);
        //берем close price по индексу ММВБ за выбранную дату
        imoexIndexPrices.add(mData.getDouble(5));
    }

    private void calcTimeFrameDates(Calendar cl,Date someTimeAgo){
        someTimeAgoStr = null;prevDateStr = null;
        Date prevDate = cl.getTime();
        someTimeAgoStr = dateFormat.format(someTimeAgo);
        prevDateStr = dateFormat.format(prevDate);
    }

    private JSONArray getDataForPrevDate2(String ticker, HttpResponse<JsonNode> response){
        response = getStockData(ticker,prevDateStr,someTimeAgoStr);
        JSONObject jsonObject = response.getBody().getObject().getJSONObject("history");
        JSONArray jsonArray = jsonObject.getJSONArray("data");
        return jsonArray;
    }

    private JSONArray getDataForPrevDate(HttpResponse<JsonNode> response){
        response = getIMOEXIndexData(prevDateStr,someTimeAgoStr);
        JSONObject jsonObject = response.getBody().getObject().getJSONObject("history");
        JSONArray jsonArray = jsonObject.getJSONArray("data");
        return jsonArray;
    }

    @Test
    public void getMOEXStocksList() throws ParseException {
        String ticker,stockName = null;
        HttpResponse<JsonNode> response = null;
        JSONObject jsonObject,jsonObject1 = null;JSONArray jsonArray,jsonArray1,jsonArray2,mData = null;Object obj = null;
        response = getMoexSecListPageData(0);
        jsonObject1 = response.getBody().getObject().getJSONObject("history.cursor");
        jsonArray1 = jsonObject1.getJSONArray("data");
        jsonArray2 = jsonArray1.getJSONArray(0);
        int totalStocks = Integer.parseInt(jsonArray2.get(1).toString());
        int stocksPerPage = Integer.parseInt(jsonArray2.get(2).toString());
        int count = totalStocks / stocksPerPage + 1;
        for (int n = 0; n <count; n++){
            int pageStartPos = n * stocksPerPage;
            response = getMoexSecListPageData(pageStartPos);
            jsonObject = response.getBody().getObject().getJSONObject("history");
            jsonArray = jsonObject.getJSONArray("data");
            for (int i = 0; i< jsonArray.length(); i++){
                mData = jsonArray.getJSONArray(i);
                stockName = mData.getString(2);
                ticker = mData.getString(3);
                ArrayList<String> russianStock = new ArrayList<>();
                russianStock.add(ticker);
                russianStock.add(stockName);
//                allMOEXStocks.add(russianStock);
                allMOEXStocksMap.put(ticker,stockName);
            }
        }
    }

    private HttpResponse<JsonNode> getStockData(String ticker, String dateFrom, String dateTill){
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get("http://iss.moex.com/iss/history/engines/stock/markets/shares/boards/tqbr/securities/" +ticker + ".json?from=" +
                    dateFrom + "&till=" + dateTill)
                    .asJson();
        } catch (Exception e) {
            logger.log(SEVERE, "запрос к рыночным данным ММВБ отработал с ошибкой");
            response = null;
        }
        logger.log(INFO, "данные по компании " + ticker + " считаны успешно");
        return response;
    }

    private HttpResponse<JsonNode> getIMOEXIndexData(String dateFrom, String dateTill){
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get("http://iss.moex.com/iss/history/engines/stock/markets/index/boards/SNDX/securities/IMOEX.json?from=" +
                    dateFrom + "&till=" + dateTill)
                    .asJson();
        } catch (Exception e) {
            logger.log(SEVERE, "запрос к рыночным данным ММВБ отработал с ошибкой");
            response = null;
        }
        logger.log(INFO, "список тикеров считаны успешно");
        return response;
    }

    private HttpResponse<JsonNode> getMoexSecListPageData(int pageStartPos){
        HttpResponse<JsonNode> response = null;
        String command = "http://iss.moex.com/iss/history/engines/stock/markets/shares/boards/tqbr/securities.json";
        if (pageStartPos > 0)
            command = command + "?start=" + Integer.toString(pageStartPos);
        try {
            response = Unirest.get(command)
                    .asJson();
        } catch (Exception e) {
            logger.log(SEVERE, "запрос к рыночным данным ММВБ отработал с ошибкой");
            response = null;
        }
        logger.log(INFO, "список тикеров считаны успешно");
        return response;
    }

    private HttpResponse<JsonNode> getCompanyTotalsData(){
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get("http://iss.moex.com/iss/history/engines/stock/totals/securities.json")
                    .asJson();
        } catch (Exception e) {
            logger.log(SEVERE, "запрос к рыночным данным ММВБ отработал с ошибкой");
            response = null;
        }
        logger.log(INFO, "данные по компаниям считаны успешно");
        return response;
    }

    private HttpResponse<JsonNode> getCompanyTradeVolumeData(String ticker,String dateFrom, String dateTill){
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get("http://iss.moex.com/iss/history/engines/stock/markets/shares/boards/tqbr/securities/" + ticker + ".json?from=" +
                    dateFrom + "&till=" + dateTill)
                    .asJson();
        } catch (Exception e) {
            logger.log(SEVERE, "запрос к рыночным данным ММВБ по тикеру " + ticker + " отработал с ошибкой");
            response = null;
        }
        logger.log(INFO, "данные по тикеру " + ticker + " считаны успешно");
        return response;
    }
}
