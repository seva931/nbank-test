package iteration2test.api;

import api.models.CreateUserRequest;
import api.models.DepositRequest;
import api.models.TransferRequest;
import api.models.TransferResponse;
import api.requests.skelethon.Endpoint;
import api.requests.skelethon.requester.CrudRequester;
import api.requests.steps.AccountSteps;
import api.requests.steps.AdminSteps;
import api.requests.steps.DataBaseSteps;
import api.specs.RequestSpecs;
import api.specs.ResponseSpecs;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.specification.RequestSpecification;
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
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static api.models.comparison.ModelAssertions.assertThatModels;

// Кейсы для перевода денег: POST /api/v1/accounts/transfer
public class MoneyTransferTest extends BaseTest {

    // Бизнес-константы
    private static final BigDecimal MIN_TRANSFER = new BigDecimal("0.01");
    private static final BigDecimal MAX_TRANSFER = new BigDecimal("10000");
    private static final BigDecimal DEPOSIT_MAX = new BigDecimal("5000");

    private CreateUserRequest user;
    private Long senderAccountId;
    private Long receiverAccountId;

    @BeforeEach
    @DisplayName("Предусловие: создан пользователь USER и два счёта")
    void initUser() {
        user = AdminSteps.createUser();

        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        senderAccountId = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated()).getId();
        receiverAccountId = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated()).getId();
    }

    // Позитивные суммы
    static Stream<BigDecimal> positiveAmounts() {
        return Stream.of(
                MIN_TRANSFER,
                new BigDecimal("1"),
                new BigDecimal("2500.50"),
                MAX_TRANSFER
        );
    }

    @ParameterizedTest
    @MethodSource("positiveAmounts")
    @DisplayName("Позитив: перевод между своими счетами — допустимые суммы + проверка БД")
    void transferPositiveAmountsBetweenOwnAccounts(BigDecimal amount) {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        // пополняем счёт-отправитель на сумму перевода
        fundAccount(userSpec, senderAccountId, amount);

        TransferRequest payload = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(amount)
                .build();

        TransferResponse resp = AccountSteps.transfer(
                userSpec, ResponseSpecs.requestReturnsOK(), payload
        );

        // Сопоставление по конфигу (включая константу message)
        assertThatModels(payload, resp).match();

        // Проверка БД: у отправителя 0, у получателя amount
        var senderFromDb = DataBaseSteps.getAccountById(senderAccountId);
        var receiverFromDb = DataBaseSteps.getAccountById(receiverAccountId);

        softly.assertThat(senderFromDb).isNotNull();
        softly.assertThat(receiverFromDb).isNotNull();

        softly.assertThat(
                BigDecimal.valueOf(senderFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        softly.assertThat(
                BigDecimal.valueOf(receiverFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(amount.setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Позитив: перевод на чужой счёт + проверка БД")
    void transferToForeignAccount() {
        // чужой пользователь и его счёт-получатель
        CreateUserRequest other = AdminSteps.createUser();
        var otherSpec = RequestSpecs.authAsUser(other.getUsername(), other.getPassword());
        Long foreignReceiverId = AccountSteps.createAccount(otherSpec, ResponseSpecs.entityWasCreated()).getId();

        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        BigDecimal amount = BigDecimal
                .valueOf(ThreadLocalRandom.current().nextDouble(0.01, 5000.00))
                .setScale(2, RoundingMode.HALF_UP);

        // пополняем счёт-отправитель на сумму перевода
        fundAccount(userSpec, senderAccountId, amount);

        TransferRequest payload = new TransferRequest(senderAccountId, foreignReceiverId, amount);
        TransferResponse resp = AccountSteps.transfer(userSpec, ResponseSpecs.requestReturnsOK(), payload);

        assertThatModels(payload, resp).match();

        // Проверка БД: у отправителя 0, у чужого получателя amount
        var senderFromDb = DataBaseSteps.getAccountById(senderAccountId);
        var foreignFromDb = DataBaseSteps.getAccountById(foreignReceiverId);

        softly.assertThat(senderFromDb).isNotNull();
        softly.assertThat(foreignFromDb).isNotNull();

        softly.assertThat(
                BigDecimal.valueOf(senderFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        softly.assertThat(
                BigDecimal.valueOf(foreignFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(amount.setScale(2, RoundingMode.HALF_UP));
    }

    // Негативные суммы
    static Stream<BigDecimal> negativeNumericAmounts() {
        return Stream.of(
                BigDecimal.ZERO,
                new BigDecimal("-1"),
                MAX_TRANSFER.add(MIN_TRANSFER)
        );
    }

    @ParameterizedTest
    @MethodSource("negativeNumericAmounts")
    @DisplayName("Негатив: между своими — суммы 0 / <0 / > MAX_TRANSFER + проверка БД (без изменений)")
    void transferNegativeCoreAmountsBetweenOwnAccounts(BigDecimal amount) {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        BigDecimal preload = amount.compareTo(BigDecimal.ZERO) > 0
                ? amount.min(MAX_TRANSFER)
                : new BigDecimal("1");
        fundAccount(userSpec, senderAccountId, preload);

        new CrudRequester(
                userSpec,
                Endpoint.TRANSFER,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_BAD_REQUEST).build()
        ).post(TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(amount)
                .build());

        // Проверка БД: баланс не изменился
        var senderFromDb = DataBaseSteps.getAccountById(senderAccountId);
        var receiverFromDb = DataBaseSteps.getAccountById(receiverAccountId);

        softly.assertThat(senderFromDb).isNotNull();
        softly.assertThat(receiverFromDb).isNotNull();

        softly.assertThat(
                BigDecimal.valueOf(senderFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(preload.setScale(2, RoundingMode.HALF_UP));

        softly.assertThat(
                BigDecimal.valueOf(receiverFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    @ParameterizedTest
    @MethodSource("negativeNumericAmounts")
    @DisplayName("Негатив: на чужой счёт — суммы 0 / <0 / > MAX_TRANSFER + проверка БД (без изменений)")
    void transferToForeignNegativeAmounts(BigDecimal amount) {
        // чужой пользователь и его счёт-получатель
        CreateUserRequest other = AdminSteps.createUser();
        var otherSpec = RequestSpecs.authAsUser(other.getUsername(), other.getPassword());
        Long foreignReceiverId = AccountSteps.createAccount(otherSpec, ResponseSpecs.entityWasCreated()).getId();

        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        BigDecimal preload = amount.compareTo(BigDecimal.ZERO) > 0 ? MAX_TRANSFER : new BigDecimal("1");
        fundAccount(userSpec, senderAccountId, preload);

        new CrudRequester(
                userSpec,
                Endpoint.TRANSFER,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_BAD_REQUEST).build()
        ).post(new TransferRequest(senderAccountId, foreignReceiverId, amount));

        // Проверка БД: баланс не изменился
        var senderFromDb = DataBaseSteps.getAccountById(senderAccountId);
        var foreignFromDb = DataBaseSteps.getAccountById(foreignReceiverId);

        softly.assertThat(senderFromDb).isNotNull();
        softly.assertThat(foreignFromDb).isNotNull();

        softly.assertThat(
                BigDecimal.valueOf(senderFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(preload.setScale(2, RoundingMode.HALF_UP));

        softly.assertThat(
                BigDecimal.valueOf(foreignFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    // Негатив: превышение баланса отправителя
    static Stream<BigDecimal> negativeAmountsExceedsBalance() {
        return Stream.of(
                new BigDecimal("100.01"),
                new BigDecimal("5000.01")
        );
    }

    @ParameterizedTest
    @MethodSource("negativeAmountsExceedsBalance")
    @DisplayName("Негатив: между своими — сумма превышает баланс отправителя + проверка БД (без изменений)")
    void transferAmountExceedsBalanceBetweenOwnAccounts(BigDecimal amount) {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        // кладём меньше, чем переводим
        BigDecimal balance = amount.subtract(MIN_TRANSFER);
        fundAccount(userSpec, senderAccountId, balance);

        new CrudRequester(
                userSpec,
                Endpoint.TRANSFER,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_BAD_REQUEST).build()
        ).post(TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(amount)
                .build());

        // Проверка БД: баланс не изменился
        var senderFromDb = DataBaseSteps.getAccountById(senderAccountId);
        var receiverFromDb = DataBaseSteps.getAccountById(receiverAccountId);

        softly.assertThat(senderFromDb).isNotNull();
        softly.assertThat(receiverFromDb).isNotNull();

        softly.assertThat(
                BigDecimal.valueOf(senderFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(balance.setScale(2, RoundingMode.HALF_UP));

        softly.assertThat(
                BigDecimal.valueOf(receiverFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    @ParameterizedTest
    @MethodSource("negativeAmountsExceedsBalance")
    @DisplayName("Негатив: на чужой счёт — сумма превышает баланс отправителя + проверка БД (без изменений)")
    void transferToForeignExceedsSenderBalance(BigDecimal amount) {
        // чужой пользователь и его счёт-получатель
        CreateUserRequest other = AdminSteps.createUser();
        var otherSpec = RequestSpecs.authAsUser(other.getUsername(), other.getPassword());
        Long foreignReceiverId = AccountSteps.createAccount(otherSpec, ResponseSpecs.entityWasCreated()).getId();

        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        BigDecimal balance = amount.subtract(MIN_TRANSFER);
        fundAccount(userSpec, senderAccountId, balance);

        new CrudRequester(
                userSpec,
                Endpoint.TRANSFER,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_BAD_REQUEST).build()
        ).post(new TransferRequest(senderAccountId, foreignReceiverId, amount));

        // Проверка БД: баланс не изменился
        var senderFromDb = DataBaseSteps.getAccountById(senderAccountId);
        var foreignFromDb = DataBaseSteps.getAccountById(foreignReceiverId);

        softly.assertThat(senderFromDb).isNotNull();
        softly.assertThat(foreignFromDb).isNotNull();

        softly.assertThat(
                BigDecimal.valueOf(senderFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(balance.setScale(2, RoundingMode.HALF_UP));

        softly.assertThat(
                BigDecimal.valueOf(foreignFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    // Негативные payload
    static Stream<Object> invalidTransferPayloads() {
        Map<String, Object> amountNull = map(111L, 222L, null);
        Map<String, Object> amountEmpty = map(111L, 222L, "");
        Map<String, Object> amountText = map(111L, 222L, "abc");

        Map<String, Object> noAmount = new HashMap<>() {{
            put("senderAccountId", 111L);
            put("receiverAccountId", 222L);
        }};
        Map<String, Object> noSender = new HashMap<>() {{
            put("receiverAccountId", 222L);
            put("amount", 10);
        }};
        Map<String, Object> noReceiver = new HashMap<>() {{
            put("senderAccountId", 111L);
            put("amount", 10);
        }};

        Map<String, Object> senderNull = map(null, 222L, 10);
        Map<String, Object> receiverNull = map(111L, null, 10);

        String emptyRaw = "";
        String jsonNull = "null";

        return Stream.of(
                amountNull, amountEmpty, amountText,
                noAmount, noSender, noReceiver,
                senderNull, receiverNull,
                emptyRaw, jsonNull
        );
    }

    @Disabled("BUG backend: /api/v1/accounts/transfer возвращает 500 вместо 400 при некорректном payload")
    @ParameterizedTest
    @MethodSource("invalidTransferPayloads")
    @DisplayName("Негатив: между своими — некорректный payload + проверка БД (без изменений)")
    void transferBetweenOwnInvalidPayloadsReturns400(Object raw) {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        // даём положительный баланс, чтобы ошибка была именно по payload
        BigDecimal initialBalance = new BigDecimal("10");
        fundAccount(userSpec, senderAccountId, initialBalance);

        Object body = raw;
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> b = new HashMap<>();
            m.forEach((k, v) -> b.put(String.valueOf(k), v));

            if (b.containsKey("senderAccountId") && b.get("senderAccountId") != null) {
                b.put("senderAccountId", senderAccountId);
            }
            if (b.containsKey("receiverAccountId") && b.get("receiverAccountId") != null) {
                b.put("receiverAccountId", receiverAccountId);
            }

            body = b;
        }

        var bad = new ResponseSpecBuilder()
                .expectStatusCode(HttpStatus.SC_BAD_REQUEST)
                .build();

        new CrudRequester(userSpec, Endpoint.TRANSFER, bad).post(body);

        // Проверка БД: баланс не изменился
        var senderFromDb = DataBaseSteps.getAccountById(senderAccountId);
        var receiverFromDb = DataBaseSteps.getAccountById(receiverAccountId);

        softly.assertThat(senderFromDb).isNotNull();
        softly.assertThat(receiverFromDb).isNotNull();

        softly.assertThat(
                BigDecimal.valueOf(senderFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(initialBalance.setScale(2, RoundingMode.HALF_UP));

        softly.assertThat(
                BigDecimal.valueOf(receiverFromDb.getBalance())
                        .setScale(2, RoundingMode.HALF_UP)
        ).isEqualByComparingTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private static Map<String, Object> map(Object senderId, Object receiverId, Object amount) {
        Map<String, Object> m = new HashMap<>();
        m.put("senderAccountId", senderId);
        m.put("receiverAccountId", receiverId);
        m.put("amount", amount);
        return m;
    }

    // Пополняет счёт на targetAmount с учётом лимита одного депозита (≤ 5000),
    // без маппинга проблемного ответа /deposit.
    private void fundAccount(RequestSpecification userSpec,
                             Long accountId,
                             BigDecimal targetAmount) {
        BigDecimal remaining = targetAmount;
        while (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal chunk = remaining.min(DEPOSIT_MAX);

            DepositRequest payload = DepositRequest.builder()
                    .accountId(accountId)
                    .amount(chunk)
                    .build();

            new CrudRequester(
                    userSpec,
                    Endpoint.DEPOSIT,
                    ResponseSpecs.requestReturnsOK()
            ).post(payload);

            remaining = remaining.subtract(chunk);
        }
    }
}
