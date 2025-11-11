package iteration2test.ui;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selectors;
import com.codeborne.selenide.Selenide;
import models.CreateUserRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.codeborne.selenide.Selenide.$;

public class DepositUiTest {
@BeforeAll
    public static void setupSelenoid() {
    Configuration.remote = "http://localhost:4444/wd/hub";
    Configuration.baseUrl = "http://192.168.1.65:3000";
    Configuration.browser = "chrome";
    Configuration.browserSize = "1920x1080";

    Configuration.browserCapabilities.setCapability("selenoid:options",
            Map.of("enableVNC", true, "enableLog", true)
    );
    }

@Test
    public void adminCanLoginCorrectDateTest() {
    CreateUserRequest admin = CreateUserRequest.builder().username("admin").password("admin").build();

    Selenide.open("/");

    $(Selectors.byAttribute("placeholder", "Username")).sendKeys(admin.getUsername());
    $(Selectors.byAttribute("placeholder", "Password")).sendKeys(admin.getPassword());
    $("button").click();
    $(Selectors.byText("Admin Panel")).shouldBe(Condition.visible);


    }

}
