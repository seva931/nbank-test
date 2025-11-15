package iteration2test.ui;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import models.AccountResponse;
import models.CreateUserRequest;
import models.LoginUserRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Alert;
import org.openqa.selenium.Keys;
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

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selenide.*;
import static org.assertj.core.api.Assertions.assertThat;

public class MoneyTransferUITest {
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
// Позитивный тест: перевод денег со своего счета на счет другого пользователя
    void transferMoneyToAnotherAccountSuccess() {
        int amount = 1000;
        // 1) пользователи (API)
        CreateUserRequest user = AdminSteps.createUser();
        CreateUserRequest user2 = AdminSteps.createUser();
        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        var userSpec2 = RequestSpecs.authAsUser(user2.getUsername(), user2.getPassword());
        // 3) создание счетов (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();
        AccountResponse account2 = AccountSteps.createAccount(userSpec2, ResponseSpecs.entityWasCreated());
        String accountNumber2 = account2.getAccountNumber();
        // 4) пополнить баланс отправителя (API) на сумму перевода
        Long accountId = account.getId();
        var deposit = models.DepositRequest.builder()
                .id(accountId)
                .balance(java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP))
                .build();
        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                deposit
        );
        // 5) логин — токен в localStorage (для UI)
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
        open("/transfer");
        // 6) выбрать счёт отправителя
        $("select.account-selector").shouldBe(visible, enabled)
                .selectOptionContainingText(accountNumber);
        // 7) ввести номер счёта получателя
        $("input[placeholder='Enter recipient account number']")
                .shouldBe(visible, enabled)
                .setValue(accountNumber2)
                .shouldHave(value(accountNumber2));
        // 8) ввести сумму
        $("input[placeholder='Enter amount']")
                .shouldBe(visible, enabled)
                .setValue(String.valueOf(amount));
        // 9) подтвердить чекбокс
        $("#confirmCheck").shouldBe(visible, enabled).setSelected(true)
                .shouldBe(checked);
        // 10) отправить перевод
        $$("button").findBy(text("Send Transfer"))
                .shouldBe(visible, enabled)
                .click();
        // 11) ОР: появился alert
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText)
                .contains("Successfully transferred $" + amount)
                .contains("to account " + accountNumber2);
        alert.accept();
        // 12) API: баланс отправителя = 0, получателя = amount
        AccountResponse[] senderAccounts = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when()
                .get("customer/accounts")
                .then()
                .statusCode(200)
                .extract()
                .as(AccountResponse[].class);
        AccountResponse updatedSender = Arrays.stream(senderAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst().orElseThrow();
        assertThat(updatedSender.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(0));
        AccountResponse[] recipientAccounts = io.restassured.RestAssured.given()
                .spec(userSpec2)
                .when()
                .get("customer/accounts")
                .then()
                .statusCode(200)
                .extract()
                .as(AccountResponse[].class);
        AccountResponse updatedRecipient = Arrays.stream(recipientAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber2))
                .findFirst().orElseThrow();
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(amount));
    }

    @Test
// Негативный тест: перевод без выбора исходного счёта
    void transferWithoutSourceAccount() {
        int amount = 1000;
        // 1) пользователи (API)
        CreateUserRequest user = AdminSteps.createUser();
        CreateUserRequest user2 = AdminSteps.createUser();
        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        var userSpec2 = RequestSpecs.authAsUser(user2.getUsername(), user2.getPassword());
        // 3) создание счетов (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();
        AccountResponse account2 = AccountSteps.createAccount(userSpec2, ResponseSpecs.entityWasCreated());
        String accountNumber2 = account2.getAccountNumber();
        // 4) пополнить баланс отправителя (API) на сумму перевода
        Long accountId = account.getId();
        var deposit = models.DepositRequest.builder()
                .id(accountId)
                .balance(java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP))
                .build();
        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                deposit
        );
        // 5) логин — токен в localStorage (для UI)
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
        open("/transfer");
        // 6) ввести номер счёта получателя
        $("input[placeholder='Enter recipient account number']")
                .shouldBe(visible, enabled)
                .setValue(accountNumber2)
                .shouldHave(value(accountNumber2));
        // 7) ввести сумму
        $("input[placeholder='Enter amount']")
                .shouldBe(visible, enabled)
                .setValue(String.valueOf(amount));
        // 8) подтвердить чекбокс
        $("#confirmCheck").shouldBe(visible, enabled).setSelected(true)
                .shouldBe(checked);
        // 9) отправить перевод
        $$("button").findBy(text("Send Transfer"))
                .shouldBe(visible, enabled)
                .click();
        // 10) ОР: появился alert
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText)
                .contains("Please fill all fields and confirm");
        alert.accept();
        // 12) API: балансы без изменений
        AccountResponse[] senderAccounts = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when()
                .get("customer/accounts")
                .then()
                .statusCode(200)
                .extract()
                .as(AccountResponse[].class);
        AccountResponse updatedSender = Arrays.stream(senderAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst().orElseThrow();
        assertThat(updatedSender.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(amount));
        AccountResponse[] recipientAccounts = io.restassured.RestAssured.given()
                .spec(userSpec2)
                .when()
                .get("customer/accounts")
                .then()
                .statusCode(200)
                .extract()
                .as(AccountResponse[].class);
        AccountResponse updatedRecipient = Arrays.stream(recipientAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber2))
                .findFirst().orElseThrow();
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(0));
    }

    @Test
