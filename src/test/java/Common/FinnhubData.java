package Common;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static Common.DivsCoreData.props;
import static org.apache.commons.lang3.StringUtils.upperCase;

public class FinnhubData {
    private static Logger logger = Logger.getLogger(FinnhubData.class.getSimpleName());
    public ArrayList<String> tickers = new ArrayList<String>();
    public ArrayList<String> filteredTickers = new ArrayList<String>();
    ObjectMapper mapper = new ObjectMapper();
    ArrayList<ArrayList<String>> data = new ArrayList<>();

    public String generateExcelReport() throws IOException {
        String reportTemplateName = Configuration.reportsFolder + "\\MA_BreakThroug_Filtered_tickers.xlsx";
        File excelTemplate = new File(reportTemplateName);
        FileInputStream file = new FileInputStream(excelTemplate);
        XSSFWorkbook book = new XSSFWorkbook(file);
        XSSFSheet sheet = book.getSheet("ScreenerResults");
        int rowNum = 2;//определяем порядковый номер первой строки, в которую нужно начинать запись данных
        DivsExcelData divsExcelData = new DivsExcelData();
        for (int i = 0; i < data.size(); i++){
            String ticker = data.get(i).get(0);
            Double closePrice = Double.parseDouble(data.get(i).get(1));
            Double maValue = Double.parseDouble(data.get(i).get(2));
            Double maGrowthValue = Double.parseDouble(data.get(i).get(3));
            divsExcelData.setStrValueToReportCell(sheet.getRow(rowNum),0,ticker);
            divsExcelData.setStrValueToReportCell(sheet.getRow(rowNum),1,closePrice);
            divsExcelData.setStrValueToReportCell(sheet.getRow(rowNum),2,maValue);
            divsExcelData.setStrValueToReportCell(sheet.getRow(rowNum),3,maGrowthValue);
            rowNum++;
        }
        String newReportName = "";
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();
        //сохраняем текущую дату в заголовке отчета
        DateFormat df = new SimpleDateFormat("dd-MM-yyyy");
        String t = df.format(today);
        divsExcelData.setStrValueToReportCell(sheet.getRow(0),3,"по состоянию на: " + t);
        //задаем дату для шаблона имени нового файла отчета
        DateFormat dateFormat = new SimpleDateFormat("dd_MM_yyyy_hh_mm");
        String newDateStr = dateFormat.format(today);
        int extPos = reportTemplateName.indexOf(".xlsx");
        newReportName = reportTemplateName.substring(0,extPos) + "_" + newDateStr + ".xlsx";
        FileOutputStream fos = new FileOutputStream(newReportName);
        book.write(fos);
        book.close();
        fos.close();
        file.close();
        return newReportName;
    }

    public void filterByADX() throws IOException {
        ArrayList<String> tickersCopy = new ArrayList<String>();
        if (filteredTickers.isEmpty())
            filteredTickers = mapper.readValue(new File(Configuration.reportsFolder + "\\RSIFilterSet.json"), new TypeReference<List<String>>(){});
        logger.log(Level.INFO, "отбираем компании с нужным уровнем ADX индикатора");
        for (int i = 0; i< filteredTickers.size(); i++){
            String ticker = filteredTickers.get(i);
            ArrayList<Double> adx = getIndicatorsLastData(ticker,"adx",14);
            if (adx.isEmpty()) continue;
            Double adxValue = adx.get(2);
            if (adxValue == null) continue;
            //Для сигнала на покупку: RSI должен быть от 20 до 50
            if ((adxValue > 20) && (adxValue < 50))
                tickersCopy.add(ticker);
            Selenide.sleep(800);
        }
        logger.log(Level.INFO, "фильтрация по индикатору ADX завершена, отобрано компаний: " + tickersCopy.size()+1);
        dumpResults(tickersCopy,"\\ADXFilterSet.json");
    }

