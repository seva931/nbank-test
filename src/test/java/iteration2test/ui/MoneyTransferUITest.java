package iteration2test.ui;

import api.models.AccountResponse;
import api.models.CreateUserRequest;
import api.models.DepositRequest;
import api.models.TransferRequest;
import api.requests.steps.AccountSteps;
import api.requests.steps.AdminSteps;
import api.specs.RequestSpecs;
import api.specs.ResponseSpecs;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Alert;
import ui.pages.MoneyTransferPage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

import static com.codeborne.selenide.Selenide.switchTo;
import static org.assertj.core.api.Assertions.assertThat;

public class MoneyTransferUITest extends BaseUiTest {
    private CreateUserRequest user;
    private CreateUserRequest user2;
    private RequestSpecification userSpec;
    private RequestSpecification userSpec2;
    private AccountResponse senderAccount;
    private AccountResponse recipientAccount;

    // генерация валидной суммы
    private int generateValidAmount() {
        return ThreadLocalRandom.current().nextInt(1, 5000);
    }

    // сумма одного перевода для двух переводов при депозите ≤ 5000 (amount * 2 ≤ 5000)
    private int generateAmountForTwoTransfersWithinDepositLimit() {
        return ThreadLocalRandom.current().nextInt(1, 2501);
    }

    // первая сумма перевода при повторном переводе с изменением суммы
    private int generateFirstTransferAmountWithinDepositLimit() {
        return ThreadLocalRandom.current().nextInt(2, 5000);
    }

    // вторая сумма перевода: меньше первой и так, чтобы first + second ≤ 5000
    private int generateSecondTransferAmountWithinDepositLimit(int firstTransferAmount) {
        int maxSecond = Math.min(firstTransferAmount - 1, 5000 - firstTransferAmount);
        return ThreadLocalRandom.current().nextInt(1, maxSecond + 1);
    }

    @BeforeEach
    void setUpUsersAndAccounts() {
        // 1) пользователи (API)
        user = AdminSteps.createUser();
        user2 = AdminSteps.createUser();

        // 2) авторизация для API
        userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        userSpec2 = RequestSpecs.authAsUser(user2.getUsername(), user2.getPassword());

        // 3) создание счетов (API)
        senderAccount = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        recipientAccount = AccountSteps.createAccount(userSpec2, ResponseSpecs.entityWasCreated());
    }

    @Test
        // Позитивный тест: перевод денег со своего счёта на счёт другого пользователя
    void transferMoneyToAnotherAccountSuccess() {
        // сумма, на которую пополним счёт отправителя
        int initialAmount = generateValidAmount();
        // сумма перевода (не больше остатка на счёте)
        int transferAmount = ThreadLocalRandom.current().nextInt(1, initialAmount + 1);

        // 1) пополнить баланс отправителя (API) на initialAmount
        DepositRequest depositPayload = DepositRequest.builder()
                .id(senderAccount.getId())
                .balance(BigDecimal.valueOf(initialAmount).setScale(2, RoundingMode.HALF_UP))
                .build();

        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                depositPayload
        );

        // 2) авторизация в UI через токен
        authAsUser(user);

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        // 3) перевод денег через UI + проверка алерта
        new MoneyTransferPage()
                .open()
                .transfer(senderAccountNumber, recipientAccountNumber, String.valueOf(transferAmount))
                .checkSuccessAlert(
                        transferAmount,
                        recipientAccountNumber,
                        BankAlert.TRANSFER_SUCCESS_PREFIX.getMessage()
                );

