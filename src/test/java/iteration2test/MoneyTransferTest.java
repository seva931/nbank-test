package iteration2test;

import generators.RandomData;
import io.restassured.builder.ResponseSpecBuilder;
import models.*;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import requests.AdminCreateUserRequester;
import requests.CreateAccountRequester;
import requests.DepositRequester;
import requests.TransferRequester;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

// Кейсы для перевода денег: POST /api/v1/accounts/transfer
public class MoneyTransferTest extends BaseTest {

    // Бизнес-константы
    private static final BigDecimal MIN_TRANSFER = new BigDecimal("0.01");
    private static final BigDecimal MAX_TRANSFER = new BigDecimal("10000");
    private static final BigDecimal DEPOSIT_MAX  = new BigDecimal("5000");

    private String username;
    private String password;
    private Long senderAccountId;
    private Long receiverAccountId;

    @BeforeEach
    @DisplayName("Предусловие: создан пользователь с ролью USER и два счёта")
    void initUser() {
        username = RandomData.getUsername();
        password = RandomData.getPassword();

        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(CreateUserRequest.builder()
                        .username(username)
                        .password(password)
                        .role(UserRole.USER)
                        .build());

        var userSpec = RequestSpecs.authAsUser(username, password);
        senderAccountId   = createAccount(userSpec);
        receiverAccountId = createAccount(userSpec);
    }

    // Позитивные кейсы
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
    @DisplayName("Перевод между своими счетами: допустимые суммы")
    void transferPositiveAmountsBetweenOwnAccounts(BigDecimal amount) {
        var userSpec = RequestSpecs.authAsUser(username, password);

        fundAccount(userSpec, senderAccountId, amount);

        var transferRequester = new TransferRequester(userSpec, ResponseSpecs.requestReturnsOK());
        TransferResponse resp = transferRequester
                .post(TransferRequest.builder()
                        .senderAccountId(senderAccountId)
                        .receiverAccountId(receiverAccountId)
                        .amount(amount)
                        .build())
                .extract().as(TransferResponse.class);

        softly.assertThat(resp.getMessage()).isEqualTo("Transfer successful");
        softly.assertThat(resp.getSenderAccountId()).isEqualTo(senderAccountId);
        softly.assertThat(resp.getReceiverAccountId()).isEqualTo(receiverAccountId);
        softly.assertThat(resp.getAmount()).isEqualByComparingTo(amount);
    }

    @Test
    @DisplayName("Перевод на чужой счёт")
    void transferToForeignAccount() {
        var userSpec = RequestSpecs.authAsUser(username, password);

        String otherUser = RandomData.getUsername();
        String otherPass = RandomData.getPassword();
        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(CreateUserRequest.builder()
                        .username(otherUser).password(otherPass).role(UserRole.USER).build());
        var otherSpec = RequestSpecs.authAsUser(otherUser, otherPass);
        Long foreignReceiverId = createAccount(otherSpec);

        BigDecimal amount = BigDecimal
                .valueOf(ThreadLocalRandom.current().nextDouble(0.01, 5000.00))
                .setScale(2, RoundingMode.HALF_UP);
        fundAccount(userSpec, senderAccountId, amount);

        var transferRequester = new TransferRequester(userSpec, ResponseSpecs.requestReturnsOK());
        TransferResponse resp = transferRequester
                .post(new TransferRequest(senderAccountId, foreignReceiverId, amount))
                .extract().as(TransferResponse.class);

        softly.assertThat(resp.getMessage()).isEqualTo("Transfer successful");
        softly.assertThat(resp.getSenderAccountId()).isEqualTo(senderAccountId);
        softly.assertThat(resp.getReceiverAccountId()).isEqualTo(foreignReceiverId);
        softly.assertThat(resp.getAmount()).isEqualByComparingTo(amount);
    }

    //Создаёт счёт и возвращает его id
    private Long createAccount(io.restassured.specification.RequestSpecification userSpec) {
        AccountResponse acc = new CreateAccountRequester(userSpec, ResponseSpecs.entityWasCreated())
                .post(null)
                .extract().as(AccountResponse.class);
        return acc.getId();
    }

    //Одиночный депозит
    private void deposit(io.restassured.specification.RequestSpecification userSpec, Long accountId, BigDecimal amount) {
        new DepositRequester(userSpec, ResponseSpecs.requestReturnsOK())
                .post(new DepositRequest(accountId, amount));
    }

    /**
     * Пополняет счёт суммой targetAmount с учётом лимита на один депозит (≤ 5000)
     * Разбивает пополнение на несколько депозитов при необходимости
     */
    private void fundAccount(io.restassured.specification.RequestSpecification userSpec,
                             Long accountId,
                             BigDecimal targetAmount) {
        BigDecimal remaining = targetAmount;
        while (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal chunk = remaining.min(DEPOSIT_MAX);
            deposit(userSpec, accountId, chunk);
            remaining = remaining.subtract(chunk);
        }
    }

    // Негативные кейсы
    static Stream<BigDecimal> negativeNumericAmounts() {
        return Stream.of(
                BigDecimal.ZERO,
                new BigDecimal("-1"),
                MAX_TRANSFER.add(MIN_TRANSFER)
        );
    }

