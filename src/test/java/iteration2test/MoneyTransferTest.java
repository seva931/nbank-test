package iteration2test;

import io.restassured.builder.ResponseSpecBuilder;
import models.CreateUserRequest;
import models.DepositRequest;
import models.TransferRequest;
import models.TransferResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import requests.skelethon.Endpoint;
import requests.skelethon.requester.CrudRequester;
import requests.steps.AccountSteps;
import requests.steps.AdminSteps;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static models.comparison.ModelAssertions.assertThatModels;


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
    @DisplayName("Позитив: перевод между своими счетами — допустимые суммы")
    void transferPositiveAmountsBetweenOwnAccounts(BigDecimal amount) {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
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
    }

    @Test
    @DisplayName("Позитив: перевод на чужой счёт")
    void transferToForeignAccount() {
        // чужой пользователь и его счёт-получатель
        CreateUserRequest other = AdminSteps.createUser();
        var otherSpec = RequestSpecs.authAsUser(other.getUsername(), other.getPassword());
        Long foreignReceiverId = AccountSteps.createAccount(otherSpec, ResponseSpecs.entityWasCreated()).getId();

        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        BigDecimal amount = BigDecimal
                .valueOf(ThreadLocalRandom.current().nextDouble(0.01, 5000.00))
                .setScale(2, RoundingMode.HALF_UP);
        fundAccount(userSpec, senderAccountId, amount);

        TransferRequest payload = new TransferRequest(senderAccountId, foreignReceiverId, amount);
        TransferResponse resp = AccountSteps.transfer(userSpec, ResponseSpecs.requestReturnsOK(), payload);

        assertThatModels(payload, resp).match();
    }

    //Негативные суммы
    static Stream<BigDecimal> negativeNumericAmounts() {
        return Stream.of(
                BigDecimal.ZERO,
                new BigDecimal("-1"),
                MAX_TRANSFER.add(MIN_TRANSFER)
        );
    }

    @ParameterizedTest
    @MethodSource("negativeNumericAmounts")
    @DisplayName("Негатив: между своими — суммы 0 / <0 / > MAX_TRANSFER")
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
    }

    @ParameterizedTest
    @MethodSource("negativeNumericAmounts")
    @DisplayName("Негатив: на чужой счёт — суммы 0 / <0 / > MAX_TRANSFER")
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
    @DisplayName("Негатив: между своими — сумма превышает баланс отправителя")
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
    }

    @ParameterizedTest
    @MethodSource("negativeAmountsExceedsBalance")
    @DisplayName("Негатив: на чужой счёт — сумма превышает баланс отправителя")
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
    }

    //Негативные payload
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

    @ParameterizedTest
    @MethodSource("invalidTransferPayloads")
    @DisplayName("Негатив: между своими — некорректный payload")
    void transferBetweenOwnInvalidPayloadsReturns400(Object raw) {
        var userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        fundAccount(userSpec, senderAccountId, new BigDecimal("10"));

        Object body = raw;
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> b = new HashMap<>();
            m.forEach((k, v) -> b.put(String.valueOf(k), v));
            if (b.containsKey("senderAccountId")) b.put("senderAccountId", senderAccountId);
            if (b.containsKey("receiverAccountId")) b.put("receiverAccountId", receiverAccountId);
            body = b;
        }

        var bad = new ResponseSpecBuilder()
                .expectStatusCode(HttpStatus.SC_BAD_REQUEST)
                .build();

        new CrudRequester(userSpec, Endpoint.TRANSFER, bad).post(body);
    }

    private static Map<String, Object> map(Object senderId, Object receiverId, Object amount) {
        Map<String, Object> m = new HashMap<>();
        m.put("senderAccountId", senderId);
        m.put("receiverAccountId", receiverId);
        m.put("amount", amount);
        return m;
    }

    // Пополняет счёт на targetAmount с учётом лимита одного депозита (≤ 5000)
    private void fundAccount(io.restassured.specification.RequestSpecification userSpec,
                             Long accountId,
                             BigDecimal targetAmount) {
        BigDecimal remaining = targetAmount;
        while (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal chunk = remaining.min(DEPOSIT_MAX);
            AccountSteps.deposit(
                    userSpec,
                    ResponseSpecs.requestReturnsOK(),
                    DepositRequest.builder().id(accountId).balance(chunk).build()
            );
            remaining = remaining.subtract(chunk);
        }
    }
}
