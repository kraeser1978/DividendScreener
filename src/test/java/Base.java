import Common.DivsCoreData;
import Common.DivsExcelData;
import Common.RapidAPIData;
import com.codeborne.selenide.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.junit.Test;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import static Common.DivsCoreData.props;
import static com.codeborne.selenide.Selenide.download;

public class Base {
    private static Logger logger = Logger.getLogger(Base.class.getSimpleName());
    ArrayList<String> companies = new ArrayList<String>();
    ArrayList<String> tickers = new ArrayList<String>();
    public String filteringMode;

    @Test
    public void run() throws Exception {
        DivsCoreData.SetUp();
        RapidAPIData rapidAPIData = new RapidAPIData();
//        rapidAPIData.tickers.add("AMP");
//        rapidAPIData.tickers.add("BBY");
//        rapidAPIData.tickers.add("CBU");
//        rapidAPIData.tickers.add("GD");
//        rapidAPIData.tickers.add("KMB");
//        rapidAPIData.tickers.add("LMT");
        rapidAPIData.tickers.add("LNT");
//        rapidAPIData.tickers.add("MXIM");
//        rapidAPIData.tickers.add("OZK");
//        rapidAPIData.tickers.add("SRE");
//        rapidAPIData.tickers.add("THG");
//        rapidAPIData.tickers.add("TROW");
//        rapidAPIData.tickers.add("TRV");
        rapidAPIData.tickers.add("VZ");
        tickers = rapidAPIData.checkIncomeGrowth();
        logger.log(Level.INFO,"отработало");
    }

    public void singleTickerAnalysis(){

    }

    public void bulkAnalysis(){

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
        logger.log(Level.INFO, "считываем данные по компаниям...");
        XSSFSheet companiesSheet = divsExcelData.getDivsSheet(newFileName);
        logger.log(Level.INFO, "находим поля, по которым будет проводиться отбор компаний...");
        //считываем порядковые номера полей для последующей фильтрации данных
        Cell numberOfYears = divsExcelData.findCell(companiesSheet,"Yrs");
        Cell yieldValue = divsExcelData.findCell(companiesSheet,"Yield");
        Cell payoutsValue = divsExcelData.findCell(companiesSheet,"Payouts/");
        Cell mrValue = divsExcelData.findCell(companiesSheet,"Inc.");
        Cell exDivValue = divsExcelData.findCell(companiesSheet,"Ex-Div");
        Cell EPSValue = divsExcelData.findCell(companiesSheet,"EPS%");
        Cell MktCapValue = divsExcelData.findCell(companiesSheet,"MktCap");

        logger.log(Level.INFO, "загружаем список компаний из файла...");
        divsExcelData.getDivsBook(newFileName);
        divsExcelData.comparisonType = "GREATER_THAN_OR_EQUALS";
        logger.log(Level.INFO, "выполняем предварительный отбор по следующим фильтрам:");
        logger.log(Level.INFO, "параметр Yrs - выбираем компании, которые платят дивиденды 15 и более лет...");
        divsExcelData.setAutoFilter(companiesSheet,numberOfYears.getColumnIndex(),"15");
        logger.log(Level.INFO, "параметр Div.Yield - дивиденды от 2.5% годовых...");
        divsExcelData.filterCompanies(yieldValue.getColumnIndex(),"2.50");
        logger.log(Level.INFO, "параметр Payouts/Year - частота выплаты дивидендов - не реже 4 раз в год...");
        divsExcelData.filterCompanies(payoutsValue.getColumnIndex(),"4");
        logger.log(Level.INFO, "параметр MR%Inc. - ежегодный прирост дивидендов - от 2% в год...");
        divsExcelData.filterCompanies(mrValue.getColumnIndex(),"2.00");
        logger.log(Level.INFO, "параметр Last Increased on: Ex-Div - дата последнего повышения дивидендов - не позже, чем год назад...");
        String lastYear = divsExcelData.getPrevYear();
        divsExcelData.filterCompanies(exDivValue.getColumnIndex(),lastYear);
        divsExcelData.comparisonType = "LESSER_THAN";
        logger.log(Level.INFO, "параметр EPS%Payout - доля прибыли, направляемая на выплату дивидендов - не более 70%...");
        divsExcelData.filterCompanies(EPSValue.getColumnIndex(),"70.00");

        divsExcelData.comparisonType = "GREATER_THAN_OR_EQUALS";
        logger.log(Level.INFO, "параметр MktCap($Mil) - выбираем компании с капитализацией свыше 2млрд.долл...");
        divsExcelData.filterCompanies(MktCapValue.getColumnIndex(),"2000.00");

        divsExcelData.comparisonType = "LESSER_THAN";
        logger.log(Level.INFO, "параметр TTM P/E - срок окупаемости инвестиций в акции компании в годах - для американского рынка не должен превышать 21");
        divsExcelData.filterCompanies(EPSValue.getColumnIndex()+1,"21.00");

        //считываем тикеры компаний
        RapidAPIData rapidAPIData = new RapidAPIData();
        //передаем массив тикеров в класс для выполнения REST запросов к market data source
        rapidAPIData.tickers = divsExcelData.getCompaniesTickersByNames(companiesSheet);
        logger.log(Level.INFO, "сравниваем прирост стоимости акций компаний с приростом стоимости эталонного ETF SDY за прошедшие 10 лет...");
        logger.log(Level.INFO, "если актив вырос меньше SDY, компания исключается из выборки");
        tickers = rapidAPIData.compareStockAgainstEthalonETF();

        logger.log(Level.INFO, "метод проверяет актив на наличие поступательного роста его дивидендов в течение 10 прошедших лет");
        logger.log(Level.INFO, "если компания на каком-либо участке исторических данных снижала выплаты по дивидендам, она исключается из выборки");
        tickers = rapidAPIData.checkDividendsGrowth();

        logger.log(Level.INFO, "сохраняем результаты фильтров в файле...");
//        String resultName = Configuration.reportsFolder + "\\USDividendChampions_filtered";
//        File resultFileName = new File(resultName + ".xlsx");
//        divsExcelData.saveFilteredResults(companiesBook,resultFileName);
    }

}