// Негативный тест: перевод без выбора счёта получателя
    void transferWithoutRecipientAccount() {
        int amount = 1000;
        // 1) пользователи (API)
        CreateUserRequest user = AdminSteps.createUser();
        CreateUserRequest user2 = AdminSteps.createUser();
        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        var userSpec2 = RequestSpecs.authAsUser(user2.getUsername(), user2.getPassword());
        // 3) создание счетов (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();
        AccountResponse account2 = AccountSteps.createAccount(userSpec2, ResponseSpecs.entityWasCreated());
        String accountNumber2 = account2.getAccountNumber();
        // 4) пополнить баланс отправителя (API) на сумму перевода
        Long accountId = account.getId();
        var deposit = models.DepositRequest.builder()
                .id(accountId)
                .balance(java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP))
                .build();
        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                deposit
        );
        // 5) логин — токен в localStorage (для UI)
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
        open("/transfer");
        // 6) выбрать счёт отправителя
        $("select.account-selector").shouldBe(visible, enabled)
                .selectOptionContainingText(accountNumber);
        // 7) ввести сумму
        $("input[placeholder='Enter amount']")
                .shouldBe(visible, enabled)
                .setValue(String.valueOf(amount));
        // 8) подтвердить чекбокс
        $("#confirmCheck").shouldBe(visible, enabled).setSelected(true)
                .shouldBe(checked);
        // 9) отправить перевод
        $$("button").findBy(text("Send Transfer"))
                .shouldBe(visible, enabled)
                .click();
        // 10) ОР: появился alert
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText)
                .contains("Please fill all fields and confirm");
        alert.accept();
        // 12) API: балансы без изменений
        AccountResponse[] senderAccounts = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when()
                .get("customer/accounts")
                .then()
                .statusCode(200)
                .extract()
                .as(AccountResponse[].class);
        AccountResponse updatedSender = Arrays.stream(senderAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst().orElseThrow();
        assertThat(updatedSender.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(amount));
        AccountResponse[] recipientAccounts = io.restassured.RestAssured.given()
                .spec(userSpec2)
                .when()
                .get("customer/accounts")
                .then()
                .statusCode(200)
                .extract()
                .as(AccountResponse[].class);
        AccountResponse updatedRecipient = Arrays.stream(recipientAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber2))
                .findFirst().orElseThrow();
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(0));
    }

    @Test
// Негативный тест: перевод с пустым полем "Сумма"
    void transferWithEmptyAmount() {
        int amount = 1000;
        // 1) пользователи (API)
        CreateUserRequest user = AdminSteps.createUser();
        CreateUserRequest user2 = AdminSteps.createUser();
        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        var userSpec2 = RequestSpecs.authAsUser(user2.getUsername(), user2.getPassword());
        // 3) создание счетов (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();
        AccountResponse account2 = AccountSteps.createAccount(userSpec2, ResponseSpecs.entityWasCreated());
        String accountNumber2 = account2.getAccountNumber();
        // 4) пополнить баланс отправителя (API) на сумму перевода
        Long accountId = account.getId();
        var deposit = models.DepositRequest.builder()
                .id(accountId)
                .balance(java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP))
                .build();
        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                deposit
        );
        // 5) логин — токен в localStorage (для UI)
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
        open("/transfer");
        // 6) выбрать счёт отправителя
        $("select.account-selector").shouldBe(visible, enabled)
                .selectOptionContainingText(accountNumber);
        // 7) ввести номер счёта получателя
        $("input[placeholder='Enter recipient account number']")
                .shouldBe(visible, enabled)
                .setValue(accountNumber2)
                .shouldHave(value(accountNumber2));
        // 8) подтвердить чекбокс
        $("#confirmCheck").shouldBe(visible, enabled).setSelected(true)
                .shouldBe(checked);
        // 9) отправить перевод
        $$("button").findBy(text("Send Transfer"))
                .shouldBe(visible, enabled)
                .click();
        // 10) ОР: появился alert
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText)
                .contains("Please fill all fields and confirm");
        alert.accept();
        // 11) API: балансы без изменений
        AccountResponse[] senderAccounts = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when()
                .get("customer/accounts")
                .then()
                .statusCode(200)
                .extract()
                .as(AccountResponse[].class);
        AccountResponse updatedSender = Arrays.stream(senderAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst().orElseThrow();
        assertThat(updatedSender.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(amount));
        AccountResponse[] recipientAccounts = io.restassured.RestAssured.given()
                .spec(userSpec2)
                .when()
                .get("customer/accounts")
                .then()
                .statusCode(200)
                .extract()
                .as(AccountResponse[].class);
        AccountResponse updatedRecipient = Arrays.stream(recipientAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber2))
                .findFirst().orElseThrow();
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(0));
    }

    @Test