        // 4) API: баланс отправителя = initialAmount - transferAmount
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );

        // 5) API: баланс получателя = transferAmount
        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                userSpec2,
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(initialAmount - transferAmount));

        assertThat(updatedRecipient.getBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(transferAmount));
    }

    @Test
        // Негативный тест: перевод без выбора исходного счёта
    void transferWithoutSourceAccount() {
        // сумма, на которую пополним счёт отправителя
        int initialAmount = generateValidAmount();
        // сумма перевода (не больше остатка на счёте)
        int transferAmount = ThreadLocalRandom.current().nextInt(1, initialAmount + 1);

        // 1) пополнить баланс отправителя (API) на initialAmount
        DepositRequest depositPayload = DepositRequest.builder()
                .id(senderAccount.getId())
                .balance(BigDecimal.valueOf(initialAmount).setScale(2, RoundingMode.HALF_UP))
                .build();

        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                depositPayload
        );

        // 2) авторизация в UI через токен
        authAsUser(user);

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        // 3) попытка перевода без выбора исходного счёта
        new MoneyTransferPage()
                .open()
                .submitWithoutSourceAccount(
                        recipientAccountNumber,
                        String.valueOf(transferAmount)
                )
                .checkAlertMessageAndAccept(
                        BankAlert.TRANSFER_FIELDS_NOT_CONFIRMED.getMessage()
                );

        // 4) API: балансы без изменений
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );
        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                userSpec2,
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(initialAmount));
        assertThat(updatedRecipient.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
        // Негативный тест: перевод без выбора счёта получателя
    void transferWithoutRecipientAccount() {
        // сумма, на которую пополним счёт отправителя
        int initialAmount = generateValidAmount();
        // сумма перевода (не больше остатка на счёте)
        int transferAmount = ThreadLocalRandom.current().nextInt(1, initialAmount + 1);

        // 1) пополнить баланс отправителя (API) на initialAmount
        DepositRequest depositPayload = DepositRequest.builder()
                .id(senderAccount.getId())
                .balance(BigDecimal.valueOf(initialAmount)
                        .setScale(2, RoundingMode.HALF_UP))
                .build();

        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                depositPayload
        );

        // 2) авторизация в UI через токен
        authAsUser(user);

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        // 3) попытка перевода без ввода счёта получателя
        new MoneyTransferPage()
                .open()
                .submitWithoutRecipientAccount(
                        senderAccountNumber,
                        String.valueOf(transferAmount)
                )
                .checkAlertMessageAndAccept(
                        BankAlert.TRANSFER_FIELDS_NOT_CONFIRMED.getMessage()
                );

        // 4) API: балансы без изменений
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );
        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                userSpec2,
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(initialAmount));
        assertThat(updatedRecipient.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
        // Негативный тест: перевод с пустым полем "Сумма"
    void transferWithEmptyAmount() {
        // сумма, на которую пополним счёт отправителя
        int initialAmount = generateValidAmount();

        // 1) пополнить баланс отправителя (API) на initialAmount
        DepositRequest depositPayload = DepositRequest.builder()
                .id(senderAccount.getId())
                .balance(BigDecimal.valueOf(initialAmount).setScale(2, RoundingMode.HALF_UP))
                .build();

        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                depositPayload
        );

        // 2) авторизация в UI через токен
        authAsUser(user);

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        // 3) попытка перевода с пустым полем "Сумма"
        new MoneyTransferPage()
                .open()
                .submitWithEmptyAmount(
                        senderAccountNumber,
                        recipientAccountNumber
                )
                .checkAlertMessageAndAccept(
                        BankAlert.TRANSFER_FIELDS_NOT_CONFIRMED.getMessage()
                );

        // 4) API: балансы без изменений
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );
        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                userSpec2,
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(initialAmount));
        assertThat(updatedRecipient.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
        // Негативный тест: перевод на несуществующий счёт
    void transferToNonexistentAccount() {
        // сумма, на которую пополним счёт отправителя
        int initialAmount = generateValidAmount();

        // 1) пополнить баланс отправителя (API) на initialAmount
        DepositRequest depositPayload = DepositRequest.builder()
                .id(senderAccount.getId())
                .balance(BigDecimal.valueOf(initialAmount).setScale(2, RoundingMode.HALF_UP))
                .build();

        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                depositPayload
        );

        // 2) авторизация в UI через токен
        authAsUser(user);

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        // 3) попытка перевода на несуществующий счёт
        String nonexistentAccountNumber = "ACC1000200";

        new MoneyTransferPage()
                .open()
                .transfer(
                        senderAccountNumber,
                        nonexistentAccountNumber,
                        String.valueOf(initialAmount)
                )
                .checkAlertMessageAndAccept(
                        BankAlert.TRANSFER_ACCOUNT_NOT_FOUND.getMessage()
                );

        // 4) API: балансы без изменений
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );
        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                userSpec2,
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(initialAmount));
        assertThat(updatedRecipient.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
        // Негативный тест: перевод без активации чек-бокса подтверждения
    void transferWithoutConfirm() {
        // сумма перевода
        int amount = generateValidAmount();

        // 1) пополнить баланс отправителя (API) на сумму перевода
        var deposit = DepositRequest.builder()
                .id(senderAccount.getId())
                .balance(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP))
                .build();

        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                deposit
        );

        // 2) авторизация в UI через токен
        authAsUser(user);

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        // 3) перевод без подтверждения через PageObject + проверка алерта
        new MoneyTransferPage()
                .open()
                .fillFormAndSendWithoutConfirm(
                        senderAccountNumber,
                        recipientAccountNumber,
                        String.valueOf(amount)
                )
                .checkAlertMessageAndAccept(
                        BankAlert.TRANSFER_FIELDS_NOT_CONFIRMED.getMessage()
                );

        // 4) API: балансы без изменений
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );
        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                userSpec2,
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(amount));
        assertThat(updatedRecipient.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
        // Негативный тест: перевод суммы больше доступного баланса - ответ от бека
    void transferAmountGreaterThanBalanceError() {
        // сумма, на которую пополним счёт отправителя
        int initialAmount = generateValidAmount();
        // сумма перевода (строго больше остатка на счёте)
        int transferAmount = initialAmount + ThreadLocalRandom.current().nextInt(1, initialAmount + 1);

        // 1) пополнить баланс отправителя (API) на initialAmount
        DepositRequest depositPayload = DepositRequest.builder()
                .id(senderAccount.getId())
                .balance(BigDecimal.valueOf(initialAmount).setScale(2, RoundingMode.HALF_UP))
                .build();

        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                depositPayload
        );

        // 2) авторизация в UI через токен
        authAsUser(user);

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        // 3) попытка перевода суммы больше баланса через UI + проверка алерта об ошибке
        new MoneyTransferPage()
                .open()
                .transfer(senderAccountNumber, recipientAccountNumber, String.valueOf(transferAmount))
                .checkAlertMessageAndAccept(
                        BankAlert.TRANSFER_INVALID_INSUFFICIENT_FUNDS.getMessage()
                );

        // 4) API: баланс отправителя не изменился и равен initialAmount
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );

        // 5) API: баланс получателя = 0
        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                userSpec2,
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(initialAmount));

        assertThat(updatedRecipient.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
        // Позитивный тест: повторный перевод денег одному и тому же пользователю с той же суммой
    void repeatTransferToSameUserSuccess() {
        // сумма одного перевода
        int transferAmount = generateAmountForTwoTransfersWithinDepositLimit();
        // общий баланс отправителя для двух одинаковых переводов
        int initialAmount = transferAmount * 2;

        // 1) пополнить баланс отправителя (API) на initialAmount
        DepositRequest deposit = DepositRequest.builder()
                .id(senderAccount.getId())
                .balance(BigDecimal.valueOf(initialAmount).setScale(2, RoundingMode.HALF_UP))
                .build();

        AccountSteps.deposit(userSpec, ResponseSpecs.requestReturnsOK(), deposit);

        // 1.1 подготовить сумму для первого перевода
        BigDecimal transferAmountBD = BigDecimal.valueOf(transferAmount)
                .setScale(2, RoundingMode.HALF_UP);

        // 1.2 собрать запрос первого перевода
        TransferRequest firstTransfer = TransferRequest.builder()
                .senderAccountId(senderAccount.getId())
                .receiverAccountId(recipientAccount.getId())
                .amount(transferAmountBD)
                .build();

        // 1.3 выполнить первый перевод (ожидаем 200 OK)
        AccountSteps.transfer(userSpec, ResponseSpecs.requestReturnsOK(), firstTransfer);

        // 2) авторизация в UI через токен
        authAsUser(user);

        MoneyTransferPage moneyTransferPage = new MoneyTransferPage().open();

        // 3) повторный перевод через UI
        moneyTransferPage
                .openRepeatTransferTab()
                .openRepeatTransferModal(transferAmount)
                .fillRepeatTransferFormAndSubmit(String.valueOf(senderAccount.getId()), transferAmount)
                .checkRepeatSuccessAlert(
                        transferAmount,
                        senderAccount.getId(),
                        BankAlert.TRANSFER_REPEAT_SUCCESS_PREFIX.getMessage()
                );

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        // 4) API: финальные балансы — у отправителя 0, у получателя transferAmount * 2
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );

        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                userSpec2,
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(updatedRecipient.getBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(transferAmount * 2L));
    }

    @Test
