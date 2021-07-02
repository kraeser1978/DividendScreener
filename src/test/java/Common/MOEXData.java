package Common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.util.Precision;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;


public class MOEXData {
    private static Logger logger = Logger.getLogger(MOEXData.class.getSimpleName());
    ObjectMapper mapper = new ObjectMapper();
    public LinkedHashMap<String,String> allMOEXStocksMap = new LinkedHashMap<String,String>();
    public ArrayList<ArrayList<String>> allMOEXStocks = new ArrayList<ArrayList<String>>();
    public ArrayList<ArrayList<String>> outPerformingStocks = new ArrayList<>();
    public ArrayList<GrowthStock> growthStocks = new ArrayList<>();
    public ArrayList<Double> imoexIndexPrices = new ArrayList<>();
    public ArrayList<Double> stockPrices = new ArrayList<>();
    String someTimeAgoStr,prevDateStr = null;
    ArrayList<String> prevDates = new ArrayList<>();
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    public String currentWorkingDir = null;

    @Test
    public void filterStocksForAllTimeFrames() throws Exception {
        //фильтруем по капитализации
        filterByCompanyCap();
        //фильтруем по месячному объему торгов
        filterByYearlyTradeVolume();
        //фильтруем по разным временным интервалам
        filterStocksFor3MonthsTimeFrame();
        filterStocksFor1YearTimeFrame();
        filterStocksFor3YearsTimeFrame();
        findCommonStocksInAllTimeFrames();
        dumpResultsToJSON();
        sortStocksByGrowthRate();
        addAttributeDataToSelection();
        String excelReport = generateExcelReport();
//        String pdfReport = convertExcel2PDF(excelReport);
        Telegram telegram = new Telegram();
        telegram.sendFile(currentWorkingDir,excelReport);
    }

    public void addAttributeDataToSelection(){
        HttpResponse<JsonNode> response = null;
        JSONObject jsonObject,jsonObject2 = null;JSONArray jsonArray,jsonArray2,securityData,marketData = null;
        String currentTicker, selectedTicker,fullName = null;int lotSize = 0; Double lastPrice = null;
        response = getCompanyCurrentMarketData();
        jsonObject = response.getBody().getObject().getJSONObject("securities");
        jsonObject2 = response.getBody().getObject().getJSONObject("marketdata");
        jsonArray = jsonObject.getJSONArray("data");
        jsonArray2 = jsonObject2.getJSONArray("data");
        //заполняем лист объектов доп полями: Название компании, размер лота
        for (int i = 0; i< jsonArray.length(); i++){
            securityData = jsonArray.getJSONArray(i);
            currentTicker = securityData.getString(0);
            for (int t = 0; t < growthStocks.size(); t++){
                selectedTicker = growthStocks.get(t).getTicker();
                if (selectedTicker.equals(currentTicker)){
                    fullName = securityData.getString(9);
                    lotSize = securityData.getInt(4);
                    growthStocks.get(t).setCompanyName(fullName);
                    growthStocks.get(t).setLotSize(lotSize);
                    break;
                }
            }
        }
        //заполняем лист объектов доп полями: last price
        for (int i = 0; i< jsonArray2.length(); i++){
            marketData = jsonArray2.getJSONArray(i);
            currentTicker = marketData.getString(0);
            for (int t = 0; t < growthStocks.size(); t++){
                selectedTicker = growthStocks.get(t).getTicker();
                if (selectedTicker.equals(currentTicker)){
                    lastPrice = marketData.getDouble(12);
                    growthStocks.get(t).setLastPrice(lastPrice);
                }
            }
        }
        logger.log(INFO,"данные по дополнительным полям добавлены к массиву объектов: Название компании, размер лота, last price");
    }

