package Common;

import com.codeborne.selenide.Configuration;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import static Common.DivsCoreData.props;
import java.util.concurrent.Future;

public class RapidAPIData {
    private static Logger logger = Logger.getLogger(RapidAPIData.class.getSimpleName());
    public ArrayList<String> tickers = new ArrayList<String>();
    public ArrayList<String> tickersPreviousSelection = new ArrayList<String>();
    final String API_URL = props.APIURL();final String rapidHost = props.rapidHost();final String rapidHostValue = props.rapidHostValue();
    final String rapidKey = props.rapidKey();final String rapidKeyValue = props.rapidKeyValue();
    public ArrayList<String> uniqueTickers = new ArrayList<String>();
    Double yieldValue,priceValue,peValue,payoutRatioValue,dividendRateValue;
    Long marketCapValue;
    Stocks stockObj;
    String compName, ticker;
    public LinkedHashMap<String, Stocks> stocksListMap = new LinkedHashMap<>();
    public LinkedHashMap<String, Stocks> copyOfStocksListMap = new LinkedHashMap<>();
    public List<Stocks> listOfStocks = new ArrayList<>();
    ObjectMapper mapper = new ObjectMapper();
    ArrayList<Long> dividendDates = new ArrayList<>();
    ArrayList<Double> dividendAmount = new ArrayList<>();
    ArrayList<HttpResponse> stockSummaryDataResponses = new ArrayList<>();
    CountDownLatch responseWaiter;
    boolean includeDividends;

    private void runAsyncRequest(String ticker){
        Future < HttpResponse < JsonNode >  > future1 = Unirest.get(API_URL + "/stock/v2/get-statistics?region=US&symbol=" + ticker)
                .header(rapidHost, rapidHostValue)
                .header(rapidKey, rapidKeyValue)
                .asJsonAsync(new Callback < JsonNode > () {
                    public void failed(UnirestException e) {
                        logger.log(Level.SEVERE, "запросы выдал ошибку");
                        responseWaiter.countDown();
                    }
                    public void completed(HttpResponse < JsonNode > response) {
                        stockSummaryDataResponses.add(response);
                        responseWaiter.countDown();
                    }
                    public void cancelled() {
                        logger.log(Level.SEVERE, "запрос отменен");
                        responseWaiter.countDown();
                    }
                });
    }

    public boolean isDividendPaidOffFor15Years(String ticker){
        boolean flag = false;
        int isDividend15Years = dividendDates.size();
        if (isDividend15Years < 60) {
            logger.log(Level.INFO, "компания " + ticker + " выплачивала дивиденды меньше 15 лет подряд - исключаем ее из выборки...");
            if (stocksListMap.get(ticker) != null)
                stocksListMap.get(ticker).changeCriteriaExecutionStatus("Yrs",props.testFailed());
            flag = false;
        } else flag = true;
        return flag;
    }

    public boolean isDividendGrewFor10Years(String ticker){
        logger.log(Level.INFO, "метод проверяет актив на наличие поступательного роста его дивидендов в течение 10 прошедших лет");
        boolean flag = false;
        ArrayList<Double> dividends10Years = new ArrayList<>();
        for (int i=0; i< 41;i++) dividends10Years.add(dividendAmount.get(i));
        logger.log(Level.INFO, "выполняем проверку на то, что дивиденды за последние 10 лет поступательно росли...");
        flag = compareCompanyQuotes(dividends10Years);
        if (flag) {
            logger.log(Level.INFO, "размер дивиденда компании " + ticker + " поступательно рос все время в течение последних 10 лет");
            stocksListMap.get(ticker).changeCriteriaExecutionStatus("Yrs",props.testPassed());
            stocksListMap.get(ticker).changeCriteriaExecutionStatus("DivCheck",props.testPassed());
        } else {
            logger.log(Level.INFO, "размер дивиденда компании " + ticker + " в течение последних 10 лет некоторое время снижался");
            stocksListMap.get(ticker).changeCriteriaExecutionStatus("Yrs",props.testPassed());
            stocksListMap.get(ticker).changeCriteriaExecutionStatus("DivCheck",props.testFailed());
        }
        return flag;
    }

    public boolean isDivPayoutFrequencyAcceptable(){
        //метод проверяет частоту последних дивидендных выплат - не реже 4 раз в год
        boolean flag = false;
        Long lastDate = dividendDates.get(0);
        Long lastFifthDate = dividendDates.get(4);
        Calendar c1 = Calendar.getInstance();
        c1.setTimeInMillis(lastDate * 1000);
        Calendar c2 = Calendar.getInstance();
        c2.setTimeInMillis(lastFifthDate * 1000);
        int lastDateYear = c1.get(Calendar.YEAR);
        int lastDateMonth = c1.get(Calendar.MONTH);
        int fifthDateYear = c2.get(Calendar.YEAR);
        int fifthDateMonth = c2.get(Calendar.MONTH);
        if (lastDateYear != fifthDateYear){
            if (lastDateMonth != fifthDateMonth) flag = false;
            else flag = true;
        } else flag = true;
        return flag;
    }

