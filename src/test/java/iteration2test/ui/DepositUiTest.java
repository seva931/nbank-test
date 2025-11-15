package iteration2test.ui;

import com.codeborne.selenide.Configuration;
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

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.*;
import static org.assertj.core.api.Assertions.assertThat;

public class DepositUiTest {
    @BeforeAll
    static void setupSelenoid() {
        Configuration.remote = "http://localhost:4444/wd/hub";
        Configuration.baseUrl = "http://localhost:3000";          // Origin = localhost
        Configuration.browser = "chrome";
        Configuration.browserSize = "1920x1080";

        String hostIp = "192.168.1.65";                            // мой IPv4 Wi-Fi
        ChromeOptions chrome = new ChromeOptions()
                .addArguments("--host-resolver-rules=MAP localhost " + hostIp);
        chrome.setCapability("selenoid:options", Map.of("enableVNC", true, "enableLog", true));
        Configuration.browserCapabilities = chrome;
    }

    private CreateUserRequest user;
    private Long accountId;

    @Test
// Позитивный тест: пополнение счёта валидной суммой
    void depositWithValidAmount() {
        int amount = 1000;

        // 1) пользователь (API)
        CreateUserRequest user = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) создание счёта (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();

        // 4) логин — токен в localStorage (для UI)
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
        open("/deposit");

        // 5) выбрать созданный счёт по номеру
        $("select.account-selector").shouldBe(visible)
                .selectOptionContainingText(accountNumber);

        // 6) ввести сумму и нажать Deposit
        $("input.deposit-input[placeholder='Enter amount'][type='number']")
                .shouldBe(visible)
                .setValue(String.valueOf(amount));
        $x("//button[contains(., 'Deposit')]").shouldBe(visible).click();

        // 7) ОР: сообщение об успехе
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText)
                .contains("Successfully deposited $" + amount)
                .contains("to account " + accountNumber);
        alert.accept();

        // 8) API: баланс счёта стал равен amount
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

    @Test
// Негативный тест: пополнение без выбора счёта
    void depositWithoutAccount() {
        int amount = 1000;

        // 1) пользователь (API)
        CreateUserRequest user = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) создание счёта (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();

        // 4) логин — токен в localStorage (для UI)
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
        open("/deposit");

        // 5) ввести сумму и нажать Deposit
        $("input.deposit-input[placeholder='Enter amount'][type='number']")
                .shouldBe(visible)
                .setValue(String.valueOf(amount));
        $x("//button[contains(., 'Deposit')]").shouldBe(visible).click();

        // 6) ОР: сообщение об ошибке
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText).contains("Please select an account");
        alert.accept();

        // 7) API: баланс счёта не изменился
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

        assertThat(updated.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(0));
    }

    @Test
// Негативный тест: сумма больше допустимого значения
    void depositAmountOverLimit() {
        int amount = 5001;

        // 1) пользователь (API)
        CreateUserRequest user = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) создание счёта (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();

        // 4) логин — токен в localStorage (для UI)
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
        open("/deposit");

        // 5) выбрать созданный счёт по номеру
        $("select.account-selector").shouldBe(visible)
                .selectOptionContainingText(accountNumber);

        // 6) ввести сумму и нажать Deposit
        $("input.deposit-input[placeholder='Enter amount'][type='number']")
                .shouldBe(visible)
                .setValue(String.valueOf(amount));
        $x("//button[contains(., 'Deposit')]").shouldBe(visible).click();

        // 7) ОР: сообщение об ошибке
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText).contains("Please deposit less or equal to 5000");
        alert.accept();

        // 8) API: баланс счёта не изменился
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

        assertThat(updated.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(0));
    }

    @Test
// Негативный тест: сумма равна 0
    void depositWithZeroAmount() {
        int amount = 0;

        // 1) пользователь (API)
        CreateUserRequest user = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) создание счёта (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();

        // 4) логин — токен в localStorage (для UI)
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
        open("/deposit");

        // 5) выбрать созданный счёт по номеру
        $("select.account-selector").shouldBe(visible)
                .selectOptionContainingText(accountNumber);

        // 6) ввести сумму и нажать Deposit
        $("input.deposit-input[placeholder='Enter amount'][type='number']")
                .shouldBe(visible)
                .setValue(String.valueOf(amount));
        $x("//button[contains(., 'Deposit')]").shouldBe(visible).click();

        // 7) ОР: сообщение об ошибке
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText).contains("Please enter a valid amount");
        alert.accept();

        // 8) API: баланс счёта не изменился
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

        assertThat(updated.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(0));
    }

    @Test
// Негативный тест: пустое поле «сумма»
    void depositWithEmptyAmount() {
        // 1) пользователь (API)
        CreateUserRequest user = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) создание счёта (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();

        // 4) логин — токен в localStorage (для UI)
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
        open("/deposit");

        // 5) выбрать созданный счёт по номеру
        $("select.account-selector").shouldBe(visible)
                .selectOptionContainingText(accountNumber);

        // 6) нажать Deposit
        $x("//button[contains(., 'Deposit')]").shouldBe(visible).click();

        // 7) ОР: сообщение об ошибке
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText).contains("Please enter a valid amount");
        alert.accept();

        // 8) API: баланс счёта не изменился
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

        assertThat(updated.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(0));
    }

    @Test
// Негативный тест: отрицательная сумма
    void depositWithNegativeAmount() {
        int amount = -101;

        // 1) пользователь (API)
        CreateUserRequest user = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) создание счёта (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();

        // 4) логин — токен в localStorage (для UI)
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
        open("/deposit");

        // 5) выбрать созданный счёт по номеру
        $("select.account-selector").shouldBe(visible)
                .selectOptionContainingText(accountNumber);

        // 6) ввести сумму и нажать Deposit
        $("input.deposit-input[placeholder='Enter amount'][type='number']")
                .shouldBe(visible)
                .setValue(String.valueOf(amount));
        $x("//button[contains(., 'Deposit')]").shouldBe(visible).click();

        // 7) ОР: сообщение об ошибке
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText).contains("Please enter a valid amount");
        alert.accept();

        // 8) API: баланс счёта не изменился
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

        assertThat(updated.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(0));
    }
}