    public String generateExcelReport() throws IOException {
        String reportTemplateName = currentWorkingDir + "\\IMOEXOutPerformingStocks.xlsx";
        File excelTemplate = new File(reportTemplateName);
        FileInputStream file = new FileInputStream(excelTemplate);
        XSSFWorkbook book = new XSSFWorkbook(file);
        XSSFSheet sheet = book.getSheet("ScreenerResults");
        int rowNum = 3;//определяем порядковый номер первой строки, в которую нужно начинать запись данных
        DivsExcelData divsExcelData = new DivsExcelData();
        for (int i = 0; i < growthStocks.size(); i++){
            String ticker = growthStocks.get(i).getTicker();
            String companyName = growthStocks.get(i).getCompanyName();
            Double lastPrice = growthStocks.get(i).getLastPrice();
            int lotSize = growthStocks.get(i).getLotSize();
            Double lotCost = lastPrice * lotSize;
            String threeM = "+" + Double.toString(growthStocks.get(i).getThreeMonthsGrowthRate()) + "%";
            String oneY = "+" + Double.toString(growthStocks.get(i).getOneYearGrowthRate()) + "%";
            String threeY = "+" + Double.toString(growthStocks.get(i).getThreeYearsGrowthRate()) + "%";
            divsExcelData.setStrValueToReportCell(sheet.getRow(rowNum),0,ticker);
            divsExcelData.setStrValueToReportCell(sheet.getRow(rowNum),1,companyName);
            divsExcelData.setStrValueToReportCell(sheet.getRow(rowNum),2,lastPrice);
            divsExcelData.setStrValueToReportCell(sheet.getRow(rowNum),3,lotSize);
            divsExcelData.setStrValueToReportCell(sheet.getRow(rowNum),4,lotCost);
            divsExcelData.setStrValueToReportCell(sheet.getRow(rowNum),5,threeM);
            divsExcelData.setStrValueToReportCell(sheet.getRow(rowNum),6,oneY);
            divsExcelData.setStrValueToReportCell(sheet.getRow(rowNum),7,threeY);
            rowNum++;
        }
        String newReportName = "";
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();
        //сохраняем текущую дату в заголовке отчета
        DateFormat df = new SimpleDateFormat("dd-MM-yyyy");
        String t = df.format(today);
        divsExcelData.setStrValueToReportCell(sheet.getRow(0),6,"по состоянию на: " + t);
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

    public String convertExcel2PDF(String sourceExcel) throws IOException, DocumentException {
        //First we read the Excel file in binary format into FileInputStream
        FileInputStream input_document = new FileInputStream(new File(sourceExcel));
        // Read workbook into HSSFWorkbook
        XSSFWorkbook my_xls_workbook = new XSSFWorkbook (input_document);
        // Read worksheet into HSSFSheet
        XSSFSheet my_worksheet = my_xls_workbook.getSheetAt(0);
        // To iterate over the rows
        Iterator<Row> rowIterator = my_worksheet.iterator();
        //We will create output PDF document objects at this point
        Document iText_xls_2_pdf = new Document();
        int extPos = sourceExcel.indexOf(".xlsx");
        String targetPDF = sourceExcel.substring(0,extPos) + ".pdf";
        PdfWriter.getInstance(iText_xls_2_pdf, new FileOutputStream(targetPDF));
        iText_xls_2_pdf.open();
        //we have two columns in the Excel sheet, so we create a PDF table with two columns
        //Note: There are ways to make this dynamic in nature, if you want to.
        PdfPTable my_table = new PdfPTable(4);
        //We will use the object below to dynamically add new data to the table
        PdfPCell table_cell; PdfPCell table_num_cell;
        //Loop through rows.
        while(rowIterator.hasNext()) {
            Row row = rowIterator.next();
            Iterator<Cell> cellIterator = row.cellIterator();
            while(cellIterator.hasNext()) {
                Cell cell = cellIterator.next(); //Fetch CELL
                CellType cellType = cell.getCellType();
                switch(cellType) { //Identify CELL type
                    //you need to add more code here based on
                    //your requirement / transformations
                    case STRING:
                        //Push the data from Excel to PDF Cell
                        table_cell=new PdfPCell(new Phrase(cell.getStringCellValue()));
                        int test = cell.getColumnIndex();
                        //выравнивание по левому краю для первой колонки с тикерами
                        if (test == 0)
                            table_cell.setHorizontalAlignment(0);
                        //для остальных полей выравнивания по правому краю
                        else
                            table_cell.setHorizontalAlignment(2);
                        //feel free to move the code below to suit to your needs
                        my_table.addCell(table_cell);
                        break;
                    case NUMERIC:
                        //Push the data from Excel to PDF Cell
                        table_num_cell = new PdfPCell(new Phrase((float) cell.getNumericCellValue()));
                        //feel free to move the code below to suit to your needs
                        my_table.addCell(table_num_cell);
                        break;
                }
                //next line
            }
        }
        //Finally add the table to PDF document
        iText_xls_2_pdf.add(my_table);
        iText_xls_2_pdf.close();
        //we created our pdf file..
        input_document.close(); //close xls
        //возвращаем краткое имя PDF файла
        int pos = targetPDF.lastIndexOf("\\");
        String shortPDF = targetPDF.substring(pos+1);
        return targetPDF;
    }

    private void findCommonStocksInAllTimeFrames() {
        for (int i = 0; i< allMOEXStocks.size(); i++){
            int companyGrowthCount = allMOEXStocks.get(i).size();
            //отбираем те компании, которые показали более высокий прирост стоимости по отношению к индексу ММВБ на всех трех временных интервалах
            //последние 3 элемента - значения прироста за 3 месяца, 1 год и 3 года
            //поэтому общее кол-во элементов листа должно быть 6
            if (companyGrowthCount == 6){
                outPerformingStocks.add(allMOEXStocks.get(i));
            }
        }
    }

    private void getWorkingDir(){
        File file = null;
        try {
            file = new File(MOEXData.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        currentWorkingDir = file.getParentFile().getPath();
    }

    private void dumpResultsToJSON(){
        getWorkingDir();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            mapper.writeValue(new File(currentWorkingDir + "\\outPerformingStocks.json"), outPerformingStocks);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sortStocksByGrowthRate(){
        getWorkingDir();
        //считываем данные из исходного файла, если лист не заполнен данными
        if (outPerformingStocks.size() == 0){
            try {
                outPerformingStocks = mapper.readValue(new File(currentWorkingDir + "\\outPerformingStocks.json"), new TypeReference<ArrayList<ArrayList<String>>>(){});
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //записываем результаты в объекты
        for (int i = 0; i< outPerformingStocks.size(); i++){
            GrowthStock growthStock = new GrowthStock();
            growthStock.setTicker(outPerformingStocks.get(i).get(0));
            Long cap = Long.parseLong(outPerformingStocks.get(i).get(1));
            growthStock.setCompanyCapValue(cap);
            Long tradeVol = Long.parseLong(outPerformingStocks.get(i).get(2));
            growthStock.setTradeVolume(tradeVol);
            Double threeMonths = Double.parseDouble(outPerformingStocks.get(i).get(3));
            growthStock.setThreeMonthsGrowthRate(threeMonths);
            Double oneYear = Double.parseDouble(outPerformingStocks.get(i).get(4));
            growthStock.setOneYearGrowthRate(oneYear);
            Double threeYears = Double.parseDouble(outPerformingStocks.get(i).get(5));
            growthStock.setThreeYearsGrowthRate(threeYears);
            growthStocks.add(growthStock);
        }
        //сортируем массив объектов по росту за 3 месяца
        BeanComparator bc = new BeanComparator(GrowthStock.class, "getThreeMonthsGrowthRate",false);
        Collections.sort(growthStocks, bc);
    }

    public void formatResultsAndSendToTelegram(){
        String output = "";
        DateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy");
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();
        String todayStr = dateFormat.format(today);
        String header1 = "Расчет%20прироста%20стоимости%20акций%20на%20различных%20временных%20интервалах%20по%20состоянию%20на%20"+todayStr+"%0A%0D";
//        String spaces = new String(new char[63]).replace("\0","%20");
        String header2 = "за%203%20месяца%20%20%20за%201%20год%20%20%20за%203%20года%0A%0D";
        Telegram telegram = new Telegram();
        for (int i = 0; i < growthStocks.size(); i++){
            String ticker = growthStocks.get(i).getTicker();
            String cap = Long.toString(growthStocks.get(i).getCompanyCapValue());
            String vol = Long.toString(growthStocks.get(i).getTradeVolume());
            String threeM = Double.toString(growthStocks.get(i).getThreeMonthsGrowthRate());
            String oneY = Double.toString(growthStocks.get(i).getOneYearGrowthRate());
            String threeY = Double.toString(growthStocks.get(i).getThreeYearsGrowthRate());
            output = output + ticker + "%20%20%2B" + threeM + "%25%20%20%2B" + oneY + "%25%20%20%2B" + threeY + "%25,%0A%0D";
        }
        telegram.sendText(header1+header2+output);
    }

    public void filterStocksFor3YearsTimeFrame() throws ParseException {
        //считываем котировки индекса ММВБ за последние 3 года
        getIMOEXHistoricalPrices(Calendar.YEAR,-3);
        getIMOEXHistoricalPrices(Calendar.DATE,-1);
        filterStocks("3 года");
    }

    public void filterStocksFor1YearTimeFrame() throws ParseException {
        //считываем котировки индекса ММВБ за последний год
        getIMOEXHistoricalPrices(Calendar.YEAR,-1);
        getIMOEXHistoricalPrices(Calendar.DATE,-1);
        filterStocks("1 год");
    }

    public void filterStocksFor3MonthsTimeFrame() throws ParseException {
        //считываем котировки индекса ММВБ за последние 3 месяца
        getIMOEXHistoricalPrices(Calendar.MONTH,-3);
        getIMOEXHistoricalPrices(Calendar.DATE,-1);
        filterStocks("3 месяца");
    }

    private void filterStocks(String timeFrame){
        //считываем котировки каждой акции из списка и фильтруем по приросту стоимости к ММВБ
        for (int i = 0; i< allMOEXStocks.size(); i++){
            String ticker = allMOEXStocks.get(i).get(0);
            stockPrices.clear();
            //извлекаем исторические данные по котировке акции компании за N период назад
            boolean isStartPeriodDataAvailable = getStockHistoricalPrices(ticker,prevDates.get(0));
            //извлекаем данные по котировке акции компании на предыдущий торговый день
            boolean isEndPeriodDataAvailable = getStockHistoricalPrices(ticker,prevDates.get(1));
            //продолжаем, если есть данные
            if (isStartPeriodDataAvailable && isEndPeriodDataAvailable ) {
                //сравниваем рост стоимости акции с ростом индекса ММВБ за период
                double stockGrowthRate = isStockOutPerformedIMOEX(ticker,timeFrame);
                //добавляем отобранную акцию в отдельный массив
                if (stockGrowthRate > 0){
                    String rate = Double.toString(stockGrowthRate);
                    if (!rate.equals("Infinity")){
                        ArrayList<String> currentStockData = allMOEXStocks.get(i);
                        currentStockData.add(rate);
                    }
                }
            }
        }
        imoexIndexPrices.clear();
        prevDates.clear();
        stockPrices.clear();
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
        telegram.sendString(totalList);
    }

    public void filterByYearlyTradeVolume(){
        HttpResponse<JsonNode> response = null;String result = null;
        ArrayList<ArrayList<String>> copyOfallMOEXStocks = new ArrayList<ArrayList<String>>();
        String ticker = null;long minTurnOverLimit = 200000000;
        //определяем начальную дату
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();
        cal.add(Calendar.YEAR,-1);
        Date oneYearAgo = cal.getTime();
        String oneYearStr = dateFormat.format(oneYearAgo);
        String todayStr = dateFormat.format(today);
        ArrayList<HttpResponse<JsonNode>> responses = new ArrayList<>();
        for (int n = 0; n < allMOEXStocks.size(); n++){
            ticker = allMOEXStocks.get(n).get(0);
            //запрашиваем данные по обороту за годовой интервал в ежедневном разрезе
            long yearlyVolume = getCompanyTradeVolumeData(ticker,oneYearStr,todayStr);
            //сравнение суммарного торгового оборота за год с минимальным значением
            //если оборот больше минимума - добавляем компанию в выборку
            if (yearlyVolume > minTurnOverLimit){
                String vol = Long.toString(yearlyVolume);
                allMOEXStocks.get(n).add(vol);
                copyOfallMOEXStocks.add(allMOEXStocks.get(n));
            }
        }
        logger.log(INFO,"список компаний с годовым объемом торгов свыше 200 млн.руб. составлен");
        allMOEXStocks.clear();
        allMOEXStocks = (ArrayList<ArrayList<String>>) copyOfallMOEXStocks.clone();
    }

    public void filterByCompanyCap(){
        HttpResponse<JsonNode> response = null;String result = null;
        JSONObject jsonObject,jsonObject1 = null;JSONArray jsonArray = null,mData = null;
        String marketType,ticker = null;Long cap = null;
        response = getCompanyTotalsData();
        jsonObject = response.getBody().getObject().getJSONObject("securities");
        jsonArray = jsonObject.getJSONArray("data");
        for (int i = 0; i< jsonArray.length(); i++){
            mData = jsonArray.getJSONArray(i);
            marketType = mData.getString(2);
            if (marketType.equals("MRKT")){
                ticker = mData.getString(0);
                cap = mData.getLong(21);
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

    public double isStockOutPerformedIMOEX(String ticker, String timeFrame){
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
            currentStockGrowthRate = 0;
        }
        logger.log(INFO,"рост индекса ММВБ за " + timeFrame + ": " + imoexIndexGrowthRate + "%");
        logger.log(INFO,"рост акции за " + timeFrame + ": " + currentStockGrowthRate + "%");
        return currentStockGrowthRate;
    }

    public boolean getStockHistoricalPrices(String ticker, String startDate) {
        boolean flag = false;
        HttpResponse<JsonNode> response = null;String result = null;
        JSONObject jsonObject = null;JSONArray jsonArray = null,mData = null;
        double value = 0;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE,-1);
        Date someTimeAgo = cal.getTime();
        someTimeAgoStr = dateFormat.format(someTimeAgo);
        //берем начальную дату из prevDates
        response = getStockData(ticker,startDate,someTimeAgoStr);
        jsonObject = response.getBody().getObject().getJSONObject("history");
        jsonArray = jsonObject.getJSONArray("data");
        result = jsonArray.toString();
        if (result.equals("[]"))
            return flag;
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
        flag = true;
        return flag;
    }

    public void getIMOEXHistoricalPrices(int timeFrame,int timeShift) throws ParseException {
        String result = null;JSONObject jsonObject = null;JSONArray jsonArray = null,mData = null;
        Calendar cl = null;Date someTimeAgo = null;
        //определяем начальную дату
        Calendar cal = Calendar.getInstance();
        if (timeFrame !=0 || timeShift !=0)
            cal.add(timeFrame,timeShift);
        someTimeAgo = cal.getTime();
        //проверка на выходные/праздничные дни
        cl = Calendar.getInstance();
        cl.setTime(someTimeAgo);
        calcTimeFrameDates(cl,someTimeAgo);

        jsonArray = getDataForPrevDate();
        result = jsonArray.toString();
        if (result.equals("[]")){
            //проверка на попадание в самые длинные новогодние каникулы в РФ
            //значение ближайшей начальной даты, на которую есть данные, сохранится в prevDateStr
            for (int i = 0; i < 11; i++){
                cl.add(Calendar.DATE,-1);
                calcTimeFrameDates(cl,someTimeAgo);
                jsonArray = getDataForPrevDate();
                result = jsonArray.toString();
                if (!result.equals("[]"))
                    break;
            }
        }
        mData = jsonArray.getJSONArray(0);
        //берем close price по индексу ММВБ за выбранную дату
        imoexIndexPrices.add(mData.getDouble(5));
        //запоминаем подобранную начальную дату, на которые есть данные по торгам - пригодится для запросов по компаниям
        prevDates.add(prevDateStr);
    }

    private void calcTimeFrameDates(Calendar cl,Date someTimeAgo){
        someTimeAgoStr = null;prevDateStr = null;
        Date prevDate = cl.getTime();
        someTimeAgoStr = dateFormat.format(someTimeAgo);
        prevDateStr = dateFormat.format(prevDate);
    }

    private JSONArray getDataForPrevDate(){
        HttpResponse<JsonNode> response = getIMOEXIndexData(prevDateStr,someTimeAgoStr);
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
        logger.log(INFO, "данные по компании " + ticker + " за период с " + dateFrom + " по " + dateTill + " считаны успешно");
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

    private HttpResponse<JsonNode> getCompanyCurrentMarketData(){
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get("https://iss.moex.com/iss/engines/stock/markets/shares/boards/tqbr/securities.json")
                    .asJson();
        } catch (Exception e) {
            logger.log(SEVERE, "запрос к рыночным данным ММВБ отработал с ошибкой");
            response = null;
        }
        logger.log(INFO, "справочные данные по компаниям считаны успешно");
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

    private Long getCompanyTradeVolumeData(String ticker,String dateFrom, String dateTill){
        int resultPageCounter = 0;HttpResponse<JsonNode> response;
        JSONObject jsonObject = null;JSONArray jsonArray = null,mData = null;
        long tradeVolume = 0; long yearlyVolume = 0;
        boolean flag;
        do {
            String counter = Integer.toString(resultPageCounter);
            try {
                response = Unirest.get("http://iss.moex.com/iss/history/engines/stock/markets/shares/boards/tqbr/securities/" + ticker + ".json?from=" +
                        dateFrom + "&till=" + dateTill + "&start=" + counter)
                        .asJson();
            } catch (Exception e) {
                logger.log(SEVERE, "запрос к рыночным данным ММВБ по тикеру " + ticker + " отработал с ошибкой");
                response = null;
            }
            //парсим данные из ответного сообщения
            jsonObject = response.getBody().getObject().getJSONObject("history");
            jsonArray = jsonObject.getJSONArray("data");
            //проверяем, есть ли в ответе данные
            flag = jsonArray.toString().equals("[]");
            //если ответ пустой - пропускаем тикер и выходим из цикла
            if (flag)
                return yearlyVolume;
            //если данные есть - суммируем торговый оборот за все выбранные торговые дни в рамках годового интервала
            for (int i = 0; i< jsonArray.length(); i++){
                mData = jsonArray.getJSONArray(i);
                tradeVolume = mData.getLong(5);
                yearlyVolume = yearlyVolume + tradeVolume;
            }
            logger.log(INFO, "данные по тикеру " + ticker + " считаны успешно");
            resultPageCounter = resultPageCounter +100;
        } while (flag == false);
        return yearlyVolume;
    }
}
