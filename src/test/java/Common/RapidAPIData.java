package Common;

import com.codeborne.selenide.Configuration;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import org.apache.commons.io.FileUtils;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import static Common.DivsCoreData.props;

public class RapidAPIData {
    private static Logger logger = Logger.getLogger(RapidAPIData.class.getSimpleName());
    public ArrayList<String> tickers = new ArrayList<String>();
    public ArrayList<String> tickersPreviousSelection = new ArrayList<String>();
    final String API_URL = props.APIURL();final String rapidHost = props.rapidHost();final String rapidHostValue = props.rapidHostValue();
    final String rapidKey = props.rapidKey();final String rapidKeyValue = props.rapidKeyValue();
    public ArrayList<String> uniqueTickers = new ArrayList<String>();
    Double yieldValue,priceValue,peValue,payoutRatioValue;
    Long marketCapValue;
    Stocks stockObj;
    String stockObjFileName = Configuration.reportsFolder + "\\stockObj.json";
    String compName;
    public LinkedHashMap<String, Stocks> stocksListMap = new LinkedHashMap<>();
    ObjectMapper mapper = new ObjectMapper();
    ArrayList<Long> dividendDates = new ArrayList<>();
    ArrayList<Double> dividendAmount = new ArrayList<>();


    public boolean getDividendHistoryData2(String startDate, String endDate, String ticker) {
        logger.log(Level.INFO, "считываем дивиденды по " + ticker + " за последние 15 лет...");
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get(API_URL + "/stock/v2/get-historical-data?frequency=1wk&filter=div" +
                    "&period1=" + startDate + "&period2=" + endDate + "&symbol=" + ticker)
                    .header(rapidHost, rapidHostValue)
                    .header(rapidKey, rapidKeyValue)
                    .asJson();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос к источнику с marked data отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            return false;
        }
        logger.log(Level.INFO, "запрос успешно отработал");
        JSONArray eventsDataArray = null;
        JSONObject jObject = null;
        try {
            eventsDataArray = response.getBody().getObject().getJSONArray("eventsData");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "дивиденды по компании " + ticker + " не были загружены, пропускаем ее...");
            return false;
        }
        for (int i=0; i < eventsDataArray.length();i++){
            try {
                jObject = eventsDataArray.getJSONObject(i);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "дивиденды по компании " + ticker + " не были загружены, пропускаем ее...");
                return false;
            }
            Long dateValue = jObject.optLong("date");
            dividendDates.add(dateValue);
            Double divValue = jObject.optDouble("amount");
            dividendAmount.add(divValue);
        }
        return true;
    }

    public boolean isDividendPaidOffFor15Years(String ticker){
        boolean flag = false;
        int isDividend15Years = dividendDates.size();
        if (isDividend15Years < 60) {
            logger.log(Level.INFO, "компания " + ticker + " выплачивала дивиденды меньше 15 лет подряд - исключаем ее из выборки...");
            stocksListMap.get(ticker).changeCriteriaExecutionStatus("Yrs",props.testFailed());
            flag = false;
        } else flag = true;
        return flag;
    }

    public boolean isDividendGrewFor10Years(String ticker){
        logger.log(Level.INFO, "метод проверяет актив на наличие поступательного роста его дивидендов в течение 10 прошедших лет");
        boolean flag = false;
        LinkedHashMap<String, Stocks> copyOfStocksListMap = new LinkedHashMap<>();
        ArrayList<Double> dividends10Years = new ArrayList<>();
        for (int i=0; i< 41;i++) dividends10Years.add(dividendAmount.get(i));
        logger.log(Level.INFO, "выполняем проверку на то, что дивиденды за последние 10 лет поступательно росли...");
        flag = compareCompanyQuotes(dividends10Years);
        if (flag) {
            logger.log(Level.INFO, "размер дивиденда компании " + ticker + " поступательно рос все время в течение последних 10 лет");
            stocksListMap.get(ticker).changeCriteriaExecutionStatus("Yrs",props.testPassed());
            stocksListMap.get(ticker).changeCriteriaExecutionStatus("DivCheck",props.testPassed());
            copyOfStocksListMap.put(ticker,stocksListMap.get(ticker));
        } else {
            logger.log(Level.INFO, "размер дивиденда компании " + ticker + " в течение последних 10 лет некоторое время снижался");
            stocksListMap.get(ticker).changeCriteriaExecutionStatus("Yrs",props.testPassed());
            stocksListMap.get(ticker).changeCriteriaExecutionStatus("DivCheck",props.testFailed());
            copyOfStocksListMap.put(ticker,stocksListMap.get(ticker));
        }
        stocksListMap.clear();
        stocksListMap = (LinkedHashMap<String, Stocks>) copyOfStocksListMap.clone();
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
        }
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

    public boolean filterByDividendRelatedCriterias() {
        String startDate = getDateAsEpoch(Calendar.YEAR,-15);
        String endDate = getDateAsEpoch(Calendar.YEAR,0);
        boolean flag = false;
        for (Map.Entry<String, Stocks> entry : stocksListMap.entrySet()){
            String ticker = entry.getKey();
            if (!getDividendHistoryData2(startDate,endDate,ticker)) continue;
            if (!isDividendPaidOffFor15Years(ticker)) continue;
            if (!isDivPayoutFrequencyAcceptable()) continue;
            if (isDivGrowthRateAcceptable()) continue;
            if (!isDivLastIncreasedDateAcceptable()) continue;
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
        ArrayList<String> nasdaqTickers = new ArrayList<>();
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get("https://finnhub.io/api/v1/stock/symbol?exchange=US&token=bt2lnbv48v6sj2tj0vh0")
//                    .header(rapidHost, rapidHostValue)
//                    .header(rapidKey, rapidKeyValue)
                    .asJson();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос к источнику с marked data отработал с ошибкой ");
            e.printStackTrace();
        }
        JSONArray jsonArray = null;
        try{
            jsonArray = response.getBody().getArray();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос к источнику с marked data отработал с ошибкой - пропускаем компанию");
            e.printStackTrace();
        }

        JSONObject jsonObject = null;
        for (int i=0; i < jsonArray.length();i++){
            try {
                jsonObject = jsonArray.getJSONObject(i);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "ошибка");
            }
            String assetType = jsonObject.optString("type");
            if (assetType.equals("EQS")){
                String ticker = jsonObject.optString("symbol");
                tickers.add(ticker);
            }
        }
        logger.log(Level.INFO, "список тикеров считаны успешно");
    }

    public boolean isDraftListCanBeReUsed() throws IOException, ParseException {
        boolean flag = false;
        String fileName = Configuration.reportsFolder + "\\stockObj.json";
        File stocksFile = new File(fileName);
        if (stocksFile.exists()) {
            Path file = Paths.get(fileName);
            BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
            FileTime modified = attr.lastModifiedTime();
            DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
            Calendar cal = Calendar.getInstance();
            Date today = cal.getTime();
            Date fileModifiedDate = dateFormat.parse(dateFormat.format(modified.toMillis()));
            Date todayDate = dateFormat.parse(dateFormat.format(today.getTime()));
            int dev = fileModifiedDate.compareTo(todayDate);
            //если файл был сгенерен сегодня, считываем его и пропускаем проверку по первым 8 критериям
            //если файл был сгенерен не сегодня, заново выполняем все проверки по всем критериям
            if (dev == 0){
                if (stocksListMap.isEmpty()){
                    List<Stocks> myObjects = mapper.readValue(new File(stockObjFileName), new TypeReference<List<Stocks>>(){});
                    for (int i = 0; i < myObjects.size(); i++){
                        String ticker = myObjects.get(i).getTicker();
                        stocksListMap.put(ticker,myObjects.get(i));
                    }
                    flag = true;
                }
            }
        }
        return  flag;
    }

    public void filterBySummaryDetails() throws IOException {
        logger.log(Level.INFO, "фильтрация списка компаний:");
        logger.log(Level.INFO, "по величине дивидендов - от 2.5% годовых");
        logger.log(Level.INFO, "по P/E  - менее 21");
        logger.log(Level.INFO, "по Payout Ratio  - доля прибыли, направляемая на выплату дивидендов - не более 70%");
        logger.log(Level.INFO, "по marketCap  - от 2 млрд.долл.");
        List<Stocks> listOfStocks = new ArrayList<>();
        for (int i = 0; i < tickers.size(); i++){
            String ticker = tickers.get(i);
            logger.log(Level.INFO, "считываем дивиденды, P/E по " + ticker + " ...");
            boolean isDataAvailable = getStockStatsData(ticker);
            //если каких-то данных не хватает, пропускаем тикер
            if (!isDataAvailable) continue;
            if (yieldValue >= 2.5 && peValue < 21 && payoutRatioValue < 70 && marketCapValue > 2000000000) {
                stockObj = new Stocks();
                stockObj.setTicker(ticker);
                stockObj.setYield(yieldValue);
                stockObj.setLastPrice(priceValue);
                stockObj.setPE(peValue);
                stockObj.setCompanyName(compName);
                listOfStocks.add(stockObj);
                stocksListMap.put(ticker,stockObj);
                logger.log(Level.INFO, "Дивиденды компании " + ticker + " больше 2.5% - оставляем ее в выборке");
                logger.log(Level.INFO, "P/E компании " + ticker + " меньше 21 - оставляем ее в выборке");
//                ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
//                String contents = ow.writeValueAsString(stockObj);
//                FileUtils.write(new File(Configuration.reportsFolder + "\\stockObj.json"),contents,"UTF-8",true);
            } else logger.log(Level.INFO, "Дивиденды и/или P/E компании " + ticker + " не соответствуют критериям - исключаем ее из выборки");
        }
//        сортируем объекты по yield по убыванию
//        Collections.sort(listOfStocks);
//        Collections.reverse(listOfStocks);
//        копируем данные объектов в хэшмап
//        for (int i = 0; i < listOfStocks.size(); i++){
//            String ticker = listOfStocks.get(i).getTicker();
//            stocksListMap.put(ticker,listOfStocks.get(i));
//        }
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(stockObjFileName), listOfStocks);
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
        JSONObject yield,closePrice,pe,jsonObject1,jsonObject2,payoutRatio,marketCap;
        logger.log(Level.INFO, "запрос успешно отработал");

        try{
            jsonObject1 = response.getBody().getObject().getJSONObject("summaryDetail");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос к источнику с marked data отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            e.printStackTrace();
            return false;
        }

        try {
            jsonObject2 = response.getBody().getObject().getJSONObject("price");
        } catch (Exception ex1) {
            logger.log(Level.SEVERE, "запрос к источнику с marked data отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            ex1.printStackTrace();
            return false;
        }
        compName = jsonObject2.optString("shortName");

        try {
            yield = jsonObject1.getJSONObject("dividendYield");
        } catch (Exception ex1) {
            logger.log(Level.SEVERE, "запрос к источнику с marked data отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            ex1.printStackTrace();
            return false;
        }
        if (yield == null) return false;
        yieldValue = yield.optDouble("raw") * 100;

        try {
            closePrice = jsonObject1.getJSONObject("previousClose");
        } catch (Exception ex1) {
            logger.log(Level.SEVERE, "запрос к источнику с marked data отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            ex1.printStackTrace();
            return false;
        }
        priceValue = closePrice.optDouble("raw");

        try {
            pe = jsonObject1.getJSONObject("trailingPE");

        } catch (Exception ex) {
            logger.log(Level.SEVERE, "запрос к источнику с marked data отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            ex.printStackTrace();
            return false;
        }
        peValue = pe.optDouble("raw");

        try {
            payoutRatio = jsonObject1.getJSONObject("payoutRatio");

        } catch (Exception ex) {
            logger.log(Level.SEVERE, "запрос к источнику с marked data отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            ex.printStackTrace();
            return false;
        }
        payoutRatioValue = payoutRatio.optDouble("raw") * 100;

        try {
            marketCap = jsonObject1.getJSONObject("marketCap");

        } catch (Exception ex) {
            logger.log(Level.SEVERE, "запрос к источнику с marked data отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            ex.printStackTrace();
            return false;
        }
        marketCapValue = marketCap.optLong("raw");
        return true;
    }

    public void sortStockList(){
        LinkedHashMap<String, Integer> criteriaPassedMap = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> sortedStockMap = new LinkedHashMap<>();
        LinkedHashMap<String, Stocks> copyOfStocksListMap = new LinkedHashMap<>();
        //вычисляем кол-во успешно выполненных критериев по каждой компании
        for (Map.Entry<String, Stocks> entry : stocksListMap.entrySet()){
            int countPassed = entry.getValue().calcCountOfPassedTests();
            criteriaPassedMap.put(entry.getKey(),countPassed);
        }
        //сортируем по кол-ву успешно выполненных критериев - от самых лучших компаний к худшим
        List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>(criteriaPassedMap.entrySet());
        list.sort(Map.Entry.comparingByValue());
        Collections.reverse(list);
        //удаляем лишние компании из оставшегося списка
        int stocksNumLimit = Integer.parseInt(props.maximumNumberOfStocks());
        int delta = list.size() - stocksNumLimit;
        int originalSize = list.size();
        for (int i = 1; i< delta+1; i++){
            list.remove(originalSize - i);
        }
//        LinkedHashMap<String, Double> yieldMap = new LinkedHashMap<>();
//        LinkedHashMap<String, Double> sortedYieldMap = new LinkedHashMap<>();
//        //сортируем по убыванию Yield для компаний с одинаковым количеством выполненных критериев
//        for (int t=11; t > 7; t--){
//            //вычисляем кол-во компаний с определенным числом выполненных критериев
//            for(Map.Entry<String, Integer> entry : list){
//                int criteria = entry.getValue();
//                int criteriaCount = Collections.frequency(entry.getKey(), t);
//            }
//
//
//            int s = 0;
//            for (Map.Entry<String, Stocks> entry : stocksListMap.entrySet()){
//                if (s < criteriaCount){
//                    Double yield = entry.getValue().getYield();
//                    yieldMap.put(entry.getKey(),yield);
//                }
//                s++;
//            }
//            //сортируем по Yield по убыванию
//            List<Map.Entry<String, Double>> yieldList = new ArrayList<Map.Entry<String, Double>>(yieldMap.entrySet());
//            yieldList.sort(Map.Entry.comparingByValue());
//            Collections.reverse(yieldList);
//            //конвертируем лист в хешмап
//            for (Map.Entry<String, Double> entry : yieldList) {
//                sortedYieldMap.put(entry.getKey(), entry.getValue());
//            }
//            //копируем объекты из оригинального хешмапа в урезанный и отсортированный список
//            for (Map.Entry<String, Double> entry : sortedYieldMap.entrySet()){
//                String key = entry.getKey();
//                Stocks stocks = stocksListMap.get(key);
//                copyOfStocksListMap.put(key,stocks);
//            }
//        }

        //конвертируем лист в хешмап
        for (Map.Entry<String, Integer> entry : list) {
                sortedStockMap.put(entry.getKey(), entry.getValue());
        }
        //копируем объекты из оригинального хешмапа в урезанный и отсортированный список
        for (Map.Entry<String, Integer> entry : sortedStockMap.entrySet()){
            String key = entry.getKey();
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

    public JSONArray getStockIncomeStatementData(String ticker) {
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
        JSONArray incomeStatementHistory;
        logger.log(Level.INFO, "запрос успешно отработал");
        JSONObject jsonObject = response.getBody().getObject().getJSONObject("incomeStatementHistory");
        if (jsonObject == null) return null;
        incomeStatementHistory = jsonObject.getJSONArray("incomeStatementHistory");
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
            ArrayList<Double> dividends = getDividendHistoryData(startDate,endDate,ticker);
            if (dividends == null) continue;
            logger.log(Level.INFO, "выполняем проверку на то, что дивиденды за выбранный период поступательно росли...");
            flag = compareCompanyQuotes(dividends);
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

    public ArrayList<Double> getDividendHistoryData(String startDate, String endDate, String ticker) {
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get(API_URL + "/stock/v2/get-historical-data?frequency=1wk&filter=div" +
                    "&period1=" + startDate + "&period2=" + endDate + "&symbol=" + ticker)
                    .header(rapidHost, rapidHostValue)
                    .header(rapidKey, rapidKeyValue)
                    .asJson();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос к источнику с marked data отработал с ошибкой - пропускаем компанию " + ticker + " ...");
            return null;
        }
        ArrayList<Double> dividends = new ArrayList<>();
        logger.log(Level.INFO, "запрос успешно отработал");
        JSONArray eventsDataArray;
        JSONObject jObject;
        try {
            eventsDataArray = response.getBody().getObject().getJSONArray("eventsData");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "дивиденды по компании " + ticker + " не были загружены, пропускаем ее...");
            return null;
        }

        for (int i=0; i < eventsDataArray.length();i++){
            try {
                jObject = eventsDataArray.getJSONObject(i);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "дивиденды по компании " + ticker + " не были загружены, пропускаем ее...");
                return null;
            }
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
