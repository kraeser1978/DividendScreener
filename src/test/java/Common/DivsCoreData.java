package Common;

import com.codeborne.selenide.*;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.interactions.Actions;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.WebDriverRunner.closeWebDriver;

public class DivsCoreData {
    private static Logger logger = Logger.getLogger(DivsCoreData.class.getSimpleName());
    protected static WebDriver driver;
    static FileHandler fh = null;
    public static HashMap <String,String> dataInput = new HashMap<String, String>();
    public static Properties locators = new Properties();
    public static Set<String> locatorCodes;
    public static Props props;

    public void reportFinalFilteredLists(ArrayList<String> tickers, HashMap<String,String> namesAndTickers, String selectionText){
        logger.log(Level.INFO,"");
        logger.log(Level.INFO,selectionText);
        logger.log(Level.INFO,"отобрано компаний = " + tickers.size());
        for (int i = 0; i < tickers.size();i++){
            String ticketCode = tickers.get(i);
            if (namesAndTickers.keySet().contains(ticketCode)){
                //извлекаем полное название компании
                String companyName = namesAndTickers.get(ticketCode);
                logger.log(Level.INFO,companyName + " (" + ticketCode + ")");
            }
        }
    }

    public static boolean shouldAnalysisContinue(ArrayList<String> previousSelection, ArrayList<String> currentSelection){
        boolean flag = true;
        //продолжаем, если текущая выборка еще не заполнена
        if (currentSelection.size() == 0) return true;
        //считываем параметр со значением ожидаемого количества компаний для финального отбора
        int expectedNumOfStocksToSelect = Integer.parseInt(props.expectedNumberOfStocks());
        //если ожидаемое кол-во отобранных компаний находится между размером предыдущей и текущей выборок - останавливаемся и прекращаем дальнейший отбор
        if (previousSelection.size() >= expectedNumOfStocksToSelect && currentSelection.size() < expectedNumOfStocksToSelect) flag = false;
        if (previousSelection.size() > expectedNumOfStocksToSelect && currentSelection.size() <= expectedNumOfStocksToSelect) flag = false;
        return flag;
    }

    public static void SetUp() throws Exception {
        logger.log(Level.INFO,"считываем параметры проекта из properties файлов...");
//        String path = Base.class.getProtectionDomain().getCodeSource().getLocation().getPath();
//        String decodedPath = URLDecoder.decode(path,"UTF-8");
        //определяем индивидуальные параметры
        File file = new File(DivsCoreData.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        String currentWorkingDir = file.getParentFile().getPath();
        String propsFilePath = currentWorkingDir + "\\dividendScreener.properties";
        String paramsFile = FileUtils.readFileToString(new File(propsFilePath), "UTF-8");
        props = new Props(paramsFile);
        //задаем папку для выгрузки файла MS Excel со списком дивидендных компаний и скриншотов с ошибками
        Configuration.reportsFolder = currentWorkingDir;
        //задаем режим логирования сообщений в лог файл
        logInit(currentWorkingDir + "\\dividendScreener_1.0.log");
        logger.log(Level.INFO, "");
        logger.log(Level.INFO, "");
//        //считываем локаторы
//        String locatorsFilePath = System.getenv("RmanpoQA_personal_case_locators");
//        String locatorsText = FileUtils.readFileToString(new File(locatorsFilePath), "UTF-8");
//        locators.load(new StringReader(locatorsText));
//        locatorCodes = locators.stringPropertyNames();

//        //задаем путь к файлам скриншотов с ошибками в ходе выполнения отбора компаний
//        logger.log(Level.INFO,"прибиваем chromedriver.exe процесс, если он остался от предыдущей сессии");
//        Process process = Runtime.getRuntime().exec("taskkill /F /IM chromedriver.exe");
//        process.waitFor();
//        process.destroy();
//        //задаем опции запуска браузера
//        ChromeOptions options = new ChromeOptions();
//        options.addArguments("--no-sandbox");
//        options.addArguments("--disable-dev-shm-usage");
//        options.addArguments("--start-maximized");
//        options.addArguments("--disable-notifications");
//        options.addArguments("--disable-extenstions");
//        options.addArguments("--disable-infobars");
//        options.addArguments("--disable-popup-blocking");
//        options.addArguments("--incognito");
//        options.addArguments("--disable-default-apps");
//        options.addArguments("--enable-precise-memory-info");
//        System.setProperty("webdriver.chrome.driver",props.driverPath());
//        logger.log(Level.INFO,"запускаем Хром...");
//        driver = new ChromeDriver(options);
//        WebDriverRunner.setWebDriver(driver);
    }

    public static void tearDown(){
        if (!driver.getWindowHandle().equals("")) {
            fh.flush();
            fh.close();
            driver.close();
            closeWebDriver();
        }
    }

    public static void logInit(String lofFileName){
        try {
            fh = new FileHandler(lofFileName,true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Logger log = Logger.getLogger("");
        fh.setFormatter(new SimpleFormatter());
        log.addHandler(fh);
        log.setLevel(Level.CONFIG);
    }

    public static void downloadDivsFile() throws IOException {
        String downloadedName = Configuration.reportsFolder + "\\USDividendChampions";
        File downloadedXlsFile = new File(downloadedName + ".xlsx");
        File downloadedFile = new File(downloadedName);
        logger.log(Level.INFO, "загружаем Excel файл с дивидендами в текущую папку...");
        download(props.dripinvestingURL(), 10000);
        logger.log(Level.INFO, "файл скачан");
        logger.log(Level.INFO,"переименовываем файл, удаляем предыдущую версию, если она существует...");
        if (downloadedXlsFile.exists()) FileUtils.forceDelete(downloadedXlsFile);
        FileUtils.moveFile(downloadedFile, downloadedXlsFile);
        logger.log(Level.INFO, "загрузка завершена");
        DivsExcelData divsExcelData = new DivsExcelData();
        String newName = Configuration.reportsFolder + "\\USDividendChampions_singleTab";
        File newFileName = new File(newName + ".xlsx");
        //удаляем предыдущую версию, если она существует
        if (newFileName.exists()) FileUtils.forceDelete(newFileName);
//        FileUtils.moveFile(downloadedXlsFile, newFileName);
        logger.log(Level.INFO, "удаляем лишние вкладки в файле...");
        divsExcelData.removeSheets(downloadedXlsFile,newFileName);
        logger.log(Level.INFO,"файл готов к работе");
        //алгоритм интерактивной закачки файла с сайта
//        open(props.dripinvestingURL());
//        Thread.sleep(3000);
//        Actions action = new Actions(driver);
//        action.sendKeys(Keys.PAGE_DOWN).build().perform();
//        Thread.sleep(1000);
//        $(By.xpath("//strong[text()='Dividend Champions Excel Spreadsheet']//parent::a"))
//                .shouldBe(Condition.enabled).download();
    }
}
