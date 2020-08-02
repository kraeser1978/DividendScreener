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

import static Common.DivsExcelData.findCell;
import static com.codeborne.selenide.Selenide.download;

public class Base {
    private static Logger logger = Logger.getLogger(Base.class.getSimpleName());
    public String filteringMode;

    @Test
    public void run() throws Exception {
        DivsCoreData.SetUp();
        RapidAPIData rapidAPIData = new RapidAPIData();
        rapidAPIData.tickers.add("AMP");
        rapidAPIData.tickers.add("BBY");
        rapidAPIData.tickers.add("GD");
        rapidAPIData.tickers.add("KMB");
        rapidAPIData.tickers.add("LMT");
        rapidAPIData.tickers.add("MXIM");
        rapidAPIData.tickers.add("NWE");
        rapidAPIData.tickers.add("OZK");
        rapidAPIData.tickers.add("TRV");
        rapidAPIData.tickers.add("VZ");
        rapidAPIData.tickersPreviousSelection.add("AMP");
        rapidAPIData.tickersPreviousSelection.add("BBY");
        rapidAPIData.tickersPreviousSelection.add("CBU");
        rapidAPIData.tickersPreviousSelection.add("GD");
        rapidAPIData.tickersPreviousSelection.add("KMB");
        rapidAPIData.tickersPreviousSelection.add("LMT");
        rapidAPIData.tickersPreviousSelection.add("LNT");
        rapidAPIData.tickersPreviousSelection.add("MXIM");
        rapidAPIData.tickersPreviousSelection.add("NWE");
        rapidAPIData.tickersPreviousSelection.add("OZK");
        rapidAPIData.tickersPreviousSelection.add("SRE");
        rapidAPIData.tickersPreviousSelection.add("THG");
        rapidAPIData.tickersPreviousSelection.add("TROW");
        rapidAPIData.tickersPreviousSelection.add("TRV");
        rapidAPIData.tickersPreviousSelection.add("VZ");
        rapidAPIData.checkIncomeGrowth();
        logger.log(Level.INFO,"отработало");
    }

    @Test
    public void main() throws Exception {
        logger.log(Level.INFO,"считываем настройки...");
        DivsCoreData divsCoreData = new DivsCoreData();
        divsCoreData.SetUp();
//        String downloadedName = Configuration.reportsFolder + "\\USDividendChampions";
//        File downloadedXlsFile = new File(downloadedName + ".xlsx");
//        File downloadedFile = new File(downloadedName);
//        logger.log(Level.INFO, "загружаем Excel файл с дивидендами в папку, указанную в файле параметров");
//        download(divsCoreData.props.dripinvestingURL(), 5000);
//        logger.log(Level.INFO, "файл скачан");
//        //переименовываем файл
//        //удаляем предыдущую версию, если она существует
//        if (downloadedXlsFile.exists()) FileUtils.forceDelete(downloadedXlsFile);
//        FileUtils.moveFile(downloadedFile, downloadedXlsFile);
//        logger.log(Level.INFO, "удаляем лишние вкладки в файле...");
//        DivsExcelData divsExcelData = new DivsExcelData();
        String newName = Configuration.reportsFolder + "\\USDividendChampions_singleTab";
        File newFileName = new File(newName + ".xlsx");
//        divsExcelData.removeSheets(downloadedXlsFile,newFileName);
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
        rapidAPIData.tickers = divsExcelData.getCompaniesTickersByNames(companiesSheet,divsExcelData.companyNames);
        rapidAPIData.tickersPreviousSelection = divsExcelData.getCompaniesTickersByNames(companiesSheet,divsExcelData.companyNamesPreviousSelection);
        //метод сравнивает прирост стоимости акции компании с приростом стоимости эталонного ETF SDY за прошедшие 10 лет
        //если актив вырос меньше SDY, компания исключается из выборки
        rapidAPIData.compareStockAgainstEthalonETF();
        //метод проверяет актив на наличие поступательного роста его дивидендов в течение 10 прошедших лет
        //если компания на каком-либо участке исторических данных снижала выплаты по дивидендам, она исключается из выборки
        rapidAPIData.checkDividendsGrowth();
        //метод проверяет актив на наличие поступательного роста показателей Net Income и Operating Income за 4 последних года
        //если значение показателя сокращалось внутри 4х летнего интервала , то компания исключается из выборки
        rapidAPIData.checkIncomeGrowth();
        logger.log(Level.INFO, "итоговый результат отбора компаний:");
        logger.log(Level.INFO,rapidAPIData.tickers.toString());
//        String resultName = Configuration.reportsFolder + "\\USDividendChampions_filtered";
//        File resultFileName = new File(resultName + ".xlsx");
//        divsExcelData.saveFilteredResults(companiesBook,resultFileName);
    }

}
