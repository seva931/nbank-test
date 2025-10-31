package iteration2test;

import generators.RandomData;
import models.AccountResponse;
import models.CreateUserRequest;
import models.DepositRequest;
import models.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import requests.AdminCreateUserRequester;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

// Кейсы для депозита счета: POST /api/v1/accounts/deposit
public class DepositTest extends BaseTest {

    // Бизнес-константы
    private static final BigDecimal MIN_DEPOSIT = new BigDecimal("0.01");
    private static final BigDecimal MAX_DEPOSIT = new BigDecimal("5000");

    private String username;
    private String password;
    private Long accountId;

    @BeforeEach
    @DisplayName("Предусловие: создан пользователь USER и счёт")
    void initUserAndAccount() {
        // 1) создаём пользователя (USER)
        username = RandomData.getUsername();
        password = RandomData.getPassword();

        new AdminCreateUserRequester(
                RequestSpecs.adminSpec(),
                ResponseSpecs.entityWasCreated()
        ).post(CreateUserRequest.builder()
                .username(username)
                .password(password)
                .role(UserRole.USER)
                .build());

        // 2) авторизуемся
        var userSpec = RequestSpecs.authAsUser(username, password);

        // 3) создаём счёт и берём id
        AccountResponse acc = given().spec(userSpec)
                .when().post("/api/v1/accounts")
                .then().spec(ResponseSpecs.entityWasCreated())
                .extract().as(AccountResponse.class);

        accountId = acc.getId();
    }

    // Позитивные кейсы
    static Stream<String> validAmounts() {
        return Stream.of(
                MIN_DEPOSIT.toPlainString(),
                "1",
                "10.50",
                "4999.99",
                "4121.992",
                MAX_DEPOSIT.toPlainString()
        );
    }

    @ParameterizedTest
    @MethodSource("validAmounts")
    @DisplayName("Позитив: депозит допустимых сумм в свой счёт")
    void depositValidAmounts(String amountStr) {
        var userSpec = RequestSpecs.authAsUser(username, password);

        given().spec(userSpec)
                .body(new DepositRequest(accountId, new BigDecimal(amountStr)))
                .when()
                .post("/api/v1/accounts/deposit")
                .then()
                .statusCode(anyOf(is(200)));
    }

    // Вспомогательный метод: создать счёт и вернуть его id
    private Long createAccount(io.restassured.specification.RequestSpecification userSpec) {
        AccountResponse acc = given().spec(userSpec)
                .when().post("/api/v1/accounts")
                .then().spec(ResponseSpecs.entityWasCreated())
                .extract().as(AccountResponse.class);
        return acc.getId();
    }

    @Test
    @DisplayName("Позитив: депозит только во второй счёт (первый пустой)")
    void depositIntoSecondAccountWhenFirstEmpty() {
        var userSpec = RequestSpecs.authAsUser(username, password);
        Long firstAccountId = createAccount(userSpec);
        Long secondAccountId = createAccount(userSpec);

        given().spec(userSpec)
                .body(new DepositRequest(secondAccountId, new BigDecimal("10.00")))
                .when()
                .post("/api/v1/accounts/deposit")
                .then()
                .statusCode(anyOf(is(200)));
    }

    @Test
    @DisplayName("Позитив: депозит в каждый из двух счетов")
    void depositIntoEachOfTwoAccounts() {
        var userSpec = RequestSpecs.authAsUser(username, password);
        Long firstAccountId = createAccount(userSpec);
        Long secondAccountId = createAccount(userSpec);

        given().spec(userSpec)
                .body(new DepositRequest(firstAccountId, new BigDecimal("5.00")))
                .when()
                .post("/api/v1/accounts/deposit")
                .then()
                .statusCode(anyOf(is(200), is(201)));

        given().spec(userSpec)
                .body(new DepositRequest(secondAccountId, new BigDecimal("7.50")))
                .when()
                .post("/api/v1/accounts/deposit")
                .then()
                .statusCode(anyOf(is(200)));
    }

