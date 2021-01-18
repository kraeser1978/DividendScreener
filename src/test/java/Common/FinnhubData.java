package Common;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static Common.DivsCoreData.props;

public class FinnhubData {
    private static Logger logger = Logger.getLogger(FinnhubData.class.getSimpleName());
    public ArrayList<String> tickers = new ArrayList<String>();
    public ArrayList<String> filteredTickers = new ArrayList<String>();
    ObjectMapper mapper = new ObjectMapper();

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
        dumpResults(tickersCopy);
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
        dumpResults(tickersCopy);
    }

    private void dumpResults(ArrayList<String> tickersCopy) throws IOException {
        String fullFileName = Configuration.reportsFolder + "\\tradeCandidatesSourceList.json";
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(fullFileName), tickersCopy);
        filteredTickers.clear();
        filteredTickers = (ArrayList<String>)tickersCopy.clone();
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