// Позитивный тест: повторный перевод денег одному и тому же пользователю с изменением суммы
    void repeatTransferToSameUserChangedAmountSuccess() {
        // сумма первого перевода (минимум 2, чтобы можно было изменить сумму)
        int firstTransferAmount = generateFirstTransferAmountWithinDepositLimit();
        // сумма повторного перевода (меньше первой и так, чтобы first + second ≤ 5000)
        int secondTransferAmount = generateSecondTransferAmountWithinDepositLimit(firstTransferAmount);
        // общий баланс отправителя: хватает на оба перевода
        int initialAmount = firstTransferAmount + secondTransferAmount;

        // 1) пополнить баланс отправителя (API) на initialAmount
        DepositRequest deposit = DepositRequest.builder()
                .id(senderAccount.getId())
                .balance(BigDecimal.valueOf(initialAmount).setScale(2, RoundingMode.HALF_UP))
                .build();

        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                deposit
        );

        // 1.1 подготовить сумму для первого перевода
        BigDecimal firstTransferAmountBD = BigDecimal.valueOf(firstTransferAmount)
                .setScale(2, RoundingMode.HALF_UP);

        // 1.2 собрать запрос первого перевода
        TransferRequest firstTransfer = TransferRequest.builder()
                .senderAccountId(senderAccount.getId())
                .receiverAccountId(recipientAccount.getId())
                .amount(firstTransferAmountBD)
                .build();

        // 1.3 выполнить первый перевод (ожидаем 200 OK)
        AccountSteps.transfer(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                firstTransfer
        );

        // 2) авторизация в UI через токен
        authAsUser(user);

        MoneyTransferPage moneyTransferPage = new MoneyTransferPage().open();

        // 3) повторный перевод через UI с изменением суммы
        moneyTransferPage
                .openRepeatTransferTab()
                .openRepeatTransferModal(firstTransferAmount)
                .fillRepeatTransferFormWithNewAmountAndSubmit(
                        String.valueOf(senderAccount.getId()),
                        secondTransferAmount
                )
                .checkRepeatSuccessAlert(
                        secondTransferAmount,
                        senderAccount.getId(),
                        BankAlert.TRANSFER_REPEAT_SUCCESS_PREFIX.getMessage()
                );

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        // 4) API: финальные балансы — у отправителя 0, у получателя firstTransferAmount + secondTransferAmount
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );

        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                userSpec2,
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(updatedRecipient.getBalance())
                .isEqualByComparingTo(
                        BigDecimal.valueOf(firstTransferAmount + secondTransferAmount)
                );
    }

    @Test
