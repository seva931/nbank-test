package iteration2test.api;

import api.models.AccountResponse;
import api.models.CreateUserRequest;
import api.models.DepositRequest;
import api.requests.skelethon.Endpoint;
import api.requests.skelethon.requester.CrudRequester;
import api.requests.steps.AccountSteps;
import api.requests.steps.AdminSteps;
import api.requests.steps.DataBaseSteps;
import api.specs.RequestSpecs;
import api.specs.ResponseSpecs;
import io.restassured.builder.ResponseSpecBuilder;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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

    @Disabled("Баг backend: /api/v1/accounts/deposit возвращает бесконечно вложенный transactions, JSON не парсится")
    @ParameterizedTest
    @MethodSource("validAmounts")
    @DisplayName("Позитив: депозит допустимых сумм в свой счёт + проверка БД")
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

        // Проверка состояния счёта в БД
        var accountFromDb = DataBaseSteps.getAccountById(resp.getId());
        softly.assertThat(accountFromDb).isNotNull();
        softly.assertThat(accountFromDb.getId()).isEqualTo(resp.getId());
        softly.assertThat(accountFromDb.getAccountNumber()).isEqualTo(resp.getAccountNumber());
        softly.assertThat(
                BigDecimal.valueOf(accountFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(resp.getBalance());
    }

    @Disabled("Баг backend: /api/v1/accounts/deposit возвращает бесконечно вложенный transactions, JSON не парсится")
    @Test
    @DisplayName("Позитив: депозит только во второй счёт (первый пустой) + проверка БД")
    void depositIntoSecondAccountWhenFirstEmpty() {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        AccountResponse firstAccount = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        AccountResponse secondAccount = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());

        Long firstAccountId = firstAccount.getId();
        Long secondAccountId = secondAccount.getId();

        BigDecimal amount = BigDecimal
                .valueOf(ThreadLocalRandom.current().nextDouble(0.01, 5000.00))
                .setScale(2, RoundingMode.HALF_UP);

        DepositRequest payload = DepositRequest.builder()
                .id(secondAccountId)
                .balance(amount)
                .build();

        AccountResponse resp = AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                payload
        );

        assertThatModels(payload, resp).match();

        // Проверка: второй счёт в БД получил депозит
        var secondFromDb = DataBaseSteps.getAccountById(secondAccountId);
        softly.assertThat(secondFromDb).isNotNull();
        softly.assertThat(secondFromDb.getId()).isEqualTo(resp.getId());
        softly.assertThat(secondFromDb.getAccountNumber()).isEqualTo(resp.getAccountNumber());
        softly.assertThat(
                BigDecimal.valueOf(secondFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(resp.getBalance());

        // Проверка: первый счёт остался пустым
        var firstFromDb = DataBaseSteps.getAccountById(firstAccountId);
        softly.assertThat(firstFromDb).isNotNull();
        softly.assertThat(
                BigDecimal.valueOf(firstFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Disabled("Баг backend: /api/v1/accounts/deposit возвращает бесконечно вложенный transactions, JSON не парсится")
    @Test
    @DisplayName("Позитив: депозит в каждый из двух счетов + проверка БД")
    void depositIntoEachOfTwoAccounts() {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        AccountResponse firstAccount = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        AccountResponse secondAccount = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());

        Long firstAccountId = firstAccount.getId();
        Long secondAccountId = secondAccount.getId();

        BigDecimal amount1 = BigDecimal
                .valueOf(ThreadLocalRandom.current().nextDouble(0.01, 5000.00))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount2 = BigDecimal
                .valueOf(ThreadLocalRandom.current().nextDouble(0.01, 5000.00))
                .setScale(2, RoundingMode.HALF_UP);

        DepositRequest payload1 = DepositRequest.builder()
                .id(firstAccountId)
                .balance(amount1)
                .build();
        AccountResponse r1 = AccountSteps.deposit(
                userSpec, ResponseSpecs.requestReturnsOK(), payload1
        );
        assertThatModels(payload1, r1).match();

        DepositRequest payload2 = DepositRequest.builder()
                .id(secondAccountId)
                .balance(amount2)
                .build();
        AccountResponse r2 = AccountSteps.deposit(
                userSpec, ResponseSpecs.requestReturnsOK(), payload2
        );
        assertThatModels(payload2, r2).match();

        // Проверка БД для первого счёта
        var firstFromDb = DataBaseSteps.getAccountById(firstAccountId);
        softly.assertThat(firstFromDb).isNotNull();
        softly.assertThat(
                BigDecimal.valueOf(firstFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(r1.getBalance());

        // Проверка БД для второго счёта
        var secondFromDb = DataBaseSteps.getAccountById(secondAccountId);
        softly.assertThat(secondFromDb).isNotNull();
        softly.assertThat(
                BigDecimal.valueOf(secondFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(r2.getBalance());
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
    @DisplayName("Негатив: депозит недопустимых сумм + проверка БД (без изменений)")
    void depositInvalidAmount(BigDecimal badAmount) {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        new CrudRequester(
                userSpec,
                Endpoint.DEPOSIT,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_BAD_REQUEST).build()
        ).post(DepositRequest.builder().id(accountId).balance(badAmount).build());

        // Проверка: баланс счёта в БД не изменился
        var accountFromDb = DataBaseSteps.getAccountById(accountId);
        softly.assertThat(accountFromDb).isNotNull();
        softly.assertThat(
                BigDecimal.valueOf(accountFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(BigDecimal.ZERO);
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
    @Disabled("BUG backend: /api/v1/accounts/deposit возвращает 500 вместо 400 при некорректном payload")
    @ParameterizedTest
    @MethodSource("invalidPayloads")
    @DisplayName("Негатив: депозит — некорректный payload + проверка БД (без изменений)")
    void depositInvalidPayload(Object body) {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // Подмена id=1 на реальный accountId там, где это применимо
        if (body instanceof Map<?, ?> map && map.containsKey("id") && map.get("id") instanceof Integer i && i == 1) {
            ((Map<String, Object>) map).put("id", accountId);
        }

        var bad = new ResponseSpecBuilder()
                .expectStatusCode(HttpStatus.SC_BAD_REQUEST)
                .build();

        new CrudRequester(userSpec, Endpoint.DEPOSIT, bad).post(body);

        // Баланс реального счёта в БД не меняется
        var accountFromDb = DataBaseSteps.getAccountById(accountId);
        softly.assertThat(accountFromDb).isNotNull();
        softly.assertThat(
                BigDecimal.valueOf(accountFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Негатив: депозит в чужой счёт + проверка БД (без изменений)")
    void depositForeignAccount() {
        // создаём другого пользователя и его счёт
        CreateUserRequest other = AdminSteps.createUser();
        var otherSpec = RequestSpecs.authAsUser(other.getUsername(), other.getPassword());
        AccountResponse foreignAccount = AccountSteps.createAccount(otherSpec, ResponseSpecs.entityWasCreated());
        Long foreignAccountId = foreignAccount.getId();

        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        new CrudRequester(
                userSpec,
                Endpoint.DEPOSIT,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_FORBIDDEN).build()
        ).post(DepositRequest.builder().id(foreignAccountId).balance(new BigDecimal("10")).build());

        // Проверка: баланс чужого счёта не изменился
        var foreignFromDb = DataBaseSteps.getAccountById(foreignAccountId);
        softly.assertThat(foreignFromDb).isNotNull();
        softly.assertThat(
                BigDecimal.valueOf(foreignFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Негатив: депозит в несуществующий счёт + проверка БД (без изменений)")
    void depositNonexistentAccount() {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        long nonexistentId = accountId + 999_999L;

        new CrudRequester(
                userSpec,
                Endpoint.DEPOSIT,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_FORBIDDEN).build()
        ).post(DepositRequest.builder().id(nonexistentId).balance(new BigDecimal("10")).build());

        // Проверка: баланс существующего счёта пользователя не изменился
        var accountFromDb = DataBaseSteps.getAccountById(accountId);
        softly.assertThat(accountFromDb).isNotNull();
        softly.assertThat(
                BigDecimal.valueOf(accountFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private static Map<String, Object> map(Object id, Object balance) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("balance", balance);
        return m;
    }
}