    public void filterByRSI() throws IOException {
        ArrayList<String> tickersCopy = new ArrayList<String>();
        if (filteredTickers.isEmpty())
            filteredTickers = mapper.readValue(new File(Configuration.reportsFolder + "\\MAFilterSet.json"), new TypeReference<List<String>>(){});
        logger.log(Level.INFO, "отбираем компании с нужным уровнем RSI индикатора");
        for (int i = 0; i< filteredTickers.size(); i++){
            String ticker = filteredTickers.get(i);
            ArrayList<Double> rsi = getIndicatorsLastData(ticker,"rsi",14);
            if (rsi.isEmpty()) continue;
            Double rsiValue = rsi.get(2);
            if (rsiValue == null) continue;
            //Для сигнала на покупку: RSI должен быть или от 20 до 40, или 60-70
            if ((rsiValue > 20) && (rsiValue < 40) || (rsiValue > 60) && (rsiValue < 70))
                tickersCopy.add(ticker);
            Selenide.sleep(800);
        }
        logger.log(Level.INFO, "фильтрация по индикатору RSI завершена, отобрано компаний: " + tickersCopy.size()+1);
        dumpResults(tickersCopy,"\\RSIFilterSet.json");
    }

    public ArrayList<ArrayList<String>> filterByMABreakThrough() throws IOException {
        ArrayList<String> tickersCopy = new ArrayList<String>();
        if (filteredTickers.isEmpty())
            filteredTickers = mapper.readValue(new File(Configuration.reportsFolder + "\\tradeCandidatesSourceList.json"), new TypeReference<List<String>>(){});
        logger.log(Level.INFO, "отбираем компании с пробоем MA индикатора");
        int topLimit = filteredTickers.size();
        int smallLimit = 30;
        for (int i = 0; i< smallLimit; i++){
            String ticker = filteredTickers.get(i);
            ArrayList<Double> ma = getIndicatorsLastData(ticker,"sma",21);
            Double openPrice = ma.get(0);
            Double closePrice = ma.get(1);
            Double maValue = ma.get(2);
            if ((openPrice == null) || (closePrice == null) || (maValue == null)) continue;
            //критерий №1: цена закрытия больше цены открытия за тот же день и цена закрытия должна превысить MA
            if ((closePrice > openPrice) && (closePrice > maValue) && (openPrice < maValue)) {
                ArrayList<ArrayList<Double>> ma50bars = getIndicatorsHistoricalData(ticker,"sma",21);
                if ((ma50bars.isEmpty()) || (ma50bars == null)) continue;
                Double previousOpenPrice = ma50bars.get(0).get(48);
                Double previousClosePrice = ma50bars.get(1).get(48);
                Double previousMaValue = ma50bars.get(2).get(48);
                //критерий №2: если цена открытия предыдущей свечи, которая перед последней (вторая с конца), тоже выше МА, исключаем из выборки
                if ((previousClosePrice > previousOpenPrice) && (previousClosePrice > previousMaValue)) continue;
                //критерий №3: если цена открытия предыдущей свечи выше цены открытия текущей - значит нисходящее движение и это ретест и отбой - исключаем из выборки
                if (previousOpenPrice > openPrice) continue;
                //критерий №4: первая пробойная свеча должна быть выше MA на 10% и не должна превышать MA на 40%
                Double growthRate = (closePrice / maValue) - 1;
//                if ((growthRate > 10) && (growthRate < 40))
//                    tickersCopy.add(ticker);
                ArrayList<String> tickerData = new ArrayList<String>();
                tickerData.add(ticker);
                tickerData.add(closePrice.toString());
                tickerData.add(maValue.toString());
                tickerData.add(growthRate.toString());
                data.add(tickerData);
            }
            Selenide.sleep(800);
        }
        logger.log(Level.INFO, "фильтрация по пробою MA завершена, отобрано компаний: " + tickersCopy.size()+1);
        dumpResults(tickersCopy,"\\MAFilterSet.json");
        return data;
    }

    public void filterByPrice() throws IOException {
        ArrayList<String> tickersCopy = new ArrayList<String>();
        if (filteredTickers.isEmpty())
            filteredTickers = mapper.readValue(new File(Configuration.reportsFolder + "\\tradeCandidatesSourceList.json"), new TypeReference<List<String>>(){});
        logger.log(Level.INFO, "отбираем компании с ценой закрытия от 1 до 500 долларов");
        Double price = null;
        for (int i = 0; i< filteredTickers.size(); i++){
            String ticker = filteredTickers.get(i);
            price = getSymbolQuotesData(ticker);
            if ((price >= 1) && (price <=500))
                tickersCopy.add(ticker);
            Selenide.sleep(800);
        }
        logger.log(Level.INFO, "фильтрация по цене инструментов завершена");
        dumpResults(tickersCopy,"\\tradeCandidatesSourceList.json");
    }