// Позитивный тест: отмена повторного перевода
    void cancelRepeatTransfer() {
        // сумма первого перевода
        int amount = generateAmountForTwoTransfersWithinDepositLimit();
        // общий баланс отправителя для двух одинаковых переводов
        int initialAmount = amount * 2;

        // 1) пополнить баланс отправителя (API) на initialAmount
        DepositRequest deposit = DepositRequest.builder()
                .id(senderAccount.getId())
                .balance(BigDecimal.valueOf(initialAmount).setScale(2, RoundingMode.HALF_UP))
                .build();

        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                deposit
        );

        // 1.1 подготовить сумму для первого перевода
        BigDecimal amountBD = BigDecimal.valueOf(amount)
                .setScale(2, RoundingMode.HALF_UP);

        // 1.2 собрать запрос первого перевода
        TransferRequest firstTransfer = TransferRequest.builder()
                .senderAccountId(senderAccount.getId())
                .receiverAccountId(recipientAccount.getId())
                .amount(amountBD)
                .build();

        // 1.3 выполнить первый перевод (ожидаем 200 OK)
        AccountSteps.transfer(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                firstTransfer
        );

        // 2) авторизация в UI через токен
        authAsUser(user);

        MoneyTransferPage moneyTransferPage = new MoneyTransferPage().open();

        // 3) открыть вкладку повторного перевода и отменить повтор
        moneyTransferPage
                .openRepeatTransferTab()
                .openRepeatTransferModal(amount)
                .cancelRepeatTransfer(String.valueOf(senderAccount.getId()));

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        // 4) API: финальные балансы — у отправителя amount, у получателя amount
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );

        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                userSpec2,
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        BigDecimal expected = BigDecimal.valueOf(amount);

        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(expected);

        assertThat(updatedRecipient.getBalance())
                .isEqualByComparingTo(expected);
    }

    @Test
