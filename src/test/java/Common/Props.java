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

    public String appURL(){
        return properties.getProperty("rmanpo_url");
    }

    public String userName(){
        return properties.getProperty("user_name");
    }

    public String userPass(){
        return properties.getProperty("password");
    }

    public String passportScanFile(){
        return properties.getProperty("passport_scan_file_path");
    }

    public String snilsScanFile(){
        return properties.getProperty("snils_scan_file_path");
    }

    public String screenshotsFolder(){
        return properties.getProperty("screenshots_folder");
    }

}