// Негативный тест: перевод на несуществующий счёт
    void transferToNonexistentAccount() {
        int amount = 1000;
        // 1) пользователи (API)
        CreateUserRequest user = AdminSteps.createUser();
        CreateUserRequest user2 = AdminSteps.createUser();
        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        var userSpec2 = RequestSpecs.authAsUser(user2.getUsername(), user2.getPassword());
        // 3) создание счетов (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();
        AccountResponse account2 = AccountSteps.createAccount(userSpec2, ResponseSpecs.entityWasCreated());
        String accountNumber2 = account2.getAccountNumber();
        // 4) пополнить баланс отправителя (API) на сумму перевода
        Long accountId = account.getId();
        var deposit = models.DepositRequest.builder()
                .id(accountId)
                .balance(java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP))
                .build();
        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                deposit
        );
        // 5) логин — токен в localStorage (для UI)
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
        open("/transfer");
        // 6) выбрать счёт отправителя
        $("select.account-selector").shouldBe(visible, enabled)
                .selectOptionContainingText(accountNumber);
        // 7) ввести сумму
        $("input[placeholder='Enter amount']")
                .shouldBe(visible, enabled)
                .setValue(String.valueOf(amount));
        // 8) ввести номер счёта получателя (несуществующий)
        $("input[placeholder='Enter recipient account number']")
                .shouldBe(visible, enabled)
                .setValue("ACC1000200");
        // 9) подтвердить чекбокс
        $("#confirmCheck").shouldBe(visible, enabled).setSelected(true)
                .shouldBe(checked);
        // 10) отправить перевод
        $$("button").findBy(text("Send Transfer"))
                .shouldBe(visible, enabled)
                .click();
        // 11) ОР: появился alert
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText)
                .contains("No user found with this account number");
        alert.accept();
        // 12) API: балансы без изменений
        AccountResponse[] senderAccounts = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when()
                .get("customer/accounts")
                .then()
                .statusCode(200)
                .extract()
                .as(AccountResponse[].class);
        AccountResponse updatedSender = Arrays.stream(senderAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst().orElseThrow();
        assertThat(updatedSender.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(amount));
        AccountResponse[] recipientAccounts = io.restassured.RestAssured.given()
                .spec(userSpec2)
                .when()
                .get("customer/accounts")
                .then()
                .statusCode(200)
                .extract()
                .as(AccountResponse[].class);
        AccountResponse updatedRecipient = Arrays.stream(recipientAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber2))
                .findFirst().orElseThrow();
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(0));
    }

    @Test
// Негативный тест: перевод без активации чек-бокса подтверждения
    void transferWithoutConfirm() {
        int amount = 1000;
        // 1) пользователи (API)
        CreateUserRequest user = AdminSteps.createUser();
        CreateUserRequest user2 = AdminSteps.createUser();
        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        var userSpec2 = RequestSpecs.authAsUser(user2.getUsername(), user2.getPassword());
        // 3) создание счетов (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();
        AccountResponse account2 = AccountSteps.createAccount(userSpec2, ResponseSpecs.entityWasCreated());
        String accountNumber2 = account2.getAccountNumber();
        // 4) пополнить баланс отправителя (API) на сумму перевода
        Long accountId = account.getId();
        var deposit = models.DepositRequest.builder()
                .id(accountId)
                .balance(java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP))
                .build();
        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                deposit
        );
        // 5) логин — токен в localStorage (для UI)
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
        open("/transfer");
        // 6) выбрать счёт отправителя
        $("select.account-selector").shouldBe(visible, enabled)
                .selectOptionContainingText(accountNumber);
        // 7) ввести сумму
        $("input[placeholder='Enter amount']")
                .shouldBe(visible, enabled)
                .setValue(String.valueOf(amount));
        // 8) ввести номер счёта получателя
        $("input[placeholder='Enter recipient account number']")
                .shouldBe(visible, enabled)
                .setValue(accountNumber2)
                .shouldHave(value(accountNumber2));
        // 9) отправить перевод
        $$("button").findBy(text("Send Transfer"))
                .shouldBe(visible, enabled)
                .click();
        // 10) ОР: появился alert
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText)
                .contains("Please fill all fields and confirm");
        alert.accept();
        // 11) API: балансы без изменений
        AccountResponse[] senderAccounts = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when()
                .get("customer/accounts")
                .then()
                .statusCode(200)
                .extract()
                .as(AccountResponse[].class);
        AccountResponse updatedSender = Arrays.stream(senderAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst().orElseThrow();
        assertThat(updatedSender.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(amount));
        AccountResponse[] recipientAccounts = io.restassured.RestAssured.given()
                .spec(userSpec2)
                .when()
                .get("customer/accounts")
                .then()
                .statusCode(200)
                .extract()
                .as(AccountResponse[].class);
        AccountResponse updatedRecipient = Arrays.stream(recipientAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber2))
                .findFirst().orElseThrow();
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(0));
    }


    @Test
