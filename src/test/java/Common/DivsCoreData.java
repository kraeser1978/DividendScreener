package Common;

import com.codeborne.selenide.*;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.bouncycastle.util.encoders.Base64Encoder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.WebDriverRunner.closeWebDriver;
import org.junit.Test;

public class DivsCoreData {
    private static Logger logger = Logger.getLogger(DivsCoreData.class.getSimpleName());
    protected static WebDriver driver;
    static FileHandler fh = null;
    public static Props props;

    public ArrayList<String> getActiveUsersEmailList(){
        logger.log(Level.INFO, "Формируем список емейлов активных пользователей...");
        ArrayList<String> validEmails = new ArrayList<>();
        String pclistFileName = Configuration.reportsFolder + "\\emailslist.txt";
        List<String> fileContents = new ArrayList<String>();
        //считываем файл со списком емейлов зарегистрированных пользователей
        try {
            fileContents = FileUtils.readLines(new File(pclistFileName), "UTF-8");
        } catch (IOException e) {
            logger.log(Level.INFO,"Ошибка при считывании файла с емейлами");
            e.printStackTrace();
        }
        //берем сегодняшнюю дату
        DateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy");
        Date today = new Date();
        Date userEndLicense =  null;
        //составляем список активных емейл адресов
        for (int i=0; i < fileContents.size(); i++){
            String currentEmail = fileContents.get(i);
            int currentEmailExpirationDatePos = currentEmail.indexOf("=");
            String currentEmailExpirationDate = currentEmail.substring(currentEmailExpirationDatePos+2);
            //конвертируем полученную из файла дату
            try {
                userEndLicense = new SimpleDateFormat("dd/MM/yyyy").parse(currentEmailExpirationDate);
            } catch (ParseException e) {
                logger.log(Level.INFO,"Ошибка при конвертации даты из файла с емейлами.");
                e.printStackTrace();
            }
            String emailAdress = currentEmail.substring(0,currentEmailExpirationDatePos-1);
            //сравниваем дату окончания абонентской платы с текущей
            //если срок действия абонента еще не истек, пользователь включается в список получающих отчет
            int comResult = userEndLicense.compareTo(today);
            if (comResult >= 0 ) validEmails.add(emailAdress);
                //если срок действия абонента истек, приложение прекращает работу
            else {
                String userEndLicenseStr = dateFormat.format(userEndLicense) + "г.";
                logger.log(Level.INFO, "Для пользователя с емейлом " + emailAdress + " подписка неактивна и была завершена " + userEndLicenseStr);
            }
        }
        return validEmails;
    }

