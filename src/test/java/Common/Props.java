package Common;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

public class Props {
    private Properties properties;

    public Props(Properties properties) throws Exception {
        this.properties = properties;
    }

    public Props(String contents) throws Exception {
        this(readPropertiesFile(contents));
    }

    private static Properties readPropertiesFile (String fileName) throws IOException {
        Properties props =  new Properties();
        props.load(new StringReader(fileName));
        return props;
    }

    public String logFilePath(){
        return properties.getProperty("log_file_path");
    }

    public String driverPath(){
        return properties.getProperty("driver_path");
    }

    public String dripinvestingURL(){
        return properties.getProperty("dripinvesting_url");
    }

    public String APIURL(){
        return properties.getProperty("API_URL");
    }

    public String rapidHost(){
        return properties.getProperty("rapidHost");
    }

    public String rapidHostValue(){
        return properties.getProperty("rapidHostValue");
    }

    public String rapidKey(){
        return properties.getProperty("rapidKey");
    }

    public String rapidKeyValue(){
        return properties.getProperty("rapidKeyValue");
    }

    public String expectedNumberOfStocks(){
        return properties.getProperty("expected_number_of_selected_stocks");
    }

    public String maximumNumberOfStocks(){
        return properties.getProperty("maximum_number_of_selected_stocks");
    }

    public String screenshotsFolder(){
        return properties.getProperty("screenshots_folder");
    }

    public String diskAPIURL(){
        return properties.getProperty("diskAPI_URL");
    }

    public String OAuthKey(){
        return properties.getProperty("OAuthKey");
    }

    public String gmailUsername(){
        return properties.getProperty("gmailUsername");
    }

    public String gmailPassword(){
        return properties.getProperty("gmailPassword");
    }

    public String isToSendReportByEmail(){
        return properties.getProperty("isToSendReportByEmail");
    }

    public String testPassed(){
        return properties.getProperty("test_passed");
    }

    public String testFailed(){
        return properties.getProperty("test_failed");
    }

    public String notTested(){
        return properties.getProperty("not_tested");
    }

    public String allUSMarketsSourceFile(){
        return properties.getProperty("all_us_markets_source_list_file_short_name");
    }

    public String excelSourceFile(){
        return properties.getProperty("excel_source_list_file_short_name");
    }

    public String allUSMarketsFilteredFile(){
        return properties.getProperty("all_us_markets_filtered_list_file_short_name");
    }

    public String excelFilteredFile(){
        return properties.getProperty("excel_filtered_list_short_name");
    }

    public String finnhubToken(){
        return properties.getProperty("Finnhub_token");
    }
}