// Позитивный тест: повторный перевод денег одному и тому же пользователю с той же суммой
    void repeatTransferToSameUserSuccess() {
        int amount = 1000;

        // 1) пользователи (API)
        CreateUserRequest user = AdminSteps.createUser();
        CreateUserRequest user2 = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        var userSpec2 = RequestSpecs.authAsUser(user2.getUsername(), user2.getPassword());

        // 3) создание счетов (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();
        AccountResponse account2 = AccountSteps.createAccount(userSpec2, ResponseSpecs.entityWasCreated());
        String accountNumber2 = account2.getAccountNumber();

        // 4) пополнить баланс отправителя (API)
        Long accountId = account.getId();
        var deposit = models.DepositRequest.builder()
                .id(accountId)
                .balance(java.math.BigDecimal.valueOf(amount * 2L).setScale(2, java.math.RoundingMode.HALF_UP))
                .build();
        AccountSteps.deposit(userSpec, ResponseSpecs.requestReturnsOK(), deposit);

        // 5.1 подготовить сумму
        var amountBD = java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP);
        // 5.2 собрать запрос перевода
        var firstTransfer = models.TransferRequest.builder()
                .senderAccountId(account.getId())
                .receiverAccountId(account2.getId())
                .amount(amountBD)
                .build();
        // 5.3 выполнить перевод (ожидаем 200 OK)
        models.TransferResponse firstTransferResp = requests.steps.AccountSteps.transfer(
                userSpec, specs.ResponseSpecs.requestReturnsOK(), firstTransfer
        );

        // 5) логин — токен в localStorage
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
        open("/transfer");

        // 6) переход на вкладку повторный перевод
        $$("button").findBy(text("Transfer Again")).shouldBe(visible, enabled).click();

        // 7) клик "Repeat" по нужной записи
        String txType = "TRANSFER_OUT";
        String amtToken = "$" + amount;
        SelenideElement txItem = $$("li.list-group-item")
                .filterBy(text(txType))
                .filterBy(text(amtToken))
                .first()
                .shouldBe(visible);
        txItem.$("button").shouldHave(text("Repeat")).shouldBe(visible, enabled).click();

        // 7.1 ОР: открылось модальное окно "Repeat Transfer"
        SelenideElement modal = $(".modal.show").shouldBe(visible);
        modal.$(".modal-title").shouldBe(visible).shouldHave(text("Repeat Transfer"));

        // 8) выбрать счёт в модальном окне
        SelenideElement accountSelect = modal.$("select.form-control").shouldBe(visible, enabled);
        String senderId = String.valueOf(account.getId());
        accountSelect.selectOptionByValue(senderId);
        accountSelect.$("option:checked").shouldHave(value(senderId)); // ОР: выбран нужный счёт

        // 9) проверка той же суммы
        modal.$("input.form-control[type='number']")
                .shouldBe(visible, enabled)
                .shouldHave(value(String.valueOf(amount)));

        // 10) активировать чекбокс
        modal.$("#confirmCheck").shouldBe(visible, enabled).setSelected(true).shouldBe(checked);

        // 11) нажать "Send Transfer"
        modal.$("button.btn-success").shouldBe(visible, enabled).shouldHave(text("Send Transfer")).click();

        // 12) ОР: появился alert с корректными ID счетов
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        String fromId = String.valueOf(account.getId());
        assertThat(alertText)
                .contains("Transfer of $" + amount)
                .contains("from Account " + fromId)
                .contains(" to " + fromId);
        alert.accept();

        // 12) API: финальные балансы — у отправителя 0, у получателя amount * 2
        AccountResponse[] senderAccounts = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when().get("customer/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponse[].class);
        AccountResponse updatedSender = Arrays.stream(senderAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst().orElseThrow();
        assertThat(updatedSender.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(0));

        AccountResponse[] recipientAccounts = io.restassured.RestAssured.given()
                .spec(userSpec2)
                .when().get("customer/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponse[].class);
        AccountResponse updatedRecipient = Arrays.stream(recipientAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber2))
                .findFirst().orElseThrow();
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(amount * 2L));
    }

    @Test