    public Session createGmailSession(){
        final String gmailUsername = props.gmailUsername();
        final String gmailPassword = props.gmailPassword();
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        Session session = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(gmailUsername, gmailPassword);
                    }
                });
        return session;
    }

    public void sendEmail(Session session, ArrayList<String> emailsList, String subject, String body, String attachment) {
        Multipart multipart = new MimeMultipart();
        try {
            // Create the message body part
            BodyPart messageBodyPart = new MimeBodyPart();
            // Fill the message
            messageBodyPart.setText(body);
            // Create a multipart message for attachment
            // Set text message part
            multipart.addBodyPart(messageBodyPart);
            // Second part is attachment
            messageBodyPart = new MimeBodyPart();
            DataSource source = new FileDataSource(attachment);
            messageBodyPart.setDataHandler(new DataHandler(source));
            int shortFileNameStartPos = attachment.lastIndexOf("\\");
            String fileName = attachment.substring(shortFileNameStartPos+1);
            messageBodyPart.setFileName(fileName);
            multipart.addBodyPart(messageBodyPart);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        for (int i=0; i < emailsList.size(); i++){
            try
            {
                MimeMessage msg = new MimeMessage(session);
                //set message headers
                msg.addHeader("Content-type", "text/HTML; charset=UTF-8");
                msg.addHeader("format", "flowed");
                msg.addHeader("Content-Transfer-Encoding", "8bit");

                msg.setFrom(new InternetAddress("dividendscreener@gmail.com", "DividendAutoScreener"));
                msg.setReplyTo(InternetAddress.parse("serk777@inbox.ru", false));
                msg.setSubject(subject, "UTF-8");
                msg.setSentDate(new Date());
                msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailsList.get(i), false));

                // Send the complete message parts
                msg.setContent(multipart);
                Transport.send(msg);
                logger.log(Level.INFO, "Отчет отправлен по адресу " + emailsList.get(i));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public boolean checkUserIsEnabled(){
        boolean isUserEnabled =  false;
        String pclistFileName = Configuration.reportsFolder + "\\pclist.txt";
        String fileContents = null;
        //считываем файл со списком имен компьютеров зарегистрированных пользователей
        try {
            fileContents = FileUtils.readFileToString(new File(pclistFileName), "UTF-8");
        } catch (IOException e) {
            logger.log(Level.INFO,"Ошибка при считывании файла дат. Обратитесь к разработчику по email: " + props.gmailUsername());
            e.printStackTrace();
        }
        //считываем список компьютеров пользователей и их конечные даты абонентской платы по пользователям
        Properties pclist =  new Properties();
        try {
            pclist.load(new StringReader(fileContents));
        } catch (IOException e) {
            logger.log(Level.INFO,"Ошибка при разборе данных из файла дат. Обратитесь к разработчику по email: " + props.gmailUsername());
            e.printStackTrace();
        }
        Date userEndLicense =  null;
        //проверяем, есть ли комп пользователя в списке
        String currentUserPC = System.getenv("COMPUTERNAME");
        boolean isUserIncluded = fileContents.contains(currentUserPC);
        //если компьютер пользователя в списке - проверяем дату окончания абонентской платы
        if (isUserIncluded) {
            String dateFromFile = pclist.getProperty(currentUserPC);
            //берем сегодняшнюю дату
            DateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy");
            Date today = new Date();
            //конвертируем дату из файла
            try {
                userEndLicense = new SimpleDateFormat("dd/MM/yyyy").parse(dateFromFile);
            } catch (ParseException e) {
                logger.log(Level.INFO,"Ошибка при конвертации даты из файла. Обратитесь к разработчику по email: " + props.gmailUsername());
                e.printStackTrace();
            }
            //сравниваем дату окончания абонентской платы с текущей
            //если срок действия абонента еще не истек, пользователь продолжает работать с приложением
            if (userEndLicense.compareTo(today) >= 0 ) isUserEnabled = true;
            //если срок действия абонента истек, приложение прекращает работу
            else {
                String userEndLicenseStr = dateFormat.format(userEndLicense) + "г.";
                logger.log(Level.INFO, "Ваша подписка неактивна и была завершена " + userEndLicenseStr + " Необходимо продлить подписку на приложение. ");
                logger.log(Level.INFO,"Пожалуйста свяжитесь с разработчиком по email: " + props.gmailUsername());
                isUserEnabled = false;
            }
        }
        else {
            //добавляем има компьютера нового пользователя в файл с 7 дневным пробным периодом
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, 7);
            Date inSevenDays = cal.getTime();
            DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
            String newExpDate = dateFormat.format(inSevenDays);
            String newFileContents = fileContents + "\r\n" + currentUserPC + " = " + newExpDate;
            try {
                FileUtils.write(new File(pclistFileName),newFileContents,"UTF-8");
                //загружаем обновленный файл с именем компьютера нового пользователя
                uploadFileToDisk();
                logger.log(Level.INFO, "Ваша подписка на приложение ранее не была оформлена. Вам предоставляется 7 дневный пробный период использования приложения. ");
                logger.log(Level.INFO,"По истечении пробного периода для оформления подписки пожалуйста свяжитесь с разработчиком по email: " + props.gmailUsername());
                pressEnterToContinue();
            } catch (IOException e) {
                e.printStackTrace();
                logger.log(Level.INFO,"Ошибка сохранения нового пользователя. Обратитесь к разработчику по email: " + props.gmailUsername());
            }
            isUserEnabled =  true;
        }
        return isUserEnabled;
    }

    private void pressEnterToContinue()
    {
        System.out.println("Для продолжения работы нажмите клавишу Enter...");
        try
        {
            System.in.read();
        }
        catch(Exception e)
        {}
    }

    public void fileDownload() throws IOException {
        String diskAPI_URL = props.diskAPIURL();
        String OAuthKey = props.OAuthKey();
        HttpResponse<JsonNode> response1 = null;
        String location = "disk:/Приложения/dividendscreener/pclist.txt";
        String fullFileName = Configuration.reportsFolder + "\\pclist.txt";
        try {
            response1 = Unirest.get(diskAPI_URL + "/disk/resources/download?path=" + location)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "OAuth " + OAuthKey)
                    .asJson();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос ссылки для скачивания файла выдал ошибку");
//            return null;
        }
        logger.log(Level.INFO, "ссылка для скачивания файла сформирована");
        JSONObject jsonObject = response1.getBody().getObject();
        JSONArray jsonArray = response1.getBody().getArray();
        String fileDownloadUrl = jsonObject.getString("href");

        HttpResponse<String> response2 = null;
        try {
            response2 = Unirest.get(fileDownloadUrl)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "OAuth " + OAuthKey)
                    .asString();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "скачивание файла завершилось с ошибкой");
