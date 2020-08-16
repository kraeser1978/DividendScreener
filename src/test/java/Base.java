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
    public static final String testPassed = "Критерию соответствует";
    public static final String testFailed = "Критерию не соответствует";
    public static final String testNotExecuted = "Проверка по критерию не выполнялась";
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
        divsCoreData.downloadDivsFile();
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
        HashMap<String,String> criteriaExecutionStatuses = new LinkedHashMap<>();
        criteriaExecutionStatuses = divsExcelData.setDefaultExecutionStatus(testNotExecuted);
        boolean criteriaStatus = false;
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
        rapidAPIData = new RapidAPIData();
        //передаем массив тикеров в класс для выполнения REST запросов к market data source
        rapidAPIData.tickersPreviousSelection = divsExcelData.getCompaniesTickersByNames(companiesSheet,divsExcelData.companyNamesPreviousSelection);
        rapidAPIData.tickers = divsExcelData.getCompaniesTickersByNames(companiesSheet,divsExcelData.companyNames);
        //метод сравнивает прирост стоимости акции компании с приростом стоимости эталонного ETF SDY за прошедшие 10 лет
        //если актив вырос меньше SDY, компания исключается из выборки
        criteriaStatus = rapidAPIData.compareStockAgainstEthalonETF();
        criteriaStatus = divsCoreData.shouldAnalysisContinue(rapidAPIData.tickersPreviousSelection,rapidAPIData.tickers);
        if (criteriaStatus) criteriaExecutionStatuses.put("SDYCheck",testPassed);
        else {
            reportsGenerationAfterFiltering(criteriaExecutionStatuses);
            return;
        };
        //метод проверяет актив на наличие поступательного роста его дивидендов в течение 10 прошедших лет
        //если компания на каком-либо участке исторических данных снижала выплаты по дивидендам, она исключается из выборки
        criteriaStatus = rapidAPIData.checkDividendsGrowth();
        criteriaStatus = divsCoreData.shouldAnalysisContinue(rapidAPIData.tickersPreviousSelection,rapidAPIData.tickers);
        if (criteriaStatus) criteriaExecutionStatuses.put("DivCheck",testPassed);
        else {
            reportsGenerationAfterFiltering(criteriaExecutionStatuses);
            return;
        };
        //метод проверяет актив на наличие поступательного роста показателей Net Income и Operating Income за 4 последние года
        //если значение показателя сокращалось внутри 4х летнего интервала , то компания исключается из выборки
        criteriaStatus = rapidAPIData.checkIncomeGrowth();
        criteriaStatus = divsCoreData.shouldAnalysisContinue(rapidAPIData.tickersPreviousSelection,rapidAPIData.tickers);
        if (criteriaStatus) criteriaExecutionStatuses.put("IncomeCheck",testPassed);
        reportsGenerationAfterFiltering(criteriaExecutionStatuses);
    }

    public static void reportsGenerationAfterFiltering(HashMap<String, String> criteriaExecutionStatuses) throws IOException {
        //меняем статус последнего успешно пройденного критерия на пройден
        criteriaExecutionStatuses = divsExcelData.changeExecutionStatus(criteriaExecutionStatuses,testNotExecuted,testPassed,"");
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
        criteriaExecutionStatuses = divsExcelData.changeExecutionStatus(criteriaExecutionStatuses,testPassed,testFailed,"reverse");
        logger.log(Level.INFO, "добавляем в отчет данные по предпоследней выборке...");
        String excelFinalReport = divsExcelData.generateExcelReport(uniqueSelection,criteriaExecutionStatuses,excelFirstReport);
        Unirest.shutdown();
        logger.log(Level.INFO, "отчет сформирован в файле " + excelFinalReport);
        //отправляем по списку емейл адресов пользователей
        sendReportByEmail(excelFinalReport);
    }
}