    // Негативные кейсы:
    static Stream<String> invalidAmounts() {
        return Stream.of(
                "0",
                "-0.01",
                "-1",
                MAX_DEPOSIT.add(MIN_DEPOSIT).toPlainString()
        );
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    @DisplayName("Негатив: депозит недопустимых сумм")
    void depositInvalidAmount(String badAmount) {
        var userSpec = RequestSpecs.authAsUser(username, password);
        Long accountId = createAccount(userSpec);

        given().spec(userSpec)
                .body(new DepositRequest(accountId, new BigDecimal(badAmount)))
                .when()
                .post("/api/v1/accounts/deposit")
                .then()
                .statusCode(400);
    }

    // Невалидные payload
    static Stream<Object> invalidPayloads() {
        // balance: null/пустые/строка/булево/массив/объект
        Map<String, Object> balanceNull = map(1, null);
        Map<String, Object> balanceEmpty = map(1, "");
        Map<String, Object> balanceSpace = map(1, " ");
        Map<String, Object> balanceText = map(1, "abc");
        Map<String, Object> balanceStringNumber = map(1, "10");
        Map<String, Object> balanceBool = map(1, true);
        Map<String, Object> balanceArray = map(1, List.of("10"));
        Map<String, Object> balanceObject = map(1, Map.of("amount", "10"));

        // отсутствует balance / отсутствует id / некорректный id
        Map<String, Object> noBalance = new HashMap<>() {{
            put("id", 1);
        }};
        Map<String, Object> noId = new HashMap<>() {{
            put("balance", 10);
        }};
        Map<String, Object> idNull = new HashMap<>() {{
            put("id", null);
            put("balance", 10);
        }};
        Map<String, Object> idString = new HashMap<>() {{
            put("id", "abc");
            put("balance", 10);
        }};
        Map<String, Object> idEmpty = new HashMap<>() {{
            put("id", "");
            put("balance", 10);
        }};
        Map<String, Object> idBool = new HashMap<>() {{
            put("id", true);
            put("balance", 10);
        }};
        Map<String, Object> idArray = new HashMap<>() {{
            put("id", List.of(1));
            put("balance", 10);
        }};
        Map<String, Object> idObject = new HashMap<>() {{
            put("id", Map.of("v", 1));
            put("balance", 10);
        }};

        // сырой body
        String emptyRaw = "";
        String jsonNull = "null";
        Map<String, Object> emptyObj = Map.of();

        return Stream.of(
                balanceNull, balanceEmpty, balanceSpace, balanceText, balanceStringNumber, balanceBool, balanceArray, balanceObject,
                noBalance, noId, idNull, idString, idEmpty, idBool, idArray, idObject,
                emptyRaw, jsonNull, emptyObj
        );
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    @DisplayName("Негатив: депозит — некорректный payload")
    void depositInvalidPayload(Object body) {
        var userSpec = RequestSpecs.authAsUser(username, password);
        Long accountId = createAccount(userSpec);

        if (body instanceof Map<?, ?> map && map.containsKey("id") && map.get("id") instanceof Integer i && i == 1) {
            ((Map<String, Object>) map).put("id", accountId);
        }

        given().spec(userSpec)
                .body(body)
                .when()
                .post("/api/v1/accounts/deposit")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("Негатив: депозит в чужой счёт")
    void depositForeignAccount() {
        String otherUser = RandomData.getUsername();
        String otherPass = RandomData.getPassword();

        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(CreateUserRequest.builder()
                        .username(otherUser).password(otherPass).role(UserRole.USER).build());

        var otherSpec = RequestSpecs.authAsUser(otherUser, otherPass);
        Long foreignAccountId = createAccount(otherSpec);

        var userSpec = RequestSpecs.authAsUser(username, password);

        given().spec(userSpec)
                .body(new DepositRequest(foreignAccountId, new BigDecimal("10")))
                .when()
                .post("/api/v1/accounts/deposit")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("Негатив: депозит в несуществующий счёт")
    void depositNonexistentAccount() {
        var userSpec = RequestSpecs.authAsUser(username, password);
        Long accountId = createAccount(userSpec);
        long nonexistentId = accountId + 999_999L;

        given().spec(userSpec)
                .body(new DepositRequest(nonexistentId, new BigDecimal("10")))
                .when()
                .post("/api/v1/accounts/deposit")
                .then()
                .statusCode(403);
    }

    //Используется в негативных тестах, чтобы передавать любые типы
    private static Map<String, Object> map(Object id, Object balance) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("balance", balance);
        return m;
    }
}