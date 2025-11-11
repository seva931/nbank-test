package iteration2test.ui;

import com.codeborne.selenide.*;
import models.AccountResponse;
import models.CreateUserRequest;
import models.LoginUserRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Alert;
import org.openqa.selenium.chrome.ChromeOptions;
import requests.skelethon.Endpoint;
import requests.skelethon.requester.CrudRequester;
import requests.steps.AccountSteps;
import requests.steps.AdminSteps;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.WebDriverConditions.urlContaining;
import static org.assertj.core.api.Assertions.assertThat;

public class Test2 {
    @BeforeAll
    static void setupSelenoid() {
        Configuration.remote  = "http://localhost:4444/wd/hub";
        Configuration.baseUrl = "http://localhost:3000";          // Origin = localhost
        Configuration.browser = "chrome";
        Configuration.browserSize = "1920x1080";

        String hostIp = "192.168.1.65";                            // ваш IPv4 Wi-Fi
        ChromeOptions chrome = new ChromeOptions()
                .addArguments("--host-resolver-rules=MAP localhost " + hostIp);
        chrome.setCapability("selenoid:options", Map.of("enableVNC", true, "enableLog", true));
        Configuration.browserCapabilities = chrome;
    }

    @Test
    public void adminCanLoginCorrectDateTest() {
        CreateUserRequest admin = CreateUserRequest.builder().username("admin").password("admin").build();
        Selenide.open("/");
        $(Selectors.byAttribute("placeholder", "Username")).setValue(admin.getUsername());
        $(Selectors.byAttribute("placeholder", "Password")).setValue(admin.getPassword());
        $("button").click();
        $(Selectors.byText("Admin Panel")).shouldBe(visible);
    }
    // Diciskd!13& - Diciskddd
    private CreateUserRequest user;
    private Long accountId;
    @Test
    void deposit_increases_balance_and_shows_success_alert() {
        // 1) пользователь (API)
        CreateUserRequest user = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) создание счёта (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();

        // 4) логин → токен в localStorage (для UI)
        String authHeader = new CrudRequester(
                RequestSpecs.unauthSpec(), Endpoint.LOGIN, ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .build())
                .extract()
                .header("Authorization");

        open("/");
        executeJavaScript("localStorage.setItem('authToken', arguments[0])", authHeader);
        open("/dashboard");

        // 5) перейти на Deposit
        $x("//button[contains(., 'Deposit Money')]").shouldBe(visible).click();
        webdriver().shouldHave(urlContaining("/deposit"));

        // 6) выбрать созданный счёт по номеру
        $("select.account-selector").shouldBe(visible)
                .selectOptionContainingText(accountNumber);

        // 7) ввести сумму и нажать Deposit
        int amount = 1000;
        $("input.deposit-input[placeholder='Enter amount'][type='number']")
                .shouldBe(visible)
                .setValue(String.valueOf(amount));
        $x("//button[contains(., 'Deposit')]").shouldBe(visible).click();

        // 8) алерт
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText)
                .contains("Successfully deposited $" + amount)
                .contains("to account " + accountNumber);
        alert.accept();

        // 9) API: баланс счёта стал = amount
        AccountResponse[] accounts = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when()
                .get("customer/accounts")
                .then()
                .statusCode(200)
                .extract()
                .as(AccountResponse[].class);

        AccountResponse updated = Arrays.stream(accounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst().orElseThrow();

        assertThat(updated.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(amount));
    }
}