    public boolean isDivGrowthRateAcceptable(){
        //метод проверяет рост дивидендных выплат - не менее 2% раз в год
        boolean flag = false;
        for (int i = 1; i < dividendAmount.size(); i++){
            double currentValue = dividendAmount.get(i-1);
            double nextValue = dividendAmount.get(i);
            if (nextValue < currentValue) {
                double diff = (currentValue - nextValue) / nextValue * 100;
                if (diff < 2) flag = false;
                else flag = true;
                break;
            }
        }
        return flag;
    }

    public boolean isDivLastIncreasedDateAcceptable(){
        //метод проверяет дату последнего повышения дивидендов - не позже, чем год назад
        boolean flag = false;
        Calendar yearAgo = Calendar.getInstance();
        yearAgo.add(Calendar.YEAR,-1);
        Date expectedDate = yearAgo.getTime();
        Calendar c2 = Calendar.getInstance();
        for (int i = 1; i < dividendAmount.size(); i++){
            double currentValue = dividendAmount.get(i-1);
            double nextValue = dividendAmount.get(i);
            if (nextValue < currentValue) {
                Long divLastIncreased = dividendDates.get(i-1);
                c2.setTimeInMillis(divLastIncreased * 1000);
                Date actualDate = c2.getTime();
                int diff = actualDate.compareTo(expectedDate);
                if (diff <= 0) flag = false;
                else flag = true;
                break;
            }
        }
        return flag;
    }

    public boolean isEPSPayOutAcceptable(String ticker){
        //метод вычисляет и проверяет EPS%Payout - доля прибыли, направляемая на выплату дивидендов, не более 70%
        boolean flag = false;
        //алгоритм, если значение было ранее извлечено через API из поля payoutRatio
        if (payoutRatioValue != null)
            if (payoutRatioValue < 70) return true;
            else return false;
        double lastDivValue = dividendAmount.get(0);
        double divAnnualized = lastDivValue * 4;

        HttpResponse<JsonNode> response = getFinancialsTabData(ticker);
        if (response ==  null) return false;
        JSONArray basicEPSData;
        JSONObject idsObject,timeSeriesData;
        try {
            timeSeriesData = response.getBody().getObject().getJSONObject("timeSeries");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос timeSeries отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            e.printStackTrace();
            return false;
        }

        try {
            basicEPSData = timeSeriesData.getJSONArray("annualBasicEPS");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос annualBasicEPS отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            e.printStackTrace();
            return false;
        }
        //берем последнее значение EPS
        idsObject = basicEPSData.getJSONObject(basicEPSData.length()- 1);
        JSONObject val = idsObject.getJSONObject("reportedValue");
        double epsValue = val.optDouble("raw");
        //проверяем, что EPS не отрицательное и больше 0.01
        if (epsValue > 0.01) {
            //вычисляес Payout Ratio
            payoutRatioValue = divAnnualized / epsValue * 100;
            //сравниваем значение Payout Ratio с ожидаемым
            if (payoutRatioValue < 70) flag = true;
        }
        return flag;
    }

    public boolean filterByDividendRelatedCriterias() {
        LinkedHashMap<String, Stocks> copyOfStocksListMap = new LinkedHashMap<>();
        String startDate = getDateAsEpoch(Calendar.YEAR,-15);
        String endDate = getDateAsEpoch(Calendar.YEAR,0);
        boolean flag = false;
        for (Map.Entry<String, Stocks> entry : stocksListMap.entrySet()){
            String ticker = entry.getKey();
            if (!getDividendHistoryData(startDate,endDate,ticker)) continue;
            if (!isDividendPaidOffFor15Years(ticker)) continue;
            if (!isDivPayoutFrequencyAcceptable()) continue;
            if (!isDivGrowthRateAcceptable()) continue;
            if (!isDivLastIncreasedDateAcceptable()) continue;
            if (!isEPSPayOutAcceptable(ticker)) continue;
            //копируем прошедшую все обязательные критерии компанию в новый хешман
            copyOfStocksListMap.put(ticker,stocksListMap.get(ticker));
        }
        stocksListMap.clear();
        stocksListMap = (LinkedHashMap<String, Stocks>) copyOfStocksListMap.clone();
        for (Map.Entry<String, Stocks> entry : stocksListMap.entrySet()){
            String ticker = entry.getKey();
            isDividendGrewFor10Years(ticker);
        }
        return true;
    }

