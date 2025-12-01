package iteration2test.api;

import api.models.AccountResponse;
import api.models.CreateUserRequest;
import api.models.DepositRequest;
import api.models.TransferRequest;
import api.models.TransferResponse;
import api.requests.steps.AccountSteps;
import api.requests.steps.AdminSteps;
import api.requests.steps.DataBaseSteps;
import api.specs.RequestSpecs;
import api.specs.ResponseSpecs;
import common.annotations.FraudCheckMock;
import common.extensions.FraudCheckWireMockExtension;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

import static api.models.comparison.ModelAssertions.assertThatModels;

@ExtendWith(FraudCheckWireMockExtension.class)
public class TransferWithFraudCheckTest extends BaseTest {

    private static final BigDecimal MAX_DEPOSIT = new BigDecimal("5000.00");

    private CreateUserRequest senderUser;
    private CreateUserRequest receiverUser;
    private RequestSpecification senderSpec;
    private Long senderAccountId;
    private Long receiverAccountId;

    @BeforeEach
    @DisplayName("Предусловие: созданы пользователи и счета для сценария с фрод-проверкой")
    void initUsersAndAccounts() {
        // пользователь-отправитель
        senderUser = AdminSteps.createUser();
        senderSpec = RequestSpecs.authAsUser(senderUser.getUsername(), senderUser.getPassword());

        // пользователь-получатель
        receiverUser = AdminSteps.createUser();
        RequestSpecification receiverSpec =
                RequestSpecs.authAsUser(receiverUser.getUsername(), receiverUser.getPassword());

        // счета
        AccountResponse senderAccount =
                AccountSteps.createAccount(senderSpec, ResponseSpecs.entityWasCreated());
        AccountResponse receiverAccount =
                AccountSteps.createAccount(receiverSpec, ResponseSpecs.entityWasCreated());

        senderAccountId = senderAccount.getId();
        receiverAccountId = receiverAccount.getId();
    }

    @Test
    @DisplayName("Позитив: перевод с фрод-проверкой, фрод одобрил, деньги списаны/зачислены")
    @FraudCheckMock(
            status = "SUCCESS",
            decision = "APPROVED",
            riskScore = 0.2,
            reason = "Low risk transaction",
            requiresManualReview = false,
            additionalVerificationRequired = false
    )
    void transferWithFraudCheckApproved() {
        // 1. Генерируем случайную сумму до 5000 и пополняем счёт отправителя
        BigDecimal amount = generateRandomAmountUpToMaxDeposit();
        fundSenderAccount(amount);

        // 2. Перевод с фрод-проверкой
        TransferRequest payload = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(amount)
                .build();

        TransferResponse response = AccountSteps.transfer(
                senderSpec,
                ResponseSpecs.requestReturnsOK(),
                payload
        );

        // 3. Проверка ответа (мэппинг моделей)
        assertThatModels(payload, response).match();

        // 4. Проверка балансов в БД
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

    private BigDecimal generateRandomAmountUpToMaxDeposit() {
        return BigDecimal
                .valueOf(ThreadLocalRandom.current().nextDouble(0.01, MAX_DEPOSIT.doubleValue()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // Пополнение счёта отправителя через /deposit
    private void fundSenderAccount(BigDecimal amount) {
        DepositRequest payload = DepositRequest.builder()
                .accountId(senderAccountId)
                .amount(amount)
                .build();

        AccountSteps.deposit(
                senderSpec,
                ResponseSpecs.requestReturnsOK(),
                payload
        );
    }
}
