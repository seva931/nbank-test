package iteration2test;

import generators.RandomData;
import models.*;
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
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;

// Кейсы для перевода денег: POST /api/v1/accounts/transfer
public class MoneyTransferTest extends BaseTest {

    // Бизнес-константы
    private static final BigDecimal MIN_TRANSFER = new BigDecimal("0.01");
    private static final BigDecimal MAX_TRANSFER = new BigDecimal("10000");
    private static final BigDecimal DEPOSIT_MAX = new BigDecimal("5000");

    private String username;
    private String password;

    @BeforeEach
    @DisplayName("Предусловие: создан пользователь с ролью USER")
    void initUser() {
        username = RandomData.getUsername();
        password = RandomData.getPassword();

        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(CreateUserRequest.builder()
                        .username(username)
                        .password(password)
                        .role(UserRole.USER)
                        .build());
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
        Long senderId = createAccount(userSpec);
        Long receiverId = createAccount(userSpec);

        fundAccount(userSpec, senderId, amount);
        TransferResponse resp =
                given().spec(userSpec)
                        .body(TransferRequest.builder()
                                .senderAccountId(senderId)
                                .receiverAccountId(receiverId)
                                .amount(amount)
                                .build())
                        .when().post("/api/v1/accounts/transfer")
                        .then().statusCode(200)
                        .extract().as(TransferResponse.class);

        softly.assertThat(resp.getMessage()).isEqualTo("Transfer successful");
        softly.assertThat(resp.getSenderAccountId()).isEqualTo(senderId);
        softly.assertThat(resp.getReceiverAccountId()).isEqualTo(receiverId);
        softly.assertThat(resp.getAmount()).isEqualByComparingTo(amount);
    }

    @Test
    @DisplayName("Перевод всей суммы (MAX_TRANSFER) между своими счетами")
    void transferFullBalanceBetweenOwnAccounts() {
        var userSpec = RequestSpecs.authAsUser(username, password);

        Long senderId = createAccount(userSpec);
        Long receiverId = createAccount(userSpec);

        fundAccount(userSpec, senderId, MAX_TRANSFER);

        TransferResponse resp =
                given().spec(userSpec)
                        .body(new TransferRequest(senderId, receiverId, MAX_TRANSFER))
                        .when().post("/api/v1/accounts/transfer")
                        .then().statusCode(200)
                        .extract().as(TransferResponse.class);

        softly.assertThat(resp.getMessage()).isEqualTo("Transfer successful");
        softly.assertThat(resp.getAmount()).isEqualByComparingTo(MAX_TRANSFER);
    }

    @Test
    @DisplayName("Перевод на чужой счёт")
    void transferToForeignAccount() {
        var userSpec = RequestSpecs.authAsUser(username, password);
        Long senderId = createAccount(userSpec);

        String otherUser = RandomData.getUsername();
        String otherPass = RandomData.getPassword();
        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(CreateUserRequest.builder()
                        .username(otherUser).password(otherPass).role(UserRole.USER).build());
        var otherSpec = RequestSpecs.authAsUser(otherUser, otherPass);
        Long foreignReceiverId = createAccount(otherSpec);

        BigDecimal amount = new BigDecimal("2500.50");
        fundAccount(userSpec, senderId, amount);

        TransferResponse resp =
                given().spec(userSpec)
                        .body(new TransferRequest(senderId, foreignReceiverId, amount))
                        .when().post("/api/v1/accounts/transfer")
                        .then().statusCode(200)
                        .extract().as(TransferResponse.class);

        softly.assertThat(resp.getMessage()).isEqualTo("Transfer successful");
        softly.assertThat(resp.getSenderAccountId()).isEqualTo(senderId);
        softly.assertThat(resp.getReceiverAccountId()).isEqualTo(foreignReceiverId);
        softly.assertThat(resp.getAmount()).isEqualByComparingTo(amount);
    }

    //Создаёт счёт и возвращает его id
    private Long createAccount(io.restassured.specification.RequestSpecification userSpec) {
        AccountResponse acc = given().spec(userSpec)
                .when().post("/api/v1/accounts")
                .then().spec(ResponseSpecs.entityWasCreated())
                .extract().as(AccountResponse.class);
        return acc.getId();
    }

    //Одиночный депозит
    private void deposit(io.restassured.specification.RequestSpecification userSpec, Long accountId, BigDecimal amount) {
        given().spec(userSpec)
                .body(new DepositRequest(accountId, amount))
                .when().post("/api/v1/accounts/deposit")
                .then().statusCode(200);
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
        Long senderId   = createAccount(userSpec);
        Long receiverId = createAccount(userSpec);

        BigDecimal need = amount.compareTo(BigDecimal.ZERO) > 0 ? amount.min(MAX_TRANSFER) : new BigDecimal("1");
        fundAccount(userSpec, senderId, need);

        given().spec(userSpec)
                .body(TransferRequest.builder()
                        .senderAccountId(senderId)
                        .receiverAccountId(receiverId)
                        .amount(amount)
                        .build())
                .when().post("/api/v1/accounts/transfer")
                .then().statusCode(400);
    }

    @ParameterizedTest
    @MethodSource("negativeNumericAmounts")
    @DisplayName("Негатив: перевод на чужой счёт — суммы 0/<0/>MAX_TRANSFER")
    void transferToForeignNegativeAmounts(BigDecimal amount) {
        var userSpec = RequestSpecs.authAsUser(username, password);
        Long senderId = createAccount(userSpec);

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
        fundAccount(userSpec, senderId, preload);

        given().spec(userSpec)
                .body(new TransferRequest(senderId, foreignReceiverId, amount))
                .when().post("/api/v1/accounts/transfer")
                .then().statusCode(400);
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
        Long senderId   = createAccount(userSpec);
        Long receiverId = createAccount(userSpec);

        BigDecimal balance = amount.subtract(MIN_TRANSFER);
        fundAccount(userSpec, senderId, balance);

        given().spec(userSpec)
                .body(TransferRequest.builder()
                        .senderAccountId(senderId)
                        .receiverAccountId(receiverId)
                        .amount(amount)
                        .build())
                .when().post("/api/v1/accounts/transfer")
                .then().statusCode(400);
    }

    @ParameterizedTest
    @MethodSource("negativeAmountsExceedsBalance")
    @DisplayName("Негатив: перевод на чужой счёт — сумма превышает баланс отправителя")
    void transferToForeignExceedsSenderBalance(BigDecimal amount) {
        var userSpec = RequestSpecs.authAsUser(username, password);
        Long senderId = createAccount(userSpec);

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
        fundAccount(userSpec, senderId, balance);

        given().spec(userSpec)
                .body(new TransferRequest(senderId, foreignReceiverId, amount))
                .when().post("/api/v1/accounts/transfer")
                .then().statusCode(400);
    }

    //Негативные payload (сокращённый до необходимого набора)
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
        Long senderId   = createAccount(userSpec);
        Long receiverId = createAccount(userSpec);
        fundAccount(userSpec, senderId, new BigDecimal("10"));

        Object body = raw;
        if (raw instanceof Map<?, ?> m) {
            Map<String,Object> b = new HashMap<>();
            m.forEach((k,v) -> b.put(String.valueOf(k), v));
            if (b.containsKey("senderAccountId"))   b.put("senderAccountId", senderId);
            if (b.containsKey("receiverAccountId")) b.put("receiverAccountId", receiverId);
            body = b;
        }

        given().spec(userSpec)
                .body(body)
                .when().post("/api/v1/accounts/transfer")
                .then().statusCode(400);
    }
}