// Позитивный тест: повторный перевод денег одному и тому же пользователю с изменением суммы
    void repeatTransferToSameUserChangedAmountSuccess() {
        int amount = 1000;

        // 1) пользователи (API)
        CreateUserRequest user = AdminSteps.createUser();
        CreateUserRequest user2 = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        var userSpec2 = RequestSpecs.authAsUser(user2.getUsername(), user2.getPassword());

        // 3) создание счетов (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();
        AccountResponse account2 = AccountSteps.createAccount(userSpec2, ResponseSpecs.entityWasCreated());
        String accountNumber2 = account2.getAccountNumber();

        // 4) пополнить баланс отправителя (API)
        Long accountId = account.getId();
        var deposit = models.DepositRequest.builder()
                .id(accountId)
                .balance(java.math.BigDecimal.valueOf(amount * 2L).setScale(2, java.math.RoundingMode.HALF_UP))
                .build();
        AccountSteps.deposit(userSpec, ResponseSpecs.requestReturnsOK(), deposit);

        // 5.1 подготовить сумму
        var amountBD = java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP);
        // 5.2 собрать запрос перевода
        var firstTransfer = models.TransferRequest.builder()
                .senderAccountId(account.getId())
                .receiverAccountId(account2.getId())
                .amount(amountBD)
                .build();
        // 5.3 выполнить перевод (ожидаем 200 OK)
        models.TransferResponse firstTransferResp = requests.steps.AccountSteps.transfer(
                userSpec, specs.ResponseSpecs.requestReturnsOK(), firstTransfer
        );

        // 5) логин — токен в localStorage
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
        open("/transfer");

        // 6) переход на вкладку повторный перевод
        $$("button").findBy(text("Transfer Again")).shouldBe(visible, enabled).click();

        // 7) клик "Repeat" по нужной записи
        String txType = "TRANSFER_OUT";
        String amtToken = "$" + amount;
        SelenideElement txItem = $$("li.list-group-item")
                .filterBy(text(txType))
                .filterBy(text(amtToken))
                .first()
                .shouldBe(visible);
        txItem.$("button").shouldHave(text("Repeat")).shouldBe(visible, enabled).click();

        // 7.1 ОР: открылось модальное окно "Repeat Transfer"
        SelenideElement modal = $(".modal.show").shouldBe(visible);
        modal.$(".modal-title").shouldBe(visible).shouldHave(text("Repeat Transfer"));

        // 8) выбрать счёт в модальном окне
        SelenideElement accountSelect = modal.$("select.form-control").shouldBe(visible, enabled);
        String senderId = String.valueOf(account.getId());
        accountSelect.selectOptionByValue(senderId);
        accountSelect.$("option:checked").shouldHave(value(senderId)); // ОР: выбран нужный счёт

        // 9) изменить сумму на меньшую, чем текущий баланс отправителя
        int newAmount = amount / 10;
        SelenideElement amountInput = modal.$("input.form-control[type='number']").shouldBe(visible, enabled);
        amountInput.clear();
        amountInput.setValue(String.valueOf(newAmount))
                .shouldHave(value(String.valueOf(newAmount)));

        // 10) активировать чекбокс
        modal.$("#confirmCheck").shouldBe(visible, enabled).setSelected(true).shouldBe(checked);

        // 11) нажать "Send Transfer"
        modal.$("button.btn-success").shouldBe(visible, enabled).shouldHave(text("Send Transfer")).click();

        // 12) ОР: появился alert
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        String fromId = String.valueOf(account.getId());
        assertThat(alertText)
                .contains("Transfer of $" + newAmount)
                .contains("from Account " + fromId)
                .contains(" to " + fromId);
        alert.accept();

        // 12) API: финальные балансы — у отправителя amount - newAmount, у получателя amount + newAmount
        AccountResponse[] senderAccounts = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when().get("customer/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponse[].class);
        AccountResponse updatedSender = Arrays.stream(senderAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst().orElseThrow();
        BigDecimal expectedSender = BigDecimal.valueOf(amount - newAmount);
        assertThat(updatedSender.getBalance()).isEqualByComparingTo(expectedSender);

        AccountResponse[] recipientAccounts = io.restassured.RestAssured.given()
                .spec(userSpec2)
                .when().get("customer/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponse[].class);
        AccountResponse updatedRecipient = Arrays.stream(recipientAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber2))
                .findFirst().orElseThrow();
        BigDecimal expectedRecipient = BigDecimal.valueOf(amount + newAmount);
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(expectedRecipient);

    }

    @Test
// Позитивный тест: отмена повторного перевода
    void cancelRepeatTransfer() {
        int amount = 1000;

        // 1) пользователи (API)
        CreateUserRequest user = AdminSteps.createUser();
        CreateUserRequest user2 = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        var userSpec2 = RequestSpecs.authAsUser(user2.getUsername(), user2.getPassword());

        // 3) создание счетов (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();
        AccountResponse account2 = AccountSteps.createAccount(userSpec2, ResponseSpecs.entityWasCreated());
        String accountNumber2 = account2.getAccountNumber();

        // 4) пополнить баланс отправителя (API)
        Long accountId = account.getId();
        var deposit = models.DepositRequest.builder()
                .id(accountId)
                .balance(java.math.BigDecimal.valueOf(amount * 2L).setScale(2, java.math.RoundingMode.HALF_UP))
                .build();
        AccountSteps.deposit(userSpec, ResponseSpecs.requestReturnsOK(), deposit);

        // 5.1 подготовить сумму
        var amountBD = java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP);
        // 5.2 собрать запрос перевода
        var firstTransfer = models.TransferRequest.builder()
                .senderAccountId(account.getId())
                .receiverAccountId(account2.getId())
                .amount(amountBD)
                .build();
        // 5.3 выполнить перевод (ожидаем 200 OK)
        models.TransferResponse firstTransferResp = requests.steps.AccountSteps.transfer(
                userSpec, specs.ResponseSpecs.requestReturnsOK(), firstTransfer
        );

        // 5) логин — токен в localStorage
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
        open("/transfer");

        // 6) переход на вкладку повторный перевод
        $$("button").findBy(text("Transfer Again")).shouldBe(visible, enabled).click();

        // 7) клик "Repeat" по нужной записи
        String txType = "TRANSFER_OUT";
        String amtToken = "$" + amount;
        SelenideElement txItem = $$("li.list-group-item")
                .filterBy(text(txType))
                .filterBy(text(amtToken))
                .first()
                .shouldBe(visible);
        txItem.$("button").shouldHave(text("Repeat")).shouldBe(visible, enabled).click();

        // 7.1 ОР: открылось модальное окно "Repeat Transfer"
        SelenideElement modal = $(".modal.show").shouldBe(visible);
        modal.$(".modal-title").shouldBe(visible).shouldHave(text("Repeat Transfer"));

        // 8) выбрать счёт в модальном окне
        SelenideElement accountSelect = modal.$("select.form-control").shouldBe(visible, enabled);
        String senderId = String.valueOf(account.getId());
        accountSelect.selectOptionByValue(senderId);
        accountSelect.$("option:checked").shouldHave(value(senderId));


        // 9) активировать чекбокс
        modal.$("#confirmCheck").shouldBe(visible, enabled).setSelected(true).shouldBe(checked);

        // 10) закрыть окно повторного перевода
        modal.$("button.btn-secondary").shouldBe(visible, enabled).shouldHave(text("Cancel")).click();

        // 11) ОР: закрытие модульного окна
        modal.should(disappear);

        // 12) API: финальные балансы
        AccountResponse[] senderAccounts = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when().get("customer/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponse[].class);
        AccountResponse updatedSender = Arrays.stream(senderAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst().orElseThrow();
        BigDecimal expectedSender = BigDecimal.valueOf(amount);
        assertThat(updatedSender.getBalance()).isEqualByComparingTo(expectedSender);

        AccountResponse[] recipientAccounts = io.restassured.RestAssured.given()
                .spec(userSpec2)
                .when().get("customer/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponse[].class);
        AccountResponse updatedRecipient = Arrays.stream(recipientAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber2))
                .findFirst().orElseThrow();
        BigDecimal expectedRecipient = BigDecimal.valueOf(amount);
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(expectedRecipient);

    }

    @Test
// Негативный тест: повторный перевод без выбора исходного счёта
    void repeatTransferWithoutSourceAccount() {
        int amount = 1000;

        // 1) пользователи (API)
        CreateUserRequest user = AdminSteps.createUser();
        CreateUserRequest user2 = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        var userSpec2 = RequestSpecs.authAsUser(user2.getUsername(), user2.getPassword());

        // 3) создание счетов (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();
        AccountResponse account2 = AccountSteps.createAccount(userSpec2, ResponseSpecs.entityWasCreated());
        String accountNumber2 = account2.getAccountNumber();

        // 4) пополнить баланс отправителя (API)
        Long accountId = account.getId();
        var deposit = models.DepositRequest.builder()
                .id(accountId)
                .balance(java.math.BigDecimal.valueOf(amount * 2L).setScale(2, java.math.RoundingMode.HALF_UP))
                .build();
        AccountSteps.deposit(userSpec, ResponseSpecs.requestReturnsOK(), deposit);

        // 5.1 подготовить сумму
        var amountBD = java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP);
        // 5.2 собрать запрос перевода
        var firstTransfer = models.TransferRequest.builder()
                .senderAccountId(account.getId())
                .receiverAccountId(account2.getId())
                .amount(amountBD)
                .build();
        // 5.3 выполнить перевод (ожидаем 200 OK)
        models.TransferResponse firstTransferResp = requests.steps.AccountSteps.transfer(
                userSpec, specs.ResponseSpecs.requestReturnsOK(), firstTransfer
        );

        // 5) логин — токен в localStorage
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
        open("/transfer");

        // 6) переход на вкладку повторный перевод
        $$("button").findBy(text("Transfer Again")).shouldBe(visible, enabled).click();

        // 7) клик "Repeat" по нужной записи
        String txType = "TRANSFER_OUT";
        String amtToken = "$" + amount;
        SelenideElement txItem = $$("li.list-group-item")
                .filterBy(text(txType))
                .filterBy(text(amtToken))
                .first()
                .shouldBe(visible);
        txItem.$("button").shouldHave(text("Repeat")).shouldBe(visible, enabled).click();

        // 7.1 ОР: открылось модальное окно "Repeat Transfer"
        SelenideElement modal = $(".modal.show").shouldBe(visible);
        modal.$(".modal-title").shouldBe(visible).shouldHave(text("Repeat Transfer"));


        // 8) активировать чекбокс
        modal.$("#confirmCheck").shouldBe(visible, enabled).setSelected(true).shouldBe(checked);

        // 9) ОР кнопка Send Transfer не кликтабельная
        SelenideElement sendBtn = modal.$("button.btn-success")
                .shouldBe(visible)
                .shouldHave(text("Send Transfer"));
        sendBtn.shouldBe(disabled);

        // 10) закрыть окно повторного перевода
        modal.$("button.btn-secondary").shouldBe(visible, enabled).shouldHave(text("Cancel")).click();


        // 11) API: финальные балансы
        AccountResponse[] senderAccounts = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when().get("customer/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponse[].class);
        AccountResponse updatedSender = Arrays.stream(senderAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst().orElseThrow();
        BigDecimal expectedSender = BigDecimal.valueOf(amount);
        assertThat(updatedSender.getBalance()).isEqualByComparingTo(expectedSender);

        AccountResponse[] recipientAccounts = io.restassured.RestAssured.given()
                .spec(userSpec2)
                .when().get("customer/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponse[].class);
        AccountResponse updatedRecipient = Arrays.stream(recipientAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber2))
                .findFirst().orElseThrow();
        BigDecimal expectedRecipient = BigDecimal.valueOf(amount);
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(expectedRecipient);

    }

    @Test
// Негативный тест: повторный перевод с пустым полем "Сумма"
    void repeatTransferWithEmptyAmount() {
        int amount = 1000;

        // 1) пользователи (API)
        CreateUserRequest user = AdminSteps.createUser();
        CreateUserRequest user2 = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        var userSpec2 = RequestSpecs.authAsUser(user2.getUsername(), user2.getPassword());

        // 3) создание счетов (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();
        AccountResponse account2 = AccountSteps.createAccount(userSpec2, ResponseSpecs.entityWasCreated());
        String accountNumber2 = account2.getAccountNumber();

        // 4) пополнить баланс отправителя (API)
        Long accountId = account.getId();
        var deposit = models.DepositRequest.builder()
                .id(accountId)
                .balance(java.math.BigDecimal.valueOf(amount * 2L).setScale(2, java.math.RoundingMode.HALF_UP))
                .build();
        AccountSteps.deposit(userSpec, ResponseSpecs.requestReturnsOK(), deposit);

        // 5.1 подготовить сумму
        var amountBD = java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP);
        // 5.2 собрать запрос перевода
        var firstTransfer = models.TransferRequest.builder()
                .senderAccountId(account.getId())
                .receiverAccountId(account2.getId())
                .amount(amountBD)
                .build();
        // 5.3 выполнить перевод (ожидаем 200 OK)
        models.TransferResponse firstTransferResp = requests.steps.AccountSteps.transfer(
                userSpec, specs.ResponseSpecs.requestReturnsOK(), firstTransfer
        );

        // 5) логин — токен в localStorage
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
        open("/transfer");

        // 6) переход на вкладку повторный перевод
        $$("button").findBy(text("Transfer Again")).shouldBe(visible, enabled).click();

        // 7) клик "Repeat" по нужной записи
        String txType = "TRANSFER_OUT";
        String amtToken = "$" + amount;
        SelenideElement txItem = $$("li.list-group-item")
                .filterBy(text(txType))
                .filterBy(text(amtToken))
                .first()
                .shouldBe(visible);
        txItem.$("button").shouldHave(text("Repeat")).shouldBe(visible, enabled).click();

        // 7.1 ОР: открылось модальное окно "Repeat Transfer"
        SelenideElement modal = $(".modal.show").shouldBe(visible);
        modal.$(".modal-title").shouldBe(visible).shouldHave(text("Repeat Transfer"));

        // 8) выбрать счёт в модальном окне
        SelenideElement accountSelect = modal.$("select.form-control").shouldBe(visible, enabled);
        String senderId = String.valueOf(account.getId());
        accountSelect.selectOptionByValue(senderId);
        accountSelect.$("option:checked").shouldHave(value(senderId));

        // 9) очистить поле сумма
        SelenideElement amountInput = modal.$("input.form-control[type='number']").shouldBe(visible, enabled);
        amountInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        amountInput.sendKeys(Keys.DELETE);
        amountInput.shouldHave(value(""));


        // 10) активировать чекбокс
        modal.$("#confirmCheck").shouldBe(visible, enabled).setSelected(true).shouldBe(checked);

        // 11) нажать "Send Transfer"
        modal.$("button.btn-success").shouldBe(visible, enabled).shouldHave(text("Send Transfer")).click();

        // 12) ОР: появился alert
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText)
                .contains("Transfer failed: Please try again");
        alert.accept();

        // 13) API: финальные балансы
        AccountResponse[] senderAccounts = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when().get("customer/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponse[].class);
        AccountResponse updatedSender = Arrays.stream(senderAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst().orElseThrow();
        BigDecimal expectedSender = BigDecimal.valueOf(amount);
        assertThat(updatedSender.getBalance()).isEqualByComparingTo(expectedSender);

        AccountResponse[] recipientAccounts = io.restassured.RestAssured.given()
                .spec(userSpec2)
                .when().get("customer/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponse[].class);
        AccountResponse updatedRecipient = Arrays.stream(recipientAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber2))
                .findFirst().orElseThrow();
        BigDecimal expectedRecipient = BigDecimal.valueOf(amount);
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(expectedRecipient);

    }

    @Test
// Негативный тест: повторный перевод денег без активирована чек-бокса "Подтвердите правильность данных"
    void repeatTransferWithoutConfirm() {
        int amount = 1000;

        // 1) пользователи (API)
        CreateUserRequest user = AdminSteps.createUser();
        CreateUserRequest user2 = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        var userSpec2 = RequestSpecs.authAsUser(user2.getUsername(), user2.getPassword());

        // 3) создание счетов (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();
        AccountResponse account2 = AccountSteps.createAccount(userSpec2, ResponseSpecs.entityWasCreated());
        String accountNumber2 = account2.getAccountNumber();

        // 4) пополнить баланс отправителя (API)
        Long accountId = account.getId();
        var deposit = models.DepositRequest.builder()
                .id(accountId)
                .balance(java.math.BigDecimal.valueOf(amount * 2L).setScale(2, java.math.RoundingMode.HALF_UP))
                .build();
        AccountSteps.deposit(userSpec, ResponseSpecs.requestReturnsOK(), deposit);

        // 5.1 подготовить сумму
        var amountBD = java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP);
        // 5.2 собрать запрос перевода
        var firstTransfer = models.TransferRequest.builder()
                .senderAccountId(account.getId())
                .receiverAccountId(account2.getId())
                .amount(amountBD)
                .build();
        // 5.3 выполнить перевод (ожидаем 200 OK)
        models.TransferResponse firstTransferResp = requests.steps.AccountSteps.transfer(
                userSpec, specs.ResponseSpecs.requestReturnsOK(), firstTransfer
        );

        // 5) логин — токен в localStorage
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
        open("/transfer");

        // 6) переход на вкладку повторный перевод
        $$("button").findBy(text("Transfer Again")).shouldBe(visible, enabled).click();

        // 7) клик "Repeat" по нужной записи
        String txType = "TRANSFER_OUT";
        String amtToken = "$" + amount;
        SelenideElement txItem = $$("li.list-group-item")
                .filterBy(text(txType))
                .filterBy(text(amtToken))
                .first()
                .shouldBe(visible);
        txItem.$("button").shouldHave(text("Repeat")).shouldBe(visible, enabled).click();

        // 7.1 ОР: открылось модальное окно "Repeat Transfer"
        SelenideElement modal = $(".modal.show").shouldBe(visible);
        modal.$(".modal-title").shouldBe(visible).shouldHave(text("Repeat Transfer"));

        // 8) выбрать счёт в модальном окне
        SelenideElement accountSelect = modal.$("select.form-control").shouldBe(visible, enabled);
        String senderId = String.valueOf(account.getId());
        accountSelect.selectOptionByValue(senderId);
        accountSelect.$("option:checked").shouldHave(value(senderId));

        // 9) ОР кнопка Send Transfer не кликтабельная
        SelenideElement sendBtn = modal.$("button.btn-success")
                .shouldBe(visible)
                .shouldHave(text("Send Transfer"));
        sendBtn.shouldBe(disabled);

        // 10) закрыть окно повторного перевода
        modal.$("button.btn-secondary").shouldBe(visible, enabled).shouldHave(text("Cancel")).click();

        // 11) API: финальные балансы
        AccountResponse[] senderAccounts = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when().get("customer/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponse[].class);
        AccountResponse updatedSender = Arrays.stream(senderAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst().orElseThrow();
        BigDecimal expectedSender = BigDecimal.valueOf(amount);
        assertThat(updatedSender.getBalance()).isEqualByComparingTo(expectedSender);

        AccountResponse[] recipientAccounts = io.restassured.RestAssured.given()
                .spec(userSpec2)
                .when().get("customer/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponse[].class);
        AccountResponse updatedRecipient = Arrays.stream(recipientAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber2))
                .findFirst().orElseThrow();
        BigDecimal expectedRecipient = BigDecimal.valueOf(amount);
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(expectedRecipient);

    }

    @Test
// Позитивный тест: повторный перевод суммы больше баланса
    void repeatTransferOverBalance() {
        int amount = 1000;

        // 1) пользователи (API)
        CreateUserRequest user = AdminSteps.createUser();
        CreateUserRequest user2 = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        var userSpec2 = RequestSpecs.authAsUser(user2.getUsername(), user2.getPassword());

        // 3) создание счетов (API)
        AccountResponse account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        String accountNumber = account.getAccountNumber();
        AccountResponse account2 = AccountSteps.createAccount(userSpec2, ResponseSpecs.entityWasCreated());
        String accountNumber2 = account2.getAccountNumber();

        // 4) пополнить баланс отправителя (API)
        Long accountId = account.getId();
        var deposit = models.DepositRequest.builder()
                .id(accountId)
                .balance(java.math.BigDecimal.valueOf(amount * 2L).setScale(2, java.math.RoundingMode.HALF_UP))
                .build();
        AccountSteps.deposit(userSpec, ResponseSpecs.requestReturnsOK(), deposit);

        // 5.1 подготовить сумму
        var amountBD = java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP);
        // 5.2 собрать запрос перевода
        var firstTransfer = models.TransferRequest.builder()
                .senderAccountId(account.getId())
                .receiverAccountId(account2.getId())
                .amount(amountBD)
                .build();
        // 5.3 выполнить перевод (ожидаем 200 OK)
        models.TransferResponse firstTransferResp = requests.steps.AccountSteps.transfer(
                userSpec, specs.ResponseSpecs.requestReturnsOK(), firstTransfer
        );

        // 5) логин — токен в localStorage
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
        open("/transfer");

        // 6) переход на вкладку повторный перевод
        $$("button").findBy(text("Transfer Again")).shouldBe(visible, enabled).click();

        // 7) клик "Repeat" по нужной записи
        String txType = "TRANSFER_OUT";
        String amtToken = "$" + amount;
        SelenideElement txItem = $$("li.list-group-item")
                .filterBy(text(txType))
                .filterBy(text(amtToken))
                .first()
                .shouldBe(visible);
        txItem.$("button").shouldHave(text("Repeat")).shouldBe(visible, enabled).click();

        // 7.1 ОР: открылось модальное окно "Repeat Transfer"
        SelenideElement modal = $(".modal.show").shouldBe(visible);
        modal.$(".modal-title").shouldBe(visible).shouldHave(text("Repeat Transfer"));

        // 8) выбрать счёт в модальном окне
        SelenideElement accountSelect = modal.$("select.form-control").shouldBe(visible, enabled);
        String senderId = String.valueOf(account.getId());
        accountSelect.selectOptionByValue(senderId);
        accountSelect.$("option:checked").shouldHave(value(senderId)); // ОР: выбран нужный счёт

        // 9) изменить сумму на больше, чем баланс
        int newAmount = amount + 10;
        SelenideElement amountInput = modal.$("input.form-control[type='number']").shouldBe(visible, enabled);
        amountInput.clear();
        amountInput.setValue(String.valueOf(newAmount))
                .shouldHave(value(String.valueOf(newAmount)));

        // 10) активировать чекбокс
        modal.$("#confirmCheck").shouldBe(visible, enabled).setSelected(true).shouldBe(checked);

        // 11) нажать "Send Transfer"
        modal.$("button.btn-success").shouldBe(visible, enabled).shouldHave(text("Send Transfer")).click();

        // 12) ОР: появился alert
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText)
                .contains("Transfer failed: Please try again");
        alert.accept();

        // 13) API: финальные балансы
        AccountResponse[] senderAccounts = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when().get("customer/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponse[].class);
        AccountResponse updatedSender = Arrays.stream(senderAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst().orElseThrow();
        BigDecimal expectedSender = BigDecimal.valueOf(amount);
        assertThat(updatedSender.getBalance()).isEqualByComparingTo(expectedSender);

        AccountResponse[] recipientAccounts = io.restassured.RestAssured.given()
                .spec(userSpec2)
                .when().get("customer/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponse[].class);
        AccountResponse updatedRecipient = Arrays.stream(recipientAccounts)
                .filter(a -> a.getAccountNumber().equals(accountNumber2))
                .findFirst().orElseThrow();
        BigDecimal expectedRecipient = BigDecimal.valueOf(amount);
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(expectedRecipient);

    }

}