//            return null;
        }
        if (response2.getStatus() == 200) {
            String fileContents = response2.getBody();
            FileUtils.write(new File(fullFileName),fileContents,"UTF-8");
            logger.log(Level.INFO, "файл успешно загружен на диск");
        }
    }

    public void uploadFileToDisk() throws IOException {
        String diskAPI_URL = props.diskAPIURL();
        String OAuthKey = props.OAuthKey();
        HttpResponse<JsonNode> response1 = null;
        String location = "disk:/Приложения/dividendscreener/pclist.txt";
        String fullFileName = Configuration.reportsFolder + "\\pclist.txt";
        String fileContents = FileUtils.readFileToString(new File(fullFileName), "UTF-8");
        fullFileName = fullFileName.replace("\\","%2F");
//        String fileNameEncoded = Base64.getUrlEncoder().encodeToString(fullFileName.getBytes());
        try {
            response1 = Unirest.get(diskAPI_URL + "/disk/resources/upload?path=" + location + "&overwrite=true")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "OAuth " + OAuthKey)
                    .asJson();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "запрос ссылки для закачки файла выдал ошибку");
//            return null;
        }
        logger.log(Level.INFO, "ссылка для загрузки файла сформирована");
        JSONObject jsonObject = response1.getBody().getObject();
        JSONArray jsonArray = response1.getBody().getArray();
        String fileUploadUrl = jsonObject.getString("href");

        HttpResponse<JsonNode> response2 = null;
        try {
            response2 = Unirest.put(fileUploadUrl)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "OAuth " + OAuthKey)
                    .body(fileContents)
                    .asJson();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "закачка файла завершилась с ошибкой");
//            return null;
        }
        logger.log(Level.INFO, "файл успешно загружен на диск");
    }

    public void reportFinalFilteredLists(ArrayList<String> tickers, HashMap<String,String> namesAndTickers, String selectionText){
        logger.log(Level.INFO,"");
        logger.log(Level.INFO,selectionText);
        logger.log(Level.INFO,"отобрано компаний = " + tickers.size());
        for (int i = 0; i < tickers.size();i++){
            String tickerCode = tickers.get(i);
            if (namesAndTickers.keySet().contains(tickerCode)){
                //извлекаем полное название компании
                String companyName = namesAndTickers.get(tickerCode);
                logger.log(Level.INFO,companyName + " (" + tickerCode + ")");
            }
        }
    }

    public boolean shouldAnalysisContinue(ArrayList<String> previousSelection, ArrayList<String> currentSelection){
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

    public void SetUp() throws Exception {
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
        //проверяем пользователя на наличие активной абонентской платы
        fileDownload();
        boolean isToContinueWork = checkUserIsEnabled();
        if (!isToContinueWork) System.exit(1);
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

    public void tearDown(){
        if (!driver.getWindowHandle().equals("")) {
            fh.flush();
            fh.close();
            driver.close();
            closeWebDriver();
        }
    }

    public void logInit(String lofFileName){
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

    public void downloadDivsFile() throws IOException, URISyntaxException {
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
