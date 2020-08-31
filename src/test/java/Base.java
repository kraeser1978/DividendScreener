import Common.DivsCoreData;
import Common.DivsExcelData;
import Common.RapidAPIData;
import Common.Stocks;
import com.codeborne.selenide.Configuration;
import com.mashape.unirest.http.Unirest;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.Test;
import javax.mail.Session;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import static Common.DivsCoreData.props;

public class Base {
    private static Logger logger = Logger.getLogger(Base.class.getSimpleName());
    public String filteringMode;
    public static DivsCoreData divsCoreData;
    public static DivsExcelData divsExcelData;
    public static RapidAPIData rapidAPIData;
    public static XSSFSheet companiesSheet;
    public static LinkedHashMap<String, Stocks> currentSelection = new LinkedHashMap<>();
    public static LinkedHashMap<String, Stocks> uniqueSelection = new LinkedHashMap<>();

    private static void sendReportByEmail(String excelFinalReport){
        if (divsCoreData.props.isToSendReportByEmail().equals("true")) {
            DateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy");
            Date today = new Date();
            String dateStr = dateFormat.format(today) + "г.";
            logger.log(Level.INFO, "отчет высылается всем активным пользователям по email... ");
            ArrayList<String> emails = divsCoreData.getActiveUsersEmailList();
            Session sess = divsCoreData.createGmailSession();
            divsCoreData.sendEmail(sess,emails,"Еженедельная выборка лучших дивидендных акций США",
                    "Добрый день, прошу ознакомиться с выборкой дивидендных акций компаний США от " + dateStr,excelFinalReport);
        }
    }

    private static LinkedHashMap<String, Stocks> fillInStocksData(ArrayList<String> tickers){
        String companyName = "";
        LinkedHashMap<String, Stocks> selection = new LinkedHashMap<>();
        for (int t = 0; t < tickers.size(); t++) {
            Stocks stocks = new Stocks();
            String ticker = tickers.get(t);
            if (divsExcelData.companyNamesAndTickers.keySet().contains(ticker)) {
                //извлекаем полное название компании
                companyName = divsExcelData.companyNamesAndTickers.get(ticker);
                stocks.setCompanyName(companyName);
            }
            stocks.setTicker(ticker);
            Row row = divsExcelData.findCompanyRow(companiesSheet,companyName);
            Cell lastPriceHeader = divsExcelData.findCell(companiesSheet,"Price");
            int lastPriceHeaderCol = lastPriceHeader.getColumnIndex();
            Cell yieldHeader = divsExcelData.findCell(companiesSheet,"Yield");
            int yieldHeaderCol = yieldHeader.getColumnIndex();
            Cell lastPrice = row.getCell(lastPriceHeaderCol);
            Cell yield = row.getCell(yieldHeaderCol);
            stocks.setLastPrice(lastPrice.getNumericCellValue());
            stocks.setYield(yield.getNumericCellValue());
            selection.put(tickers.get(t), stocks);
        }
        return selection;
    }

    private static void preparation() throws Exception {
        divsCoreData = new DivsCoreData();
        divsCoreData.SetUp();
//        divsCoreData.downloadDivsFile();
        String newName = Configuration.reportsFolder + "\\USDividendChampions_singleTab";
        File newFileName = new File(newName + ".xlsx");
        divsExcelData = new DivsExcelData();
        logger.log(Level.INFO, "загружаем данные из файла...");
        divsExcelData.getDivsBook(newFileName);
        logger.log(Level.INFO, "задаем критерии поиска и фильтрации...");
        companiesSheet = divsExcelData.getDivsSheet(newFileName);
        divsExcelData.setSearchCriteria(companiesSheet);
    }

