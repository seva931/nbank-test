package iteration2test.api;

import io.restassured.builder.ResponseSpecBuilder;
import api.models.AccountResponse;
import api.models.CreateUserRequest;
import api.models.DepositRequest;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import api.requests.skelethon.Endpoint;
import api.requests.skelethon.requester.CrudRequester;
import api.requests.steps.AccountSteps;
import api.requests.steps.AdminSteps;
import api.specs.RequestSpecs;
import api.specs.ResponseSpecs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static api.models.comparison.ModelAssertions.assertThatModels;

// Кейсы для депозита счета: POST /api/v1/accounts/deposit

public class DepositTest extends BaseTest {

    // Бизнес-константы
    private static final BigDecimal MIN_DEPOSIT = new BigDecimal("0.01");
    private static final BigDecimal MAX_DEPOSIT = new BigDecimal("5000");

    private CreateUserRequest user;
    private Long accountId;

    @BeforeEach
    @DisplayName("Предусловие: создан пользователь USER и счёт")
    void initUserAndAccount() {
        // 1) пользователь USER
        user = AdminSteps.createUser();

        // 2) авторизация пользователя
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // 3) создание счёта
        AccountResponse acc = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        accountId = acc.getId();
    }

    // Позитивные суммы
    static Stream<BigDecimal> validAmounts() {
        return Stream.of(
                MIN_DEPOSIT,
                new BigDecimal("1"),
                new BigDecimal("10.50"),
                new BigDecimal("4999.99"),
                new BigDecimal("4121.992"),
                MAX_DEPOSIT
        );
    }

    @ParameterizedTest
    @MethodSource("validAmounts")
    @DisplayName("Позитив: депозит допустимых сумм в свой счёт")
    void depositValidAmounts(BigDecimal amount) {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        DepositRequest payload = DepositRequest.builder()
                .id(accountId)
                .balance(amount)
                .build();

        AccountResponse resp = AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                payload
        );

        // Сопоставление по конфигу (id -> id, balance -> balance)
        assertThatModels(payload, resp).match();
    }

    @Test
    @DisplayName("Позитив: депозит только во второй счёт (первый пустой)")
    void depositIntoSecondAccountWhenFirstEmpty() {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        Long firstAccountId = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated()).getId();
        Long secondAccountId = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated()).getId();

        BigDecimal amount = BigDecimal
                .valueOf(ThreadLocalRandom.current().nextDouble(0.01, 5000.00))
                .setScale(2, RoundingMode.HALF_UP);

        AccountResponse resp = AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                DepositRequest.builder().id(secondAccountId).balance(amount).build()
        );

        assertThatModels(
                DepositRequest.builder().id(secondAccountId).balance(amount).build(),
                resp
        ).match();
    }

    @Test
    @DisplayName("Позитив: депозит в каждый из двух счетов")
    void depositIntoEachOfTwoAccounts() {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        Long firstAccountId = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated()).getId();
        Long secondAccountId = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated()).getId();

        BigDecimal amount1 = BigDecimal
                .valueOf(ThreadLocalRandom.current().nextDouble(0.01, 5000.00))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount2 = BigDecimal
                .valueOf(ThreadLocalRandom.current().nextDouble(0.01, 5000.00))
                .setScale(2, RoundingMode.HALF_UP);

        AccountResponse r1 = AccountSteps.deposit(
                userSpec, ResponseSpecs.requestReturnsOK(),
                DepositRequest.builder().id(firstAccountId).balance(amount1).build()
        );
        assertThatModels(DepositRequest.builder().id(firstAccountId).balance(amount1).build(), r1).match();

        AccountResponse r2 = AccountSteps.deposit(
                userSpec, ResponseSpecs.requestReturnsOK(),
                DepositRequest.builder().id(secondAccountId).balance(amount2).build()
        );
        assertThatModels(DepositRequest.builder().id(secondAccountId).balance(amount2).build(), r2).match();
    }

    // Негативные суммы (валидация)
    static Stream<BigDecimal> invalidAmounts() {
        return Stream.of(
                new BigDecimal("0"),
                new BigDecimal("-0.01"),
                new BigDecimal("-1"),
                MAX_DEPOSIT.add(MIN_DEPOSIT) // > MAX
        );
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    @DisplayName("Негатив: депозит недопустимых сумм")
    void depositInvalidAmount(BigDecimal badAmount) {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        new CrudRequester(
                userSpec,
                Endpoint.DEPOSIT,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_BAD_REQUEST).build()
        ).post(DepositRequest.builder().id(accountId).balance(badAmount).build());
    }

    // Невалидные payload
    static Stream<Object> invalidPayloads() {
        Map<String, Object> balanceNull = map(1, null);
        Map<String, Object> balanceEmpty = map(1, "");
        Map<String, Object> balanceSpace = map(1, " ");
        Map<String, Object> balanceText = map(1, "abc");
        Map<String, Object> balanceStringNumber = map(1, "10");
        Map<String, Object> balanceBool = map(1, true);
        Map<String, Object> balanceArray = map(1, List.of("10"));
        Map<String, Object> balanceObject = map(1, Map.of("amount", "10"));

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
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        if (body instanceof Map<?, ?> map && map.containsKey("id") && map.get("id") instanceof Integer i && i == 1) {
            ((Map<String, Object>) map).put("id", accountId);
        }

        var bad = new ResponseSpecBuilder()
                .expectStatusCode(HttpStatus.SC_BAD_REQUEST)
                .build();

        new CrudRequester(userSpec, Endpoint.DEPOSIT, bad).post(body);
    }

    @Test
    @DisplayName("Негатив: депозит в чужой счёт")
    void depositForeignAccount() {
        // создаём другого пользователя и его счёт
        CreateUserRequest other = AdminSteps.createUser();
        var otherSpec = RequestSpecs.authAsUser(other.getUsername(), other.getPassword());
        Long foreignAccountId = AccountSteps.createAccount(otherSpec, ResponseSpecs.entityWasCreated()).getId();

        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        new CrudRequester(
                userSpec,
                Endpoint.DEPOSIT,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_FORBIDDEN).build()
        ).post(DepositRequest.builder().id(foreignAccountId).balance(new BigDecimal("10")).build());
    }

    @Test
    @DisplayName("Негатив: депозит в несуществующий счёт")
    void depositNonexistentAccount() {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        long nonexistentId = accountId + 999_999L;

        new CrudRequester(
                userSpec,
                Endpoint.DEPOSIT,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_FORBIDDEN).build()
        ).post(DepositRequest.builder().id(nonexistentId).balance(new BigDecimal("10")).build());
    }

    private static Map<String, Object> map(Object id, Object balance) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("balance", balance);
        return m;
    }
}