    @ParameterizedTest
    @MethodSource("negativeNumericAmounts")
    @DisplayName("Негатив: перевод между своими счетами — суммы 0, <0, >MAX_TRANSFER")
    void transferNegativeCoreAmountsBetweenOwnAccounts(BigDecimal amount) {
        var userSpec = RequestSpecs.authAsUser(username, password);

        BigDecimal need = amount.compareTo(BigDecimal.ZERO) > 0 ? amount.min(MAX_TRANSFER) : new BigDecimal("1");
        fundAccount(userSpec, senderAccountId, need);

        new TransferRequester(
                userSpec,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_BAD_REQUEST).build()
        ).post(TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(amount)
                .build());
    }

    @ParameterizedTest
    @MethodSource("negativeNumericAmounts")
    @DisplayName("Негатив: перевод на чужой счёт — суммы 0/<0/>MAX_TRANSFER")
    void transferToForeignNegativeAmounts(BigDecimal amount) {
        var userSpec = RequestSpecs.authAsUser(username, password);

        String otherUser = RandomData.getUsername();
        String otherPass = RandomData.getPassword();
        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(CreateUserRequest.builder()
                        .username(otherUser)
                        .password(otherPass)
                        .role(UserRole.USER)
                        .build());
        var otherSpec = RequestSpecs.authAsUser(otherUser, otherPass);
        Long foreignReceiverId = createAccount(otherSpec);

        BigDecimal preload = amount.compareTo(BigDecimal.ZERO) > 0 ? MAX_TRANSFER : new BigDecimal("1");
        fundAccount(userSpec, senderAccountId, preload);

        new TransferRequester(
                userSpec,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_BAD_REQUEST).build()
        ).post(new TransferRequest(senderAccountId, foreignReceiverId, amount));
    }

    // Негативные кейсы: превышение баланса отправителя
    static Stream<BigDecimal> negativeAmountsExceedsBalance() {
        return Stream.of(
                new BigDecimal("100.01"),
                new BigDecimal("5000.01")
        );
    }

    @ParameterizedTest
    @MethodSource("negativeAmountsExceedsBalance")
    @DisplayName("Негатив: перевод между своими счетами — сумма превышает баланс отправителя")
    void transferAmountExceedsBalanceBetweenOwnAccounts(BigDecimal amount) {
        var userSpec = RequestSpecs.authAsUser(username, password);

        BigDecimal balance = amount.subtract(MIN_TRANSFER);
        fundAccount(userSpec, senderAccountId, balance);

        new TransferRequester(
                userSpec,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_BAD_REQUEST).build()
        ).post(TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(amount)
                .build());
    }

    @ParameterizedTest
    @MethodSource("negativeAmountsExceedsBalance")
    @DisplayName("Негатив: перевод на чужой счёт — сумма превышает баланс отправителя")
    void transferToForeignExceedsSenderBalance(BigDecimal amount) {
        var userSpec = RequestSpecs.authAsUser(username, password);

        String otherUser = RandomData.getUsername();
        String otherPass = RandomData.getPassword();
        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(CreateUserRequest.builder()
                        .username(otherUser)
                        .password(otherPass)
                        .role(UserRole.USER)
                        .build());
        var otherSpec = RequestSpecs.authAsUser(otherUser, otherPass);
        Long foreignReceiverId = createAccount(otherSpec);

        BigDecimal balance = amount.subtract(MIN_TRANSFER);
        fundAccount(userSpec, senderAccountId, balance);

        new TransferRequester(
                userSpec,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_BAD_REQUEST).build()
        ).post(new TransferRequest(senderAccountId, foreignReceiverId, amount));
    }

    // Негативные payload
    static Stream<Object> invalidTransferPayloads() {
        // amount = null / "" / "abc"
        Map<String, Object> amountNull = new HashMap<>();
        amountNull.put("senderAccountId", 111L);
        amountNull.put("receiverAccountId", 222L);
        amountNull.put("amount", null);

        Map<String, Object> amountEmpty = new HashMap<>();
        amountEmpty.put("senderAccountId", 111L);
        amountEmpty.put("receiverAccountId", 222L);
        amountEmpty.put("amount", "");

        Map<String, Object> amountText = new HashMap<>();
        amountText.put("senderAccountId", 111L);
        amountText.put("receiverAccountId", 222L);
        amountText.put("amount", "abc");

        // отсутствуют ключевые поля
        Map<String, Object> noAmount = new HashMap<>();
        noAmount.put("senderAccountId", 111L);
        noAmount.put("receiverAccountId", 222L);

        Map<String, Object> noSender = new HashMap<>();
        noSender.put("receiverAccountId", 222L);
        noSender.put("amount", 10);

        Map<String, Object> noReceiver = new HashMap<>();
        noReceiver.put("senderAccountId", 111L);
        noReceiver.put("amount", 10);

        // null в id
        Map<String, Object> senderNull = new HashMap<>();
        senderNull.put("senderAccountId", null);
        senderNull.put("receiverAccountId", 222L);
        senderNull.put("amount", 10);

        Map<String, Object> receiverNull = new HashMap<>();
        receiverNull.put("senderAccountId", 111L);
        receiverNull.put("receiverAccountId", null);
        receiverNull.put("amount", 10);

        // сырьё
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
    @DisplayName("Негатив: между своими — кривые payload")
    void transferBetweenOwnInvalidPayloadsReturns400(Object raw) {
        var userSpec = RequestSpecs.authAsUser(username, password);
        fundAccount(userSpec, senderAccountId, new BigDecimal("10"));

        Object body = raw;
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> b = new HashMap<>();
            m.forEach((k, v) -> b.put(String.valueOf(k), v));
            if (b.containsKey("senderAccountId"))   b.put("senderAccountId", senderAccountId);
            if (b.containsKey("receiverAccountId")) b.put("receiverAccountId", receiverAccountId);
            body = b;
        }

        new TransferRequester(
                userSpec,
                new ResponseSpecBuilder().expectStatusCode(HttpStatus.SC_BAD_REQUEST).build()
        ).postRaw(body);
    }
}