// Негативный тест: повторный перевод без выбора исходного счёта
    void repeatTransferWithoutSourceAccount() {
        // сумма первого перевода
        int amount = generateAmountForTwoTransfersWithinDepositLimit();
        // общий баланс отправителя для двух одинаковых переводов
        int initialAmount = amount * 2;

        // 1) пополнить баланс отправителя (API) на initialAmount
        DepositRequest deposit = DepositRequest.builder()
                .id(senderAccount.getId())
                .balance(BigDecimal.valueOf(initialAmount).setScale(2, RoundingMode.HALF_UP))
                .build();

        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                deposit
        );

        // 1.1 подготовить сумму
        BigDecimal amountBD = BigDecimal.valueOf(amount)
                .setScale(2, RoundingMode.HALF_UP);

        // 1.2 собрать запрос первого перевода
        TransferRequest firstTransfer = TransferRequest.builder()
                .senderAccountId(senderAccount.getId())
                .receiverAccountId(recipientAccount.getId())
                .amount(amountBD)
                .build();

        // 1.3 выполнить первый перевод (ожидаем 200 OK)
        AccountSteps.transfer(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                firstTransfer
        );

        // 2) авторизация в UI через токен
        authAsUser(user);

        MoneyTransferPage moneyTransferPage = new MoneyTransferPage().open();

        // 3) попытка повторного перевода без выбора исходного счёта
        moneyTransferPage
                .openRepeatTransferTab()
                .openRepeatTransferModal(amount)
                .submitRepeatWithoutSourceAccount();

        // 4) закрыть окно повторного перевода
        moneyTransferPage.closeRepeatTransferModal();

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        // 5) API: финальные балансы — у отправителя amount, у получателя amount
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );

        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                userSpec2,
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        BigDecimal expected = BigDecimal.valueOf(amount);

        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(expected);
        assertThat(updatedRecipient.getBalance())
                .isEqualByComparingTo(expected);
    }

    @Test
// Негативный тест: повторный перевод с пустым полем "Сумма"
    void repeatTransferWithEmptyAmount() {
        // сумма первого перевода
        int amount = generateAmountForTwoTransfersWithinDepositLimit();
        // общий баланс отправителя для двух одинаковых переводов
        int initialAmount = amount * 2;

        // 1) пополнить баланс отправителя (API) на initialAmount
        DepositRequest deposit = DepositRequest.builder()
                .id(senderAccount.getId())
                .balance(BigDecimal.valueOf(initialAmount).setScale(2, RoundingMode.HALF_UP))
                .build();

        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                deposit
        );

        // 1.1 подготовить сумму
        BigDecimal amountBD = BigDecimal.valueOf(amount)
                .setScale(2, RoundingMode.HALF_UP);

        // 1.2 собрать запрос первого перевода
        TransferRequest firstTransfer = TransferRequest.builder()
                .senderAccountId(senderAccount.getId())
                .receiverAccountId(recipientAccount.getId())
                .amount(amountBD)
                .build();

        // 1.3 выполнить первый перевод (ожидаем 200 OK)
        AccountSteps.transfer(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                firstTransfer
        );

        // 2) авторизация в UI через токен
        authAsUser(user);

        MoneyTransferPage moneyTransferPage = new MoneyTransferPage().open();

        // 3) попытка повторного перевода с пустой суммой
        moneyTransferPage
                .openRepeatTransferTab()
                .openRepeatTransferModal(amount)
                .submitRepeatWithEmptyAmount(String.valueOf(senderAccount.getId()));

        // 4) ОР: появился alert с текстом "Transfer failed: Please try again"
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText)
                .contains(BankAlert.TRANSFER_FAILED_RETRY.getMessage());
        alert.accept();

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        // 5) API: финальные балансы — у отправителя amount, у получателя amount
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );

        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                userSpec2,
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        BigDecimal expected = BigDecimal.valueOf(amount);

        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(expected);
        assertThat(updatedRecipient.getBalance())
                .isEqualByComparingTo(expected);
    }

    @Test
