package iteration2test.ui;

import api.configs.Config;
import com.codeborne.selenide.Configuration;
import common.extensions.*;
import iteration2test.api.BaseTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.Map;

@ExtendWith(AdminSessionExtension.class)
@ExtendWith(UserSessionExtension.class)
@ExtendWith(BrowserMatchExtension.class)
@ExtendWith(DeviceMatchExtension.class)
@ExtendWith(TestDataCleanupExtension.class)
public class BaseUiTest extends BaseTest {
    @BeforeAll
    static void setupSelenoid() {
        Configuration.remote = Config.getProperty("uiRemote");
        Configuration.baseUrl = Config.getProperty("uiBaseUrl");          // Origin = localhost
        Configuration.browser = Config.getProperty("browser");
        Configuration.browserSize = Config.getProperty("browserSize");
        Configuration.headless = true;

        String hostIp = Config.getProperty("uiHostIp");                         // мой IPv4 Wi-Fi
        ChromeOptions chrome = new ChromeOptions()
                .addArguments("--host-resolver-rules=MAP localhost " + hostIp);
        chrome.setCapability("selenoid:options", Map.of("enableVNC", true, "enableLog", true));
        Configuration.browserCapabilities = chrome;
    }
}
