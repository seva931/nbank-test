package iteration2test.ui;

import com.codeborne.selenide.Configuration;
import models.CreateUserRequest;
import models.LoginUserRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Alert;
import org.openqa.selenium.chrome.ChromeOptions;
import requests.skelethon.Endpoint;
import requests.skelethon.requester.CrudRequester;
import requests.steps.AdminSteps;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.util.Map;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.WebDriverConditions.urlContaining;
import static org.assertj.core.api.Assertions.assertThat;

public class UserReanameUITest {
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
// Позитивный тест: первичная установка имени
    void setNameFirstTime() {
        String name = "Keys Jons";
        // 1) пользователь (API)
        CreateUserRequest user = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) логин - токен в localStorage (для UI)
        String authHeader = new CrudRequester(
                RequestSpecs.unauthSpec(), Endpoint.LOGIN, ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .build())
                .extract()
                .header("Authorization");

        // 4) перейти на изменение профиля
        open("/");
        executeJavaScript("localStorage.setItem('authToken', arguments[0])", authHeader);
        open("/edit-profile");

        // 5) выбрать поле новое имя и ввести имя
        $("input[placeholder='Enter new name']").shouldBe(visible, enabled).setValue(name);

        // 6) нажать на save changes
        $$("button").findBy(text("Save Changes")).shouldBe(visible, enabled).click();

        // 7) ОР: сообщение об успехе
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText).contains("Name updated successfully");
        alert.accept();

        // 8) проверка имени в API
        String actualName = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when()
                .get("customer/profile")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("name");

        assertThat(actualName).isEqualTo(name);
    }

    @Test
// Позитивный тест: повторное изменение имени на другое
    void updateNameSecondTime() {
        String firstName = "Keys Jons";
        String secondName = "Jonses Volk";

        // 1) пользователь (API)
        CreateUserRequest user = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) логин - токен в localStorage (для UI)
        String authHeader = new CrudRequester(
                RequestSpecs.unauthSpec(), Endpoint.LOGIN, ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .build())
                .extract()
                .header("Authorization");

        // 4) перейти на изменение профиля
        open("/");
        executeJavaScript("localStorage.setItem('authToken', arguments[0])", authHeader);
        open("/edit-profile");

        // 5) ввести первое имя
        $("input[placeholder='Enter new name']").shouldBe(visible, enabled).setValue(firstName);

        // 6) сохранить
        $$("button").findBy(text("Save Changes")).shouldBe(visible, enabled).click();

        // 7) ОР: сообщение об успехе
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText).contains("Name updated successfully");
        alert.accept();

        // 8) вернуться в меню
        $$("button").findBy(text("Home")).shouldBe(visible, enabled).click();

        //9) снова открыть редактирование профиля
        $("header .profile-header").shouldBe(visible, enabled).click();
        webdriver().shouldHave(urlContaining("/edit-profile"));

        //10) ввести второе имя
        $("input[placeholder='Enter new name']").shouldBe(visible, enabled).setValue(secondName);

        //11) сохранить
        $$("button").findBy(text("Save Changes")).shouldBe(visible, enabled).click();

        //12) ОР: сообщение об успехе
        Alert alert2 = switchTo().alert();
        String alertText2 = alert2.getText();
        assertThat(alertText2).contains("Name updated successfully");
        alert2.accept();

        // 13) проверка имени в API
        String actualName = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when()
                .get("customer/profile")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("name");

        assertThat(actualName).isEqualTo(secondName);
    }

    @Test
// Негативный тест: одно слово в имени
    void setNameSingleWord() {
        String name = "Jjdjslsd";
        // 1) пользователь (API)
        CreateUserRequest user = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) логин - токен в localStorage (для UI)
        String authHeader = new CrudRequester(
                RequestSpecs.unauthSpec(), Endpoint.LOGIN, ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .build())
                .extract()
                .header("Authorization");

        // 4) перейти на изменение профиля
        open("/");
        executeJavaScript("localStorage.setItem('authToken', arguments[0])", authHeader);
        open("/edit-profile");

        // 5) ввести имя
        $("input[placeholder='Enter new name']").shouldBe(visible, enabled).setValue(name);

        // 6) сохранить
        $$("button").findBy(text("Save Changes")).shouldBe(visible, enabled).click();

        // 7) ОР: сообщение об ошибке
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText).contains("Name must contain two words with letters only");
        alert.accept();

        // 8) проверка имени в API
        String actualName = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when()
                .get("customer/profile")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("name");

        assertThat(actualName).isEqualTo(null);
    }

    @Test
// Негативный тест: пустая строка
    void setNameEmpty() {
        // 1) пользователь (API)
        CreateUserRequest user = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) логин - токен в localStorage (для UI)
        String authHeader = new CrudRequester(
                RequestSpecs.unauthSpec(), Endpoint.LOGIN, ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .build())
                .extract()
                .header("Authorization");

        // 4) перейти на изменение профиля
        open("/");
        executeJavaScript("localStorage.setItem('authToken', arguments[0])", authHeader);
        open("/edit-profile");

        // 5) сохранить
        $$("button").findBy(text("Save Changes")).shouldBe(visible, enabled).click();

        // 6) ОР: сообщение об ошибке
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText).contains("Please enter a valid name");
        alert.accept();

        // 7) проверка имени в API
        String actualName = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when()
                .get("customer/profile")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("name");

        assertThat(actualName).isEqualTo(null);
    }

    @Test
