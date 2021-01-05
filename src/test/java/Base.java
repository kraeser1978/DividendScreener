import Common.DivsCoreData;
import Common.DivsExcelData;
import Common.RapidAPIData;
import com.codeborne.selenide.Configuration;
import com.mashape.unirest.http.Unirest;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.Test;
import javax.mail.Session;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import static Common.DivsCoreData.props;

public class Base {
    private static Logger logger = Logger.getLogger(Base.class.getSimpleName());
    public static DivsCoreData divsCoreData;
    public static DivsExcelData divsExcelData;
    public static RapidAPIData rapidAPIData;
    public static XSSFSheet companiesSheet;

    @Test
    public void main() throws Exception {
        preparation();
        excelDataFiltering();
//        allUSMarketsDataFiltering();
        reportsGeneration();
    }

    private void sendReportByEmail(String excelFinalReport){
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

    private void preparation() throws Exception {
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

    public void excelDataFiltering() throws Exception {
        boolean criteriaStatus = false;
        rapidAPIData = new RapidAPIData();
        boolean skipMajorTests = rapidAPIData.isStockListCanBeReUsed(props.excelSourceFile(),7);
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
            rapidAPIData.filterBySummaryDetails(props.excelSourceFile());
        }
        rapidAPIData.compareStockAgainstEthalonETF();
        rapidAPIData.checkDividendsGrowth();
        rapidAPIData.checkIncomeGrowth();
    }

    public void allUSMarketsDataFiltering() throws Exception {
        if (rapidAPIData == null)
            rapidAPIData = new RapidAPIData();
        rapidAPIData.getStocksListFromFinnhub();
        ArrayList<String> excelTickers = divsExcelData.getAllTickers(companiesSheet);
        rapidAPIData.cleanUpUSMarketsTickersLists(excelTickers);
        boolean skipMonthlySourceDataUpdate = rapidAPIData.isStockListCanBeReUsed(props.allUSMarketsSourceFile(),30);
        if (!skipMonthlySourceDataUpdate){
            //если файл с исходным списком акций устарел (более 30 дней) - генерим его
            rapidAPIData.monthlyFilterByFundamentals();
        }
        rapidAPIData.filterBySummaryDetails(props.allUSMarketsFilteredFile());
        rapidAPIData.filterByDividendRelatedCriterias();
        rapidAPIData.compareStockAgainstEthalonETF();
        rapidAPIData.checkIncomeGrowth();
    }

    public void reportsGeneration() throws Exception {
        logger.log(Level.INFO, "формирование отчета с результатами отбора компаний...");
        rapidAPIData.sortStockList();
        String excelReport = divsExcelData.generateExcelReport(rapidAPIData.stocksListMap);
        Unirest.shutdown();
        logger.log(Level.INFO, "отчет сформирован в файле " + excelReport);
//        sendReportByEmail(excelReport);
    }
}
