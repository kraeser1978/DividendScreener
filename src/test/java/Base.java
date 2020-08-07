import Common.DivsCoreData;
import Common.DivsExcelData;
import Common.RapidAPIData;
import com.codeborne.selenide.Configuration;
import com.mashape.unirest.http.Unirest;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.Test;
import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Base {
    private static Logger logger = Logger.getLogger(Base.class.getSimpleName());
    public String filteringMode;
    public static DivsCoreData divsCoreData;
    public static DivsExcelData divsExcelData;
    public static XSSFSheet companiesSheet;
    public static final String testPassed = "Критерию соответствует";
    public static final String testFailed = "Критерию не соответствует";
    public static final String testNotExecuted = "Проверка по критерию не выполнялась";

    private static void preparation() throws Exception {
        DivsCoreData divsCoreData = new DivsCoreData();
        divsCoreData.SetUp();
        divsCoreData.downloadDivsFile();
        String newName = Configuration.reportsFolder + "\\USDividendChampions_singleTab";
        File newFileName = new File(newName + ".xlsx");
        divsExcelData = new DivsExcelData();
        logger.log(Level.INFO, "загружаем данные из файла...");
        divsExcelData.getDivsBook(newFileName);
        logger.log(Level.INFO, "задаем критерии поиска и фильтрации...");
        companiesSheet = divsExcelData.getDivsSheet(newFileName);
        divsExcelData.getSearchCriteria(companiesSheet);
    }

    @Test
    public static void main(String[] args) throws Exception {
        preparation();
        boolean criteriaStatus = false;
        HashMap<String,String> criteriaExecutionStatuses = new LinkedHashMap<>();
        logger.log(Level.INFO, "Yrs - компании, которые платят дивиденды 15 и более лет");
        Cell numberOfYears = divsExcelData.findCell(companiesSheet,"Yrs");
        divsExcelData.setAutoFilter(companiesSheet,numberOfYears.getColumnIndex(),"15");
        criteriaExecutionStatuses.put("Yrs",testPassed);
        logger.log(Level.INFO, "выполняем предварительный отбор по следующим фильтрам:");
        for (Map.Entry<String, ArrayList<String>> entry : divsExcelData.fieldsSearchCriterias.entrySet()) {
            String key = entry.getKey();
            ArrayList<String> criterias = entry.getValue();
            int columnSeqNo = divsExcelData.fieldsColumns.get(key);
            divsExcelData.comparisonType = criterias.get(2);
            criteriaStatus = divsExcelData.filterCompanies(columnSeqNo,criterias.get(0),criterias.get(1));
            if (criteriaStatus) criteriaExecutionStatuses.put(key,testPassed);
            else criteriaExecutionStatuses.put(key,testFailed);
        }
        //считываем тикеры компаний
        RapidAPIData rapidAPIData = new RapidAPIData();
        //передаем массив тикеров в класс для выполнения REST запросов к market data source
        rapidAPIData.tickersPreviousSelection = divsExcelData.getCompaniesTickersByNames(companiesSheet,divsExcelData.companyNamesPreviousSelection);
        rapidAPIData.tickers = divsExcelData.getCompaniesTickersByNames(companiesSheet,divsExcelData.companyNames);
//        //метод сравнивает прирост стоимости акции компании с приростом стоимости эталонного ETF SDY за прошедшие 10 лет
//        //если актив вырос меньше SDY, компания исключается из выборки
        criteriaStatus = true;
//        criteriaStatus = rapidAPIData.compareStockAgainstEthalonETF();
        if (criteriaStatus) criteriaExecutionStatuses.put("SDYCheck",testPassed);
        else criteriaExecutionStatuses.put("SDYCheck",testFailed);
//        //метод проверяет актив на наличие поступательного роста его дивидендов в течение 10 прошедших лет
//        //если компания на каком-либо участке исторических данных снижала выплаты по дивидендам, она исключается из выборки
//        criteriaStatus = rapidAPIData.checkDividendsGrowth();

        
//        criteriaStatus = divsCoreData.shouldAnalysisContinue(rapidAPIData.tickersPreviousSelection,rapidAPIData.tickers);
//        if (criteriaStatus) criteriaExecutionStatuses.put("DivCheck",testPassed);
//        else {
//            criteriaExecutionStatuses.put("DivCheck",testFailed);
//            criteriaExecutionStatuses.put("IncomeCheck",testNotExecuted);
//        };

        criteriaStatus = true;
        if (criteriaStatus) criteriaExecutionStatuses.put("DivCheck",testPassed);
        else criteriaExecutionStatuses.put("DivCheck",testFailed);
//        //метод проверяет актив на наличие поступательного роста показателей Net Income и Operating Income за 4 последние года
//        //если значение показателя сокращалось внутри 4х летнего интервала , то компания исключается из выборки
//        criteriaStatus = rapidAPIData.checkIncomeGrowth();
//        criteriaStatus = false;
        if (criteriaStatus) criteriaExecutionStatuses.put("IncomeCheck",testPassed);
        else criteriaExecutionStatuses.put("IncomeCheck",testFailed);
//        logger.log(Level.INFO,"");
//        logger.log(Level.INFO, "итоговый результат отбора компаний:");
        rapidAPIData.tickers.clear();
        rapidAPIData.tickers.add("AMP");
        rapidAPIData.tickers.add("GD");
        rapidAPIData.tickers.add("KMB");
        rapidAPIData.tickers.add("LMT");
        rapidAPIData.tickers.add("POR");
        rapidAPIData.tickers.add("VZ");
        rapidAPIData.tickersPreviousSelection.clear();
        rapidAPIData.tickersPreviousSelection.add("AMP");
        rapidAPIData.tickersPreviousSelection.add("CBU");
        rapidAPIData.tickersPreviousSelection.add("GD");
        rapidAPIData.tickersPreviousSelection.add("KMB");
        rapidAPIData.tickersPreviousSelection.add("LMT");
        rapidAPIData.tickersPreviousSelection.add("POR");
        rapidAPIData.tickersPreviousSelection.add("SRE");
        rapidAPIData.tickersPreviousSelection.add("THG");
        rapidAPIData.tickersPreviousSelection.add("TROW");
        rapidAPIData.tickersPreviousSelection.add("VZ");

        //генерим отчет по последней выборке
        String reportTemplateName = Configuration.reportsFolder + "\\DividendScreenerResultsTemplate.xlsx";
        String excelFirstReport = divsExcelData.generateExcelReport(rapidAPIData.tickers,criteriaExecutionStatuses,reportTemplateName);
        //выбираем компании, которые присутствуют только в предыдущей выборке
        rapidAPIData.findUniqueTickers(rapidAPIData.tickers,rapidAPIData.tickersPreviousSelection);
        //меняем статус последнего успешно пройденного критерия на не пройден для предыдущей выборки
        String testCode = "";
        LinkedList reverseStatuses = new LinkedList(criteriaExecutionStatuses.entrySet());
        Collections.reverse(reverseStatuses);
        for (int i=0; i< reverseStatuses.size();i++){
            String test = reverseStatuses.get(i).toString();
            if (test.contains(testPassed)){
                int div = test.indexOf("=");
                testCode = test.substring(0,div);
                break;
            }
        }
        criteriaExecutionStatuses.put(testCode,testFailed);
        //добавляем отчет данными по предпоследней выборке
        String excelFinalReport = divsExcelData.generateExcelReport(rapidAPIData.uniqueTickers,criteriaExecutionStatuses,excelFirstReport);
        Unirest.shutdown();
    }
}