// Негативный тест: только пробелы
    void setNameSpacesOnly() {
        String name = "     ";
        // 1) пользователь (API)
        CreateUserRequest user = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) логин - токен в localStorage (для UI)
        String authHeader = new CrudRequester(
                RequestSpecs.unauthSpec(), Endpoint.LOGIN, ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .build())
                .extract()
                .header("Authorization");

        // 4) перейти на изменение профиля
        open("/");
        executeJavaScript("localStorage.setItem('authToken', arguments[0])", authHeader);
        open("/edit-profile");

        // 5) ввести имя
        $("input[placeholder='Enter new name']").shouldBe(visible, enabled).setValue(name);

        // 6) сохранить
        $$("button").findBy(text("Save Changes")).shouldBe(visible, enabled).click();

        // 7) ОР: сообщение об ошибке
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText).contains("Please enter a valid name");
        alert.accept();

        // 8) проверка имени в API
        String actualName = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when()
                .get("customer/profile")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("name");

        assertThat(actualName).isEqualTo(null);
    }

    @Test
// Негативный тест: новое имя совпадает с текущим
    void setNameSameAsCurrent() {
        String firstName = "Keys Jons";
        String secondName = "Keys Jons";

        // 1) пользователь (API)
        CreateUserRequest user = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) логин - токен в localStorage (для UI)
        String authHeader = new CrudRequester(
                RequestSpecs.unauthSpec(), Endpoint.LOGIN, ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .build())
                .extract()
                .header("Authorization");

        // 4) перейти на изменение профиля
        open("/");
        executeJavaScript("localStorage.setItem('authToken', arguments[0])", authHeader);
        open("/edit-profile");

        // 5) ввести первое имя
        $("input[placeholder='Enter new name']").shouldBe(visible, enabled).setValue(firstName);

        // 6) сохранить
        $$("button").findBy(text("Save Changes")).shouldBe(visible, enabled).click();

        // 7) ОР: сообщение об успехе
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText).contains("Name updated successfully");
        alert.accept();

        // 8) вернуться в меню
        $$("button").findBy(text("Home")).shouldBe(visible, enabled).click();

        // 9)снова открыть редактирование профиля
        $("header .profile-header").shouldBe(visible, enabled).click();
        webdriver().shouldHave(urlContaining("/edit-profile"));

        // 10)ввести то же самое имя
        $("input[placeholder='Enter new name']").shouldBe(visible, enabled).setValue(secondName);

        // 11)сохранить
        $$("button").findBy(text("Save Changes")).shouldBe(visible, enabled).click();

        // 12) ОР: сообщение об ошибке
        Alert alert2 = switchTo().alert();
        String alertText2 = alert2.getText();
        assertThat(alertText2).contains("New name is the same as the current one");
        alert2.accept();

        // 13) проверка имени в API
        String actualName = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when()
                .get("customer/profile")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("name");

        assertThat(actualName).isEqualTo(firstName);
    }

    @Test
// Негативный тест: повторное изменение на пробелы
    void updateNameSecondTimeToSpaces() {
        String firstName = "Keys Jons";
        String secondName = "    ";

        // 1) пользователь (API)
        CreateUserRequest user = AdminSteps.createUser();

        // 2) авторизация для API
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) логин - токен в localStorage (для UI)
        String authHeader = new CrudRequester(
                RequestSpecs.unauthSpec(), Endpoint.LOGIN, ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .build())
                .extract()
                .header("Authorization");

        // 4) перейти на изменение профиля
        open("/");
        executeJavaScript("localStorage.setItem('authToken', arguments[0])", authHeader);
        open("/edit-profile");

        // 5) ввести первое имя
        $("input[placeholder='Enter new name']").shouldBe(visible, enabled).setValue(firstName);

        // 6) сохранить
        $$("button").findBy(text("Save Changes")).shouldBe(visible, enabled).click();

        // 7) ОР: сообщение об успехе
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText).contains("Name updated successfully");
        alert.accept();

        // 8)вернуться в меню
        $$("button").findBy(text("Home")).shouldBe(visible, enabled).click();

        // 9)снова открыть редактирование профиля
        $("header .profile-header").shouldBe(visible, enabled).click();
        webdriver().shouldHave(urlContaining("/edit-profile"));

        // 10)ввести пробелы
        $("input[placeholder='Enter new name']").shouldBe(visible, enabled).setValue(secondName);

        // 11)сохранить
        $$("button").findBy(text("Save Changes")).shouldBe(visible, enabled).click();

        // 12) ОР: сообщение об ошибке
        Alert alert2 = switchTo().alert();
        String alertText2 = alert2.getText();
        assertThat(alertText2).contains("Please enter a valid name");
        alert2.accept();

        // 13) проверка имени в API
        String actualName = io.restassured.RestAssured.given()
                .spec(userSpec)
                .when()
                .get("customer/profile")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("name");

        assertThat(actualName).isEqualTo(firstName);
    }

}