    public void filterByMarketCap() throws IOException {
        ArrayList<String> tickersCopy = new ArrayList<String>();
        logger.log(Level.INFO, "отбираем компании с рыночной капитализацией от 500 млн.долл.");
        Double cap = null;
        for (int i = 0; i< tickers.size(); i++){
            String ticker = tickers.get(i);
            cap = getCompanyMarketCap(ticker);
            if (cap >= 500)
                tickersCopy.add(ticker);
            Selenide.sleep(800);
        }
        logger.log(Level.INFO, "фильтрация по капитализации завершена");
        dumpResults(tickersCopy,"\\tradeCandidatesSourceList.json");
    }

    private void dumpResults(ArrayList<String> tickersCopy, String shortName) throws IOException {
        String fullFileName = Configuration.reportsFolder + shortName;
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(fullFileName), tickersCopy);
        filteredTickers.clear();
        filteredTickers = (ArrayList<String>)tickersCopy.clone();
    }

    public String getDateAsEpoch(int datePeriodType, int dateShiftInveral) {
        Calendar cal = Calendar.getInstance();
        cal.add(datePeriodType, dateShiftInveral);
        long epochDate = cal.getTimeInMillis() / 1000;
        String epochStr = String.valueOf(epochDate);
        return epochStr;
    }

    private JSONObject sendIndicatorRequest(String ticker, String indicatorType, int timeperiod){
        HttpResponse<JsonNode> response = null;
        String startDate = getDateAsEpoch(Calendar.MONTH,-6);
        String endDate = getDateAsEpoch(Calendar.DATE,0);
//        String endDate = "1610439621";
        try {
            response = Unirest.get("https://finnhub.io/api/v1/indicator?symbol=" + ticker + "&resolution=D&from=" + startDate + "&to=" + endDate +
                    "&indicator=" + indicatorType + "&timeperiod=" + timeperiod + "&token=" + props.finnhubToken())
                    .asJson();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "для тикера " + ticker + " запрос данных по МА из Finnhub marked data отработал с ошибкой");
            e.printStackTrace();
            return null;
        }
        JSONObject jsonObject = null;
        try{
            jsonObject = response.getBody().getObject();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "для тикера " + ticker + " запрос по МА из Finnhub не вернул данные - пропускаем компанию");
            e.printStackTrace();
            return null;
        }
        return jsonObject;
    }

    private ArrayList<ArrayList<Double>> getIndicatorsHistoricalData(String ticker, String indicatorType, int timeperiod){
        ArrayList<ArrayList<Double>> indData = new ArrayList<ArrayList<Double>>();
        JSONObject jsonObject = sendIndicatorRequest(ticker, indicatorType, timeperiod);
        //сохраняем данные из ответа запроса в 2мерных листах
        //берем цену открытия за последние 50 периодов
        ArrayList<Double> openPrices = getAllQuotesAndIndicator(jsonObject,ticker,"цена открытия","o");
        if (openPrices == null)
            return indData;
        else indData.add(openPrices);
        //берем цену закрытия за последние 50 периодов
        ArrayList<Double> closePrices = getAllQuotesAndIndicator(jsonObject,ticker,"цена закрытия","c");
        if (closePrices == null)
            return indData;
        else indData.add(closePrices);
        //берем значение индикатора за последние 50 периодов
        ArrayList<Double> indicatorValues = getAllQuotesAndIndicator(jsonObject,ticker,upperCase(indicatorType),indicatorType);
        if (indicatorValues == null)
            return indData;
        else indData.add(indicatorValues);
        return indData;
    }

    private ArrayList<Double> getAllQuotesAndIndicator(JSONObject jsonObject, String ticker, String quoteName, String key){
        ArrayList<Double> quotes = new ArrayList<>();
        Double quote = null; JSONArray array2 = null;
        try {
            array2 = jsonObject.optJSONArray(key);
        } catch (Exception e){
            logger.log(Level.SEVERE, "нет массива данных по " + quoteName + " для " + ticker);
            return null;
        }
        //заполняем лист значениями по последним 50 свечам
        int actualListSize = array2.length();
        int requiredSize = 0;
        //если данных в массиве меньше 50 элементов, прекращаем заполнение
        if (actualListSize > 49)
            requiredSize = 50;
        else
            return quotes;
        int delta = actualListSize - requiredSize;
        for (int i = delta; i< actualListSize; i++) {
            try {
                String quoteVal = array2.get(i).toString();
                quote = Double.parseDouble(quoteVal);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "нет данных по " + quoteName + " для " + ticker);
                return null;
            }
            if ((quote != null) && (!quote.isNaN())) {
                logger.log(Level.INFO, quoteName + " по " + ticker + " составляет " + quote + " долл.");
                quotes.add(quote);
            }
            else if ((key.equals("c")) || (key.equals("o")) && (quote == 0.0)) {
                logger.log(Level.INFO, "нет данных по " + quoteName + " для " + ticker);
                return quotes;
            }
        }
        return quotes;
    }

    private ArrayList<Double> getIndicatorsLastData(String ticker, String indicatorType, int length){
        ArrayList<Double> indData = new ArrayList<>();
        JSONObject jsonObject = sendIndicatorRequest(ticker, indicatorType, length);
        //берем цену открытия за последний день
        Double openPrice = getLastQuotesAndIndicator(jsonObject,ticker,"цена открытия","o");
        if (openPrice == null)
            return indData;
        else indData.add(openPrice);
        //берем цену закрытия за последний день
        Double closePrice = getLastQuotesAndIndicator(jsonObject,ticker,"цена закрытия","c");
        if (closePrice == null)
            return indData;
        else indData.add(closePrice);
        //берем значение индикатора за последний день
        Double indicator = getLastQuotesAndIndicator(jsonObject,ticker,upperCase(indicatorType),indicatorType);
        if (indicator == null)
            return indData;
        else indData.add(indicator);
        return indData;
    }

    private Double getLastQuotesAndIndicator(JSONObject jsonObject, String ticker, String quoteName, String key){
        Double quote = null; JSONArray array2 = null;
        try {
            array2 = jsonObject.optJSONArray(key);
        } catch (Exception e){
            logger.log(Level.SEVERE, "нет массива данных по " + quoteName + " для " + ticker);
            return null;
        }

        try {
            quote = Double.parseDouble(array2.get(array2.length()-1).toString());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "нет данных по " + quoteName + " для " + ticker);
            return null;
        }

        if ((quote != null) && (!quote.isNaN()) && (quote != 0.0))
            logger.log(Level.INFO, quoteName + " по " + ticker + " составляет " + quote + " долл.");
        else {
            logger.log(Level.INFO, "нет данных по " + quoteName + " для " + ticker);
            return null;
        }
        return quote;
    }

    private Double getSymbolQuotesData(String ticker){
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get("https://finnhub.io/api/v1/quote?symbol=" + ticker + "&token=" + props.finnhubToken())
                    .asJson();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "для тикера " + ticker + " запрос цены в Finnhub marked data отработал с ошибкой");
            e.printStackTrace();
        }
        JSONObject jsonObject = null;
        try{
            jsonObject = response.getBody().getObject();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "для тикера " + ticker + " запрос цены не вернул данные - пропускаем компанию");
            e.printStackTrace();
        }
        Double currentPrice = null;
        currentPrice = jsonObject.optDouble("c");
        if ((currentPrice != null) && (!currentPrice.isNaN()) && (currentPrice != 0.0))
            logger.log(Level.INFO, "текущая цена " + ticker + " составляет " + currentPrice + " долл.");
        else logger.log(Level.INFO, "нет данных по текущей цене " + ticker);
        return currentPrice;
    }

    private Double getCompanyMarketCap(String ticker){
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get("https://finnhub.io/api/v1/stock/profile2?symbol=" + ticker + "&token=" + props.finnhubToken())
                    .asJson();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "для тикера " + ticker + " запрос профайла компании в Finnhub marked data отработал с ошибкой");
            e.printStackTrace();
        }
        JSONObject jsonObject = null;
        try{
            jsonObject = response.getBody().getObject();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "для тикера " + ticker + " запрос не вернул данные - пропускаем компанию");
            e.printStackTrace();
        }
        Double marketCap = null;
        marketCap = jsonObject.optDouble("marketCapitalization");
        if ((marketCap != null) && (!marketCap.isNaN()) && (marketCap != 0.0))
            logger.log(Level.INFO, "капитализация компании " + ticker + " составляет " + marketCap + " млн.долл.");
        else logger.log(Level.INFO, "нет данных по капитализации компании " + ticker);
        return marketCap;
    }
}
