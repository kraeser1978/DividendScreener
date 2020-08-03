import Common.DivsCoreData;
import Common.DivsExcelData;
import Common.RapidAPIData;
import com.codeborne.selenide.Configuration;
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

    @Test
    public static void main(String[] args) throws Exception {
        DivsCoreData divsCoreData = new DivsCoreData();
        divsCoreData.SetUp();
        divsCoreData.downloadDivsFile();
        String newName = Configuration.reportsFolder + "\\USDividendChampions_singleTab";
        File newFileName = new File(newName + ".xlsx");

        DivsExcelData divsExcelData = new DivsExcelData();
        logger.log(Level.INFO, "загружаем данные из файла...");
        divsExcelData.getDivsBook(newFileName);
        logger.log(Level.INFO, "задаем критерии поиска и фильтрации...");
        XSSFSheet companiesSheet = divsExcelData.getDivsSheet(newFileName);
        divsExcelData.getSearchCriteria(companiesSheet);
        logger.log(Level.INFO, "параметр Yrs - выбираем компании, которые платят дивиденды 15 и более лет...");
        Cell numberOfYears = divsExcelData.findCell(companiesSheet,"Yrs");
        divsExcelData.setAutoFilter(companiesSheet,numberOfYears.getColumnIndex(),"15");
        logger.log(Level.INFO, "выполняем предварительный отбор по следующим фильтрам:");
        for (Map.Entry<String, ArrayList<String>> entry : divsExcelData.fieldsSearchCriterias.entrySet()) {
            String key = entry.getKey();
            ArrayList<String> criterias = entry.getValue();
            int columnSeqNo = divsExcelData.fieldsColumns.get(key);
            divsExcelData.comparisonType = criterias.get(2);
            divsExcelData.filterCompanies(columnSeqNo,criterias.get(0),criterias.get(1));
        }
        //считываем тикеры компаний
        RapidAPIData rapidAPIData = new RapidAPIData();
        //передаем массив тикеров в класс для выполнения REST запросов к market data source
        rapidAPIData.tickersPreviousSelection = divsExcelData.getCompaniesTickersByNames(companiesSheet,divsExcelData.companyNamesPreviousSelection);
        rapidAPIData.tickers = divsExcelData.getCompaniesTickersByNames(companiesSheet,divsExcelData.companyNames);
        //метод сравнивает прирост стоимости акции компании с приростом стоимости эталонного ETF SDY за прошедшие 10 лет
        //если актив вырос меньше SDY, компания исключается из выборки
        rapidAPIData.compareStockAgainstEthalonETF();
        //метод проверяет актив на наличие поступательного роста его дивидендов в течение 10 прошедших лет
        //если компания на каком-либо участке исторических данных снижала выплаты по дивидендам, она исключается из выборки
        rapidAPIData.checkDividendsGrowth();
        //метод проверяет актив на наличие поступательного роста показателей Net Income и Operating Income за 4 последних года
        //если значение показателя сокращалось внутри 4х летнего интервала , то компания исключается из выборки
        rapidAPIData.checkIncomeGrowth();
        logger.log(Level.INFO,"");
        logger.log(Level.INFO, "итоговый результат отбора компаний:");
//        rapidAPIData.tickers.clear();
//        rapidAPIData.tickers.add("GD");
//        rapidAPIData.tickersPreviousSelection.clear();
//        rapidAPIData.tickersPreviousSelection.add("AMP");
//        rapidAPIData.tickersPreviousSelection.add("BBY");
//        rapidAPIData.tickersPreviousSelection.add("GD");
//        rapidAPIData.tickersPreviousSelection.add("KMB");
//        rapidAPIData.tickersPreviousSelection.add("LMT");
//        rapidAPIData.tickersPreviousSelection.add("MXIM");
//        rapidAPIData.tickersPreviousSelection.add("NWE");
//        rapidAPIData.tickersPreviousSelection.add("OZK");
//        rapidAPIData.tickersPreviousSelection.add("TRV");
//        rapidAPIData.tickersPreviousSelection.add("VZ");
        String firstSelection = "Выборка по предпоследнему критерию (кол-во компаний больше ожидаемого):";
        divsCoreData.reportFinalFilteredLists(rapidAPIData.tickersPreviousSelection,divsExcelData.companyNamesAndTickers,firstSelection);
        String secondSelection = "Выборка по последнему критерию (кол-во компаний меньше либо равно ожидаемому):";
        divsCoreData.reportFinalFilteredLists(rapidAPIData.tickers,divsExcelData.companyNamesAndTickers,secondSelection);
    }
}