// Негативный тест: повторный перевод денег без активированного чек-бокса "Подтвердите правильность данных"
    void repeatTransferWithoutConfirm() {
        // сумма первого перевода
        int amount = generateAmountForTwoTransfersWithinDepositLimit();
        // общий баланс отправителя для двух одинаковых переводов
        int initialAmount = amount * 2;

        // 1) пополнить баланс отправителя (API) на initialAmount
        DepositRequest deposit = DepositRequest.builder()
                .id(senderAccount.getId())
                .balance(BigDecimal.valueOf(initialAmount).setScale(2, RoundingMode.HALF_UP))
                .build();

        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                deposit
        );

        // 1.1 подготовить сумму
        BigDecimal amountBD = BigDecimal.valueOf(amount)
                .setScale(2, RoundingMode.HALF_UP);

        // 1.2 собрать запрос первого перевода
        TransferRequest firstTransfer = TransferRequest.builder()
                .senderAccountId(senderAccount.getId())
                .receiverAccountId(recipientAccount.getId())
                .amount(amountBD)
                .build();

        // 1.3 выполнить первый перевод (ожидаем 200 OK)
        AccountSteps.transfer(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                firstTransfer
        );

        // 2) авторизация в UI через токен
        authAsUser(user);

        MoneyTransferPage moneyTransferPage = new MoneyTransferPage().open();

        // 3) попытка повторного перевода без чекбокса подтверждения
        moneyTransferPage
                .openRepeatTransferTab()
                .openRepeatTransferModal(amount)
                .submitRepeatWithoutConfirm(String.valueOf(senderAccount.getId()), amount);

        // 4) закрыть окно повторного перевода
        moneyTransferPage.closeRepeatTransferModal();

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        // 5) API: финальные балансы — у отправителя amount, у получателя amount
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );

        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                userSpec2,
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        BigDecimal expected = BigDecimal.valueOf(amount);

        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(expected);
        assertThat(updatedRecipient.getBalance())
                .isEqualByComparingTo(expected);
    }

    @Test
// Негативный тест: повторный перевод суммы больше доступного баланса
    void repeatTransferOverBalance() {
        // сумма первого перевода
        int amount = generateAmountForTwoTransfersWithinDepositLimit();
        // общий баланс отправителя для двух переводов
        int initialAmount = amount * 2;

        // 1) пополнить баланс отправителя (API) на initialAmount
        DepositRequest deposit = DepositRequest.builder()
                .id(senderAccount.getId())
                .balance(BigDecimal.valueOf(initialAmount).setScale(2, RoundingMode.HALF_UP))
                .build();

        AccountSteps.deposit(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                deposit
        );

        // 1.1 подготовить сумму для первого перевода
        BigDecimal amountBD = BigDecimal.valueOf(amount)
                .setScale(2, RoundingMode.HALF_UP);

        // 1.2 собрать запрос первого перевода
        TransferRequest firstTransfer = TransferRequest.builder()
                .senderAccountId(senderAccount.getId())
                .receiverAccountId(recipientAccount.getId())
                .amount(amountBD)
                .build();

        // 1.3 выполнить первый перевод (ожидаем 200 OK)
        AccountSteps.transfer(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                firstTransfer
        );

        // 2) авторизация в UI через токен
        authAsUser(user);

        MoneyTransferPage moneyTransferPage = new MoneyTransferPage().open();

        // 3) подготовить сумму повторного перевода больше доступного баланса (amount)
        int overAmount = amount + ThreadLocalRandom.current().nextInt(1, amount + 1);

        // 4) попытка повторного перевода суммы > баланса
        moneyTransferPage
                .openRepeatTransferTab()
                .openRepeatTransferModal(amount)
                .fillRepeatTransferFormWithNewAmountAndSubmit(
                        String.valueOf(senderAccount.getId()),
                        overAmount
                );

        // 5) ОР: появился alert об ошибке
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        assertThat(alertText)
                .contains(BankAlert.TRANSFER_FAILED_RETRY.getMessage());
        alert.accept();

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        // 6) API: финальные балансы — у отправителя amount, у получателя amount
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );

        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                userSpec2,
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        BigDecimal expected = BigDecimal.valueOf(amount);

        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(expected);
        assertThat(updatedRecipient.getBalance())
                .isEqualByComparingTo(expected);
    }

}