    @Test
    public static void main(String[] args) throws Exception {
        preparation();
        boolean criteriaStatus = false;
        rapidAPIData = new RapidAPIData();
//        rapidAPIData.getStocksListFromFinnhub();
//        ArrayList<String> excelTickers = divsExcelData.getAllTickers(companiesSheet);
//        rapidAPIData.cleanUpUSMarketsTickersLists(excelTickers);
//        rapidAPIData.filterBySummaryDetails();
        boolean skipMajorTests = rapidAPIData.isDraftListCanBeReUsed();
        rapidAPIData.filterByDividendRelatedCriterias();
        if (!skipMajorTests){
            HashMap<String,String> criteriaExecutionStatuses = new LinkedHashMap<>();
            criteriaExecutionStatuses = divsExcelData.setDefaultExecutionStatus(props.notTested());
            logger.log(Level.INFO, "Yrs - компании, которые платят дивиденды 15 и более лет");
            Cell numberOfYears = divsExcelData.findCell(companiesSheet,"Yrs");
            divsExcelData.setAutoFilter(companiesSheet,numberOfYears.getColumnIndex(),"15");
            criteriaExecutionStatuses.put("Yrs",props.testPassed());
            logger.log(Level.INFO, "выполняем предварительный отбор по следующим фильтрам:");
            for (Map.Entry<String, ArrayList<String>> entry : divsExcelData.fieldsSearchCriterias.entrySet()) {
                String key = entry.getKey();
                ArrayList<String> criterias = entry.getValue();
                int columnSeqNo = divsExcelData.fieldsColumns.get(key);
                divsExcelData.comparisonType = criterias.get(2);
                criteriaStatus = divsExcelData.filterCompanies(columnSeqNo,criterias.get(0),criterias.get(1));
                if (criteriaStatus) criteriaExecutionStatuses.put(key,props.testPassed());
                else criteriaExecutionStatuses.put(key,props.testFailed());
            }
            rapidAPIData.tickers = divsExcelData.getCompaniesTickersByNames(companiesSheet,divsExcelData.companyNames);
            rapidAPIData.filterBySummaryDetails();
        }
        rapidAPIData.compareStockAgainstEthalonETF();
        rapidAPIData.checkDividendsGrowth();
        rapidAPIData.checkIncomeGrowth();
        reportsGeneration2();
    }

    @Test
    public static void reportsGeneration2() throws Exception {
        logger.log(Level.INFO, "формирование отчета с результатами отбора компаний...");
        rapidAPIData.sortStockList();
        String excelReport = divsExcelData.generateExcelReport2(rapidAPIData.stocksListMap);
        Unirest.shutdown();
        logger.log(Level.INFO, "отчет сформирован в файле " + excelReport);
        sendReportByEmail(excelReport);
    }

    public static void reportsGenerationAfterFiltering(HashMap<String, String> criteriaExecutionStatuses) throws IOException {
        //меняем статус последнего успешно пройденного критерия на пройден
        criteriaExecutionStatuses = divsExcelData.changeExecutionStatus(criteriaExecutionStatuses,props.notTested(),props.testPassed(),"");
        String reportTemplateName = Configuration.reportsFolder + "\\DividendScreenerResultsTemplate.xlsx";
        //записываем данные по выбранным акциям в объекты
        currentSelection = fillInStocksData(rapidAPIData.tickers);
        //генерим отчет по последней выборке
        logger.log(Level.INFO, "формирование отчета с результатами отбора компаний...");
        String excelFirstReport = divsExcelData.generateExcelReport(currentSelection,criteriaExecutionStatuses,reportTemplateName);

        logger.log(Level.INFO, "выбираем компании, которые присутствуют только в предыдущей выборке...");
        rapidAPIData.findUniqueTickers(rapidAPIData.tickers,rapidAPIData.tickersPreviousSelection);
        logger.log(Level.INFO, "отбрасываем часть компаний из предыдущей выборки - до максимально возможного кол-ва в отчете...");
        rapidAPIData.resetNoOfStocksToMaxLimit();
        //записываем данные по выбранным акциям в объекты
        uniqueSelection = fillInStocksData(rapidAPIData.uniqueTickers);
        //меняем статус последнего успешно пройденного критерия на не пройден для предпоследней выборки
        criteriaExecutionStatuses = divsExcelData.changeExecutionStatus(criteriaExecutionStatuses,props.testPassed(),props.testFailed(),"reverse");
        logger.log(Level.INFO, "добавляем в отчет данные по предпоследней выборке...");
        String excelFinalReport = divsExcelData.generateExcelReport(uniqueSelection,criteriaExecutionStatuses,excelFirstReport);
        Unirest.shutdown();
        logger.log(Level.INFO, "отчет сформирован в файле " + excelFinalReport);
        //отправляем по списку емейл адресов пользователей
        sendReportByEmail(excelFinalReport);
    }
}