    public void cleanUpUSMarketsTickersLists(ArrayList<String> tickersFromExcel){
        //метод отсеивает дубликаты тикеров из Finnhub файла и DripTools таблицы
        for (int i=0; i<tickersFromExcel.size();i++){
            String tickerFromExcel = tickersFromExcel.get(i);
            if (tickers.contains(tickerFromExcel)) tickers.remove(tickerFromExcel);
        }
        logger.log(Level.INFO, "общий список тикеров очищен от тикеров из таблицы DripInvesting Tools");
    }

    public void getStocksListFromFinnhub() {
        logger.log(Level.INFO, "запрашиваем общий список тикеров для Common Stock из Finnhub marked data...");
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get("https://finnhub.io/api/v1/stock/symbol?exchange=US&currency=USD&securityType=Common%20Stock&token=" + props.finnhubToken())
                    .asJson();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос к Finnhub marked data отработал с ошибкой ");
            e.printStackTrace();
        }
        JSONArray jsonArray = null;
        try{
            jsonArray = response.getBody().getArray();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос к Finnhub marked data отработал с ошибкой - пропускаем компанию");
            e.printStackTrace();
        }
        //фильтруем по кодам американских бирж, исключаем OTC инструменты
        JSONObject jsonObject = null;
        for (int i=0; i < jsonArray.length();i++){
            try {
                jsonObject = jsonArray.getJSONObject(i);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "ошибка");
            }
            String assetType = jsonObject.optString("mic");
            if (assetType.contains("X")){
                String ticker = jsonObject.optString("symbol");
                tickers.add(ticker);
            }
        }
        logger.log(Level.INFO, "список тикеров считаны успешно");
    }

    public boolean isStockListCanBeReUsed(String fileName, int numOfDaysToCheck) throws IOException, ParseException {
        boolean flag = false;
        fileName = Configuration.reportsFolder + fileName;
        File stocksFile = new File(fileName);
        if (stocksFile.exists()) {
            Path file = Paths.get(fileName);
            BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
            FileTime modified = attr.lastModifiedTime();
            DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE,-numOfDaysToCheck);
            Date daysBehind = cal.getTime();
            Date fileModifiedDate = dateFormat.parse(dateFormat.format(modified.toMillis()));
            Date daysBehindDate = dateFormat.parse(dateFormat.format(daysBehind.getTime()));
            int dev = fileModifiedDate.compareTo(daysBehindDate);
            //если файл был сгенерен до установленного срока, т.е. относительно свежий, считываем его и пропускаем проверку по фундаментальным критериям
            //если файл был сгенерен ранее установленного срока, т.е. устарел, не считываем его, а заново выполняем все проверки по всем критериям с нуля
            if (dev > 0){
                List<Stocks> myObjects = mapper.readValue(new File(fileName), new TypeReference<List<Stocks>>(){});
                tickers.clear();
                for (int i = 0; i < myObjects.size(); i++){
                    String ticker = myObjects.get(i).getTicker();
                    stocksListMap.put(ticker,myObjects.get(i));
                    tickers.add(ticker);
                }
                flag = true;
            }
        }
        return  flag;
    }

    public void monthlyFilterByFundamentals(String outputFileName) throws IOException, InterruptedException {
        logger.log(Level.INFO, "фильтрация списка компаний по фундаментальным мультипликаторам:");
        logger.log(Level.INFO, "по продолжительности непрерывных дивидендных выплат  - от 15 лет подряд");
        logger.log(Level.INFO, "по marketCap  - от 2 млрд.долл.");
        String fileName = Configuration.reportsFolder + outputFileName;
        int cycles = tickers.size() / 5;
        int remainder = tickers.size() - (cycles * 5);
        int roundSize = tickers.size() - remainder;
        includeDividends = true;
        for (int i = 0; i < roundSize; i+=5) { //обработка списка тикеров общим числом кратным 5
            responseWaiter = new CountDownLatch(5);
            getAsyncData(i,i+5);
            parseAsyncStockSummaryData(outputFileName);
        }
        responseWaiter = new CountDownLatch(remainder);//обработка остатка (последняя партия тикеров меньше 5)
        getAsyncData(roundSize,tickers.size());
        parseAsyncStockSummaryData(outputFileName);
        //подкручиваем формат файла для последующего импорта в других сессиях приложения
        String contents = FileUtils.readFileToString(new File(fileName),"UTF-8");
        String newContents = contents.replace("}{","},{");
        newContents = "[" + newContents + "]";
        FileUtils.write(new File(fileName),newContents,"UTF-8",false);
    }

    public void getAsyncData(int i, int maxCount) throws InterruptedException {
        for (int t = i; t < maxCount; t++){
            String ticker = tickers.get(t);
            logger.log(Level.INFO, "считываем рыночные данные по " + ticker + " ...");
            runAsyncRequest(ticker);
        }
        responseWaiter.await();
        responseWaiter = new CountDownLatch(0);
    }

    public Stocks addStockAsObj(String ticker){
        stockObj = new Stocks();
        stockObj.setTicker(ticker);
        stockObj.setYield(yieldValue);
        stockObj.setLastPrice(priceValue);
        stockObj.setPE(peValue);
        stockObj.setCompanyName(compName);
        stockObj.changeCriteriaExecutionStatus("Yrs",props.testPassed());
        stockObj.changeCriteriaExecutionStatus("Yield",props.testPassed());
        stockObj.changeCriteriaExecutionStatus("Year",props.testPassed());
        stockObj.changeCriteriaExecutionStatus("Inc.",props.testPassed());
        stockObj.changeCriteriaExecutionStatus("Ex-Div",props.testPassed());
        stockObj.changeCriteriaExecutionStatus("Payout",props.testPassed());
        stockObj.changeCriteriaExecutionStatus("($Mil)",props.testPassed());
        stockObj.changeCriteriaExecutionStatus("P/E",props.testPassed());
        listOfStocks.add(stockObj);
        logger.log(Level.INFO, "компания " + ticker + " соответствует вышеуказанным критериям - оставляем ее в выборке");
        return stockObj;
    }

    public void dumpStockToFile(String ticker,String outputFileName) throws IOException {
        String fileName = Configuration.reportsFolder + outputFileName;
        stockObj = new Stocks();
        stockObj.setTicker(ticker);
        stockObj.setYield(yieldValue);
        stockObj.setLastPrice(priceValue);
        stockObj.setPE(peValue);
        stockObj.setCompanyName(compName);
        stockObj.changeCriteriaExecutionStatus("Yrs",props.testPassed());
        stockObj.changeCriteriaExecutionStatus("Yield",props.notTested());
        stockObj.changeCriteriaExecutionStatus("Year",props.notTested());
        stockObj.changeCriteriaExecutionStatus("Inc.",props.notTested());
        stockObj.changeCriteriaExecutionStatus("Ex-Div",props.notTested());
        stockObj.changeCriteriaExecutionStatus("Payout",props.notTested());
        stockObj.changeCriteriaExecutionStatus("($Mil)",props.testPassed());
        stockObj.changeCriteriaExecutionStatus("P/E",props.notTested());
        stocksListMap.put(ticker,stockObj);
        logger.log(Level.INFO, "капитализация компании " + ticker + " равна или выше 2 млрд.долл. - оставляем ее в выборке");
        logger.log(Level.INFO, "продолжительность непрерывных дивидендных выплат  компании " + ticker + " - от 15 лет подряд - оставляем ее в выборке");
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String contents = ow.writeValueAsString(stockObj);
        FileUtils.write(new File(fileName),contents,"UTF-8",true);
    }

    public void parseAsyncStockSummaryData(String outputFileName) throws IOException {
        for (HttpResponse entry: stockSummaryDataResponses ) {
            parseStockSummaryData(entry);
            if (includeDividends) {
                if (marketCapValue != null){
                    if (marketCapValue > 2000000000) {
                        String startDate = getDateAsEpoch(Calendar.YEAR,-15);
                        String endDate = getDateAsEpoch(Calendar.YEAR,0);
                        if (getDividendHistoryData(startDate,endDate,ticker)){
                            if (isDividendPaidOffFor15Years(ticker))
                                dumpStockToFile(ticker,outputFileName);
                        }
                    } else logger.log(Level.INFO, "компания " + ticker + " не соответствует критериям по капитализации и/или продолжительности дивидендных выплат");
                }
            } else
                if (yieldValue != null && peValue != null && marketCapValue != null){
                    if (yieldValue >= 2.5 && peValue < 21 && marketCapValue > 2000000000) {
                        if (payoutRatioValue.isNaN() || payoutRatioValue < 70) {
                            stockObj = addStockAsObj(ticker);
                            copyOfStocksListMap.put(ticker,stockObj);
                        }
                    } else
                        logger.log(Level.INFO, "компания " + ticker + " не соответствует одному или нескольким вышеуказанным критериям");
                }
        }
        stockSummaryDataResponses.clear();
    }

    public void filterBySummaryDetails(String fileName) throws IOException, InterruptedException {
        String fullFileName = Configuration.reportsFolder + fileName;
        logger.log(Level.INFO, "фильтрация списка компаний:");
        logger.log(Level.INFO, "по величине дивидендов - от 2.5% годовых");
        logger.log(Level.INFO, "по P/E  - менее 21");
        logger.log(Level.INFO, "по Payout Ratio  - доля прибыли, направляемая на выплату дивидендов - не более 70%");
        logger.log(Level.INFO, "по marketCap  - от 2 млрд.долл.");
        int cycles = tickers.size() / 5;
        int remainder = tickers.size() - (cycles * 5);
        int roundSize = tickers.size() - remainder;
        includeDividends = false;
        for (int i = 0; i < roundSize; i+=5) { //обработка списка тикеров общим числом кратным 5
            responseWaiter = new CountDownLatch(5);
            getAsyncData(i,i+5);
            parseAsyncStockSummaryData(fileName);
        }
        responseWaiter = new CountDownLatch(remainder);//обработка остатка (последняя партия тикеров меньше 5)
        getAsyncData(roundSize,tickers.size());
        parseAsyncStockSummaryData(fileName);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(fullFileName), listOfStocks);
        stocksListMap.clear();
        stocksListMap = (LinkedHashMap<String, Stocks>) copyOfStocksListMap.clone();
    }

    public boolean getStockStatsData(String ticker) {
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get(API_URL + "/stock/v2/get-statistics?region=US&symbol=" + ticker)
                    .header(rapidHost, rapidHostValue)
                    .header(rapidKey, rapidKeyValue)
                    .asJson();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос к источнику с marked data отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            e.printStackTrace();
            return false;
        }
        logger.log(Level.INFO, "запрос успешно отработал");
        boolean flag = parseStockSummaryData(response);
        return flag;
    }

    public boolean parseStockSummaryData(HttpResponse<JsonNode> response){
        JSONObject yield = null,closePrice = null,pe = null,jsonObject1 = null,jsonObject2 = null,payoutRatio = null,marketCap = null,dividendRate = null;
        ticker = null;compName = null;yieldValue = null;priceValue = null;peValue = null;marketCapValue = null;payoutRatioValue = null;dividendRateValue = null;
        ticker = response.getBody().getObject().optString("symbol");
        if (ticker.equals("GD"))
            logger.log(Level.INFO, "поймал");

        try{
            jsonObject1 = response.getBody().getObject().getJSONObject("summaryDetail");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос summaryDetail отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            e.printStackTrace();
            return false;
        }

        try {
            jsonObject2 = response.getBody().getObject().getJSONObject("price");
        } catch (Exception ex1) {
            logger.log(Level.SEVERE, "запрос price отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            ex1.printStackTrace();
            return false;
        }
        compName = jsonObject2.optString("shortName");

        try {
            closePrice = jsonObject2.getJSONObject("regularMarketPrice");
        } catch (Exception ex1) {
            logger.log(Level.SEVERE, "запрос previousClose отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            ex1.printStackTrace();
            return false;
        }
        priceValue = closePrice.optDouble("fmt");

        try {
            dividendRate = jsonObject1.getJSONObject("dividendRate");
        } catch (Exception ex1) {
            logger.log(Level.SEVERE, "запрос dividendRate не вернул данные, пробуем поле trailingAnnualDividendRate ...");
            try {
                dividendRate = jsonObject1.getJSONObject("trailingAnnualDividendRate");
            } catch (Exception ex2) {
                logger.log(Level.SEVERE, "запрос trailingAnnualDividendRate не вернул данные - пробуем брать значение из полей dividendYield/trailingAnnualDividendYield  ...");
            }
        }
//        if (dividendRate.toString().equals("{}") || dividendRate == null) {
//
//        }
        //если поле dividendRate содержит значение, рассчитываем yield исходя из него и текущей цены акции
        if (dividendRate != null) {
            dividendRateValue = dividendRate.optDouble("raw");
            yieldValue = dividendRateValue / priceValue * 100;
        //если поле dividendRate пустое, дополнительно считываем yield из полей trailingAnnualDividendYield / dividendYield
        } else {
            try {
                yield = jsonObject1.getJSONObject("trailingAnnualDividendYield");
            } catch (Exception ex1) {
                logger.log(Level.SEVERE, "запрос trailingAnnualDividendYield не вернул данные, пробуем поле dividendYield...");
            }
            if (yield == null) {
                try {
                    yield = jsonObject1.getJSONObject("dividendYield");
                } catch (Exception ex1) {
                    logger.log(Level.SEVERE, "запрос dividendYield не вернул данные - пропускаем компанию " + ticker + " ...");
                    return false;
                }
            }
            if (yield != null) yieldValue = yield.optDouble("raw") * 100;
        }

        try {
            pe = jsonObject1.getJSONObject("trailingPE");

        } catch (Exception ex) {
            logger.log(Level.INFO, "запрос trailingPE не вернул даннные, пробуем поле forwardPE");
        }
        if (pe == null) {
            try {
                pe = jsonObject1.getJSONObject("forwardPE");

            } catch (Exception ex) {
                logger.log(Level.SEVERE, "запрос forwardPE отработал с ошибкой - пропускаем компанию " + ticker + " ...");
                return false;
            }
        }
        if (pe != null) peValue = pe.optDouble("raw");

        try {
            payoutRatio = jsonObject1.getJSONObject("payoutRatio");

        } catch (Exception ex) {
            logger.log(Level.SEVERE, "запрос payoutRatio отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            ex.printStackTrace();
            return false;
        }
        if (payoutRatio != null)
            payoutRatioValue = payoutRatio.optDouble("raw");
        if (!payoutRatioValue.isNaN())
            payoutRatioValue = payoutRatioValue * 100;

        try {
            marketCap = jsonObject1.getJSONObject("marketCap");

        } catch (Exception ex) {
            logger.log(Level.SEVERE, "запрос marketCap отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            ex.printStackTrace();
            return false;
        }
        marketCapValue = marketCap.optLong("raw");
        return true;
    }

    public void sortStockList(){
        LinkedHashMap<String, Stocks> copyOfStocksListMap = new LinkedHashMap<>();
        ArrayList<Stocks> stocksList = new ArrayList<>();
        //заполняем лист
        for (Map.Entry<String, Stocks> entry : stocksListMap.entrySet())
            stocksList.add(entry.getValue());
//        //вычисляем кол-во успешно выполненных критериев по каждой компании
//        BeanComparator bc = new BeanComparator(Stocks.class, "calcCountOfPassedTests",false);
//        //сортируем по кол-ву успешно выполненных критериев - от самых лучших компаний к худшим
//        Collections.sort(stocksList, bc);
        //сортируем по доходности
        BeanComparator bc2 = new BeanComparator(Stocks.class, "getYield",false);
        Collections.sort(stocksList,bc2);

//        for (Map.Entry<String, Stocks> entry : stocksListMap.entrySet()){
//            Stocks stocks = entry.getValue();
//            int count = stocks.calcCountOfPassedTests();
//            stocks.setCountOfPassedTests(count);
//            stocksList.add(stocks);
//        }
//        ColumnComparator cc1 = new ColumnComparator(5,false);
//        ColumnComparator cc2 = new ColumnComparator(3, false);
//        GroupComparator gc = new GroupComparator(cc1, cc2);
//        Collections.sort(stocksList, gc);

        int stocksNumLimit = Integer.parseInt(props.maximumNumberOfStocks());
        int originalSize = stocksList.size();
        //удаляем лишние компании из оставшегося списка
        if (originalSize > stocksNumLimit) {
            int delta = originalSize - stocksNumLimit;
            for (int i = 1; i< delta+1; i++){
                stocksList.remove(originalSize - i);
            }
        }
        //копируем объекты из оригинального хешмапа в урезанный и отсортированный список
        for (int i = 0; i <stocksList.size(); i++) {
            String key = stocksList.get(i).getTicker();
            Stocks stocks = stocksListMap.get(key);
            copyOfStocksListMap.put(key,stocks);
        }
        stocksListMap.clear();
        stocksListMap = (LinkedHashMap<String, Stocks>) copyOfStocksListMap.clone();
    }

    public void resetNoOfStocksToMaxLimit(){
        //отбрасываем часть компаний из предыдущей выборки - до максимально возможного кол-ва в отчете
        int stocksNumLimit = Integer.parseInt(props.maximumNumberOfStocks());
        int prevSelectionSize = uniqueTickers.size();
        int totalNumOfSelectedStocks = tickers.size() + prevSelectionSize;
        if (totalNumOfSelectedStocks > stocksNumLimit){
            int deltaToExclude = totalNumOfSelectedStocks - stocksNumLimit;
            for (int i = 1; i < deltaToExclude+1; i++){
                uniqueTickers.remove(prevSelectionSize-i);
            }
        }
    }

    public void findUniqueTickers(ArrayList<String> tickers,ArrayList<String> tickersPreviousSelection){
        boolean isFound = false;
        for (int i = 0; i < tickersPreviousSelection.size();i++){
            String tickerToCheck = tickersPreviousSelection.get(i);
            for (int t = 0; t < tickers.size(); t++){
                isFound = tickers.get(t).equals(tickerToCheck);
                if (isFound) break;
            }
            if (!isFound) uniqueTickers.add(tickerToCheck);
        }
    }

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

    public boolean checkIncomeGrowth() {
        logger.log(Level.INFO, "метод проверяет актив на наличие поступательного роста показателей Net Income и Operating Income за 4 последних года");
        boolean flag = false;
        for (Map.Entry<String, Stocks> entry : stocksListMap.entrySet()){
            String ticker = entry.getKey();
            logger.log(Level.INFO, "считываем показатели Net Income и Operating Income по " + ticker + " за 4 последних года...");
            JSONArray incomeStatementHistory = getStockIncomeStatementData(ticker);
            if (incomeStatementHistory == null) continue;
            ArrayList<Double> netIncVals = getNetIncomeValues(incomeStatementHistory);
            ArrayList<Double> opIncVals = getOperatingIncomeValues(incomeStatementHistory);
            logger.log(Level.INFO, "выполняем проверку на то, что показатели Net Income и Operating Income за выбранный период поступательно росли...");
            boolean netIncomeResult = compareCompanyQuotes(netIncVals);
            boolean operatingIncomeResult = compareCompanyQuotes(opIncVals);
            if (netIncomeResult && operatingIncomeResult) {
                logger.log(Level.INFO, "Net Income или Operating Income компании " + ticker + " поступательно росли все время в течение выбранного периода");
                stocksListMap.get(ticker).changeCriteriaExecutionStatus("IncomeCheck",props.testPassed());
            } else {
                logger.log(Level.INFO, "Net Income или Operating Income компании " + ticker + " в течение выбранного периода некоторое время снижались");
                stocksListMap.get(ticker).changeCriteriaExecutionStatus("IncomeCheck",props.testFailed());
            }
        }
        return true;
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

    public HttpResponse<JsonNode> getFinancialsTabData(String ticker){
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get(API_URL + "/stock/v2/get-financials?symbol=" + ticker)
                    .header(rapidHost, rapidHostValue)
                    .header(rapidKey, rapidKeyValue)
                    .asJson();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос к источнику с marked data отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            return null;
        }
        logger.log(Level.INFO, "запрос успешно отработал");
        return response;
    }

    public JSONArray getStockIncomeStatementData(String ticker) {
        HttpResponse<JsonNode> response = getFinancialsTabData(ticker);
        JSONObject jsonObject;
        JSONArray incomeStatementHistory;
        try {
            jsonObject = response.getBody().getObject().getJSONObject("incomeStatementHistory");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос incomeStatementHistory отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            return null;
        }
        if (jsonObject == null) return null;
        try {
            incomeStatementHistory = jsonObject.getJSONArray("incomeStatementHistory");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос incomeStatementHistory отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            return null;
        }
        return incomeStatementHistory;
    }

    public String getDateAsEpoch(int datePeriodType, int dateShiftInveral) {
        Calendar cal = Calendar.getInstance();
        cal.add(datePeriodType, dateShiftInveral);
        long epochDate = cal.getTimeInMillis() / 1000;
        String epochStr = String.valueOf(epochDate);
        return epochStr;
    }

    public boolean checkDividendsGrowth() {
        logger.log(Level.INFO, "метод проверяет актив на наличие поступательного роста его дивидендов в течение 10 прошедших лет");
        String startDate = getDateAsEpoch(Calendar.YEAR,-10);
        String endDate = getDateAsEpoch(Calendar.YEAR,0);
        boolean flag = false;
        for (Map.Entry<String, Stocks> entry : stocksListMap.entrySet()){
            String ticker = entry.getKey();
            logger.log(Level.INFO, "считываем дивиденды по " + ticker + " за последние 10 лет...");
            boolean dividends = getDividendHistoryData(startDate,endDate,ticker);
            if (!dividends) continue;
            logger.log(Level.INFO, "выполняем проверку на то, что дивиденды за выбранный период поступательно росли...");
            flag = compareCompanyQuotes(dividendAmount);
            if (flag) {
                logger.log(Level.INFO, "размер дивиденда компании " + ticker + " поступательно рос все время в течение выбранного периода");
                stocksListMap.get(ticker).changeCriteriaExecutionStatus("DivCheck",props.testPassed());
            } else {
                logger.log(Level.INFO, "размер дивиденда компании " + ticker + " в течение выбранного периода некоторое время снижался");
                stocksListMap.get(ticker).changeCriteriaExecutionStatus("DivCheck",props.testFailed());
            }
        }
        return true;
    }

    public boolean compareStockAgainstEthalonETF() {
        logger.log(Level.INFO, "сравниваем прирост стоимости акций компаний с приростом стоимости эталонного ETF SDY за прошедшие 10 лет...");
        double ethalonValue = 0;
        for (Map.Entry<String, Stocks> entry : stocksListMap.entrySet()){
            String ticker = entry.getKey();
            logger.log(Level.INFO, "считываем котировки " + ticker + " за последние 10 лет...");
            JSONObject chartsData = getPriceChartData(ticker);
            if (chartsData == null) continue;
            JSONArray assetQuotes = getStockQuotes(chartsData);
            logger.log(Level.INFO, "рассчитываем рост стоимости " + ticker + " за период...");
            double assetValue = getPriceIncrease(assetQuotes);
            if (ethalonValue == 0){
                logger.log(Level.INFO, "считываем котировки эталонного ETF SDY за последние 10 лет...");
                JSONArray SDYQuotes = getEthalonETFQuotes(chartsData);
                logger.log(Level.INFO, "рассчитываем рост стоимости эталонного ETF SDY за период...");
                ethalonValue = getPriceIncrease(SDYQuotes);
            }
            logger.log(Level.INFO, "сравниваем прирост стоимости " + ticker + " с приростом эталонного ETF SDY...");
            if (assetValue > ethalonValue * 2) {
                logger.log(Level.INFO, ticker + " вырос сильнее эталона");
                stocksListMap.get(ticker).changeCriteriaExecutionStatus("SDYCheck",props.testPassed());
            } else {
                logger.log(Level.INFO, ticker + " вырос слабее эталона");
                stocksListMap.get(ticker).changeCriteriaExecutionStatus("SDYCheck",props.testFailed());
            }
        }
        return true;
    }

    public boolean getDividendHistoryData(String startDate, String endDate, String ticker) {
        boolean flag = false;
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get(API_URL + "/stock/v2/get-chart?&region=US&interval=1d" +
                    "&period1=" + startDate + "&period2=" + endDate + "&symbol=" + ticker)
                    .header(rapidHost, rapidHostValue)
                    .header(rapidKey, rapidKeyValue)
                    .asJson();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос дивидендов отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            return flag;
        }
        logger.log(Level.INFO, "запрос дивидендов успешно отработал");
        flag = parseDividendHistoryData(response,ticker);
        return flag;
    }

    public boolean parseDividendHistoryData(HttpResponse<JsonNode> response, String ticker){
        boolean flag = false;double divValue;
        ArrayList<String> epochDates = new ArrayList<>();
        JSONArray datesKeys; JSONObject jObject,jObject1,jObject2;
        try {
            jObject = response.getBody().getObject().getJSONObject("chart");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "дивиденды по компании " + ticker + " не были загружены, пропускаем ее...");
            return flag;
        }
        try {
            jObject1 = jObject.getJSONArray("result").getJSONObject(0).getJSONObject("events").getJSONObject("dividends");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "дивиденды по компании " + ticker + " не были загружены, пропускаем ее...");
            return flag;
        }
        dividendDates.clear();
        dividendAmount.clear();
        //считываем массив дат выплат дивидендов
        datesKeys = jObject1.names();
        //конвертируем массив дат в long
        for (int t = 0; t < datesKeys.length(); t++){
            Long dateValue = Long.parseLong(datesKeys.getString(t));
            dividendDates.add(dateValue);
//            epochDates.add(datesKeys.getString(t));
        }
        //сортируем даты по возрастанию, в начале - самая раняя
        Collections.sort(dividendDates);
        Collections.reverse(dividendDates);
        //по каждой дате считываем значение дивиденда и записываем в отдельный массив
        for (int i=0; i < dividendDates.size();i++){
            try {
                String currentDate = dividendDates.get(i).toString();
                jObject2 = jObject1.getJSONObject(currentDate);
                Object obj  = jObject2.get("amount");
                divValue = Double.parseDouble(obj.toString());
                dividendAmount.add(divValue);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "дивиденды по компании " + ticker + " не были загружены, пропускаем ее...");
                return flag;
            }
        }
        flag = true;
        return flag;
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

    public JSONObject getPriceChartData(String ticker) {
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get(API_URL + "/market/get-charts" +
                    "?comparisons=SDY&region=US&lang=en&symbol=" + ticker + "&interval=1wk&range=10y")
                    .header(rapidHost, rapidHostValue)
                    .header(rapidKey, rapidKeyValue)
                    .asJson();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос к источнику с marked data отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            return null;
        }
        JSONObject jObject,chart;
        JSONArray result;
        logger.log(Level.INFO, "запрос успешно отработал");
        try {
            chart = response.getBody().getObject().getJSONObject("chart");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "данные с котировками для компании " + ticker + " не загружены, пропускаем ее...");
            return null;
        }
        if (chart == null) return null;

        try {
            result = chart.getJSONArray("result");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "данные с котировками для компании " + ticker + " не загружены, пропускаем ее...");
            return null;
        }

        try {
            jObject = result.getJSONObject(0);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "данные с котировками для компании " + ticker + " не загружены, пропускаем ее...");
            return null;
        }
        return jObject;
    }

    public void copyFilteredTickers(ArrayList<String> companyNamesFiltered){
        ///очищаем массив предыдущей выборки
        tickersPreviousSelection.clear();
        //копируем в массив предыдущей выборки текущую выборку - список компаний до запуска текущей сессии фильтрации
        tickersPreviousSelection = (ArrayList<String>)tickers.clone();
        //очищаем основной массив
        tickers.clear();
        //копируем значения из новой выборки в основной - список отобранных компаний в текущей сессии фильтрации
        tickers = (ArrayList<String>)companyNamesFiltered.clone();
    }
}
