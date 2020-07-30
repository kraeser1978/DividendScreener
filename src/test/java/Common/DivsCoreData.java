package Common;

import com.codeborne.selenide.*;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.interactions.Actions;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.WebDriverRunner.closeWebDriver;

public class DivsCoreData {
    private static Logger logger = Logger.getLogger(DivsCoreData.class.getSimpleName());
    protected static WebDriver driver;
    static FileHandler fh = null;
    public static HashMap <String,String> dataInput = new HashMap<String, String>();
    public static Properties locators = new Properties();
    public static Set<String> locatorCodes;
    public static Props props;

    public static void SetUp() throws Exception {
        logger.log(Level.INFO,"считываем параметры проекта из properties файлов...");
        //определяем индивидуальные параметры
        String propsFilePath = System.getenv("TEMP") + "\\dividendScreener.properties";
        String paramsFile = FileUtils.readFileToString(new File(propsFilePath), "UTF-8");
        props = new Props(paramsFile);
        Configuration.reportsFolder = props.screenshotsFolder();
        //задаем режим логирования сообщений в лог файл
        logInit(props.logFilePath());
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

    public static void downloadDivsFile() throws IOException, InterruptedException {
        open(props.dripinvestingURL());
        Thread.sleep(3000);
        Actions action = new Actions(driver);
        action.sendKeys(Keys.PAGE_DOWN).build().perform();
        Thread.sleep(1000);
        $(By.xpath("//strong[text()='Dividend Champions Excel Spreadsheet']//parent::a"))
                .shouldBe(Condition.enabled).download();
    }

}
