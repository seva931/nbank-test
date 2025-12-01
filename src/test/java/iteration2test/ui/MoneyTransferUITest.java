package iteration2test.ui;

import api.models.AccountResponse;
import api.models.CreateUserRequest;
import api.models.DepositRequest;
import api.models.TransferRequest;
import api.requests.steps.AccountSteps;
import api.specs.RequestSpecs;
import api.specs.ResponseSpecs;
import common.annotations.Browsers;
import common.annotations.Device;
import common.annotations.UserSession;
import common.storage.SessionStorage;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import ui.pages.MoneyTransferPage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class MoneyTransferUITest extends BaseUiTest {

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

    private CreateUserRequest getUser(int index) {
        return SessionStorage.getUser(index);
    }

    private RequestSpecification getUserSpec(int index) {
        CreateUserRequest user = getUser(index);
        return RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
    }

    private AccountResponse createAccountForUser(int userIndex) {
        return AccountSteps.createAccount(
                getUserSpec(userIndex),
                ResponseSpecs.entityWasCreated()
        );
    }

    private void depositToSender(AccountResponse senderAccount, int amount) {
        BigDecimal amountBD = BigDecimal.valueOf(amount)
                .setScale(2, RoundingMode.HALF_UP);

        DepositRequest deposit = DepositRequest.builder()
                .accountId(senderAccount.getId())
                .amount(amountBD)
                .build();

        AccountSteps.deposit(
                getUserSpec(1),                 // было getUserSpec()
                ResponseSpecs.requestReturnsOK(),
                deposit
        );
    }

    private void performFirstTransfer(AccountResponse senderAccount,
                                      AccountResponse recipientAccount,
                                      int amount) {
        BigDecimal amountBD = BigDecimal.valueOf(amount)
                .setScale(2, RoundingMode.HALF_UP);

        TransferRequest firstTransfer = TransferRequest.builder()
                .senderAccountId(senderAccount.getId())
                .receiverAccountId(recipientAccount.getId())
                .amount(amountBD)
                .build();

        AccountSteps.transfer(
                getUserSpec(1),
                ResponseSpecs.requestReturnsOK(),
                firstTransfer
        );
    }

    // подготовка сценария для repeat-тестов:
    // пополнение, первый перевод, открытие страницы и вкладки "Transfer Again"
    private MoneyTransferPage prepareRepeatTransferScenario(int initialAmount,
                                                            int firstTransferAmount,
                                                            AccountResponse senderAccount,
                                                            AccountResponse recipientAccount) {
        depositToSender(senderAccount, initialAmount);
        performFirstTransfer(senderAccount, recipientAccount, firstTransferAmount);
        return new MoneyTransferPage()
                .open()
                .openRepeatTransferTab();
    }

    // базовый helper по номерам
    private void assertBalances(String senderAccountNumber,
                                String recipientAccountNumber,
                                BigDecimal expectedSender,
                                BigDecimal expectedRecipient) {
        AccountResponse updatedSender = AccountSteps.getAccountByNumber(
                getUserSpec(1),
                ResponseSpecs.requestReturnsOK(),
                senderAccountNumber
        );

        AccountResponse updatedRecipient = AccountSteps.getAccountByNumber(
                getUserSpec(2),
                ResponseSpecs.requestReturnsOK(),
                recipientAccountNumber
        );

        assertThat(updatedSender.getBalance()).isEqualByComparingTo(expectedSender);
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(expectedRecipient);
    }

    // удобный helper по AccountResponse
    private void assertBalances(AccountResponse senderAccount,
                                AccountResponse recipientAccount,
                                BigDecimal expectedSender,
                                BigDecimal expectedRecipient) {
        assertBalances(
                senderAccount.getAccountNumber(),
                recipientAccount.getAccountNumber(),
                expectedSender,
                expectedRecipient
        );
    }

    // Позитивный тест: перевод денег со своего счёта на счёт другого пользователя
    @Test
    @UserSession(2)
    @Browsers({"chrome"})
    @Device({"desktop"})
    void transferMoneyToAnotherAccountSuccess() {
        int initialAmount = generateValidAmount();
        int transferAmount = ThreadLocalRandom.current().nextInt(1, initialAmount + 1);

        AccountResponse senderAccount = createAccountForUser(1);
        AccountResponse recipientAccount = createAccountForUser(2);

        depositToSender(senderAccount, initialAmount);

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        new MoneyTransferPage()
                .open()
                .transfer(senderAccountNumber, recipientAccountNumber, String.valueOf(transferAmount))
                .checkSuccessAlert(
                        transferAmount,
                        recipientAccountNumber,
                        BankAlert.TRANSFER_SUCCESS_PREFIX.getMessage()
                );

        assertBalances(
                senderAccount,
                recipientAccount,
                BigDecimal.valueOf(initialAmount - transferAmount),
                BigDecimal.valueOf(transferAmount)
        );
    }

    // Негативный тест: перевод без выбора исходного счёта
    @Test
    @UserSession(2)
    @Browsers({"chrome"})
    @Device({"desktop"})
    void transferWithoutSourceAccount() {
        int initialAmount = generateValidAmount();
        int transferAmount = ThreadLocalRandom.current().nextInt(1, initialAmount + 1);

        AccountResponse senderAccount = createAccountForUser(1);
        AccountResponse recipientAccount = createAccountForUser(2);

        depositToSender(senderAccount, initialAmount);

        String recipientAccountNumber = recipientAccount.getAccountNumber();

        new MoneyTransferPage()
                .open()
                .submitWithoutSourceAccount(
                        recipientAccountNumber,
                        String.valueOf(transferAmount)
                )
                .checkAlertMessageAndAccept(
                        BankAlert.TRANSFER_FIELDS_NOT_CONFIRMED.getMessage()
                );

        assertBalances(
                senderAccount,
                recipientAccount,
                BigDecimal.valueOf(initialAmount),
                BigDecimal.ZERO
        );
    }

    // Негативный тест: перевод без выбора счёта получателя
    @Test
    @UserSession(2)
    @Browsers({"chrome"})
    @Device({"desktop"})
    void transferWithoutRecipientAccount() {
        int initialAmount = generateValidAmount();
        int transferAmount = ThreadLocalRandom.current().nextInt(1, initialAmount + 1);

        AccountResponse senderAccount = createAccountForUser(1);
        AccountResponse recipientAccount = createAccountForUser(2);

        depositToSender(senderAccount, initialAmount);

        String senderAccountNumber = senderAccount.getAccountNumber();

        new MoneyTransferPage()
                .open()
                .submitWithoutRecipientAccount(
                        senderAccountNumber,
                        String.valueOf(transferAmount)
                )
                .checkAlertMessageAndAccept(
                        BankAlert.TRANSFER_FIELDS_NOT_CONFIRMED.getMessage()
                );

        assertBalances(
                senderAccount,
                recipientAccount,
                BigDecimal.valueOf(initialAmount),
                BigDecimal.ZERO
        );
    }

    // Негативный тест: перевод с пустым полем "Сумма"
    @Test
    @UserSession(2)
    @Browsers({"chrome"})
    @Device({"desktop"})
    void transferWithEmptyAmount() {
        int initialAmount = generateValidAmount();

        AccountResponse senderAccount = createAccountForUser(1);
        AccountResponse recipientAccount = createAccountForUser(2);

        depositToSender(senderAccount, initialAmount);

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        new MoneyTransferPage()
                .open()
                .submitWithEmptyAmount(
                        senderAccountNumber,
                        recipientAccountNumber
                )
                .checkAlertMessageAndAccept(
                        BankAlert.TRANSFER_FIELDS_NOT_CONFIRMED.getMessage()
                );

        assertBalances(
                senderAccount,
                recipientAccount,
                BigDecimal.valueOf(initialAmount),
                BigDecimal.ZERO
        );
    }

    // Негативный тест: перевод на несуществующий счёт
    @Test
    @UserSession(2)
    @Browsers({"chrome"})
    @Device({"desktop"})
    void transferToNonexistentAccount() {
        int initialAmount = generateValidAmount();

        AccountResponse senderAccount = createAccountForUser(1);
        AccountResponse recipientAccount = createAccountForUser(2);

        depositToSender(senderAccount, initialAmount);

        String senderAccountNumber = senderAccount.getAccountNumber();
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

        assertBalances(
                senderAccount,
                recipientAccount,
                BigDecimal.valueOf(initialAmount),
                BigDecimal.ZERO
        );
    }

    // Негативный тест: перевод без активации чек-бокса подтверждения
    @Test
    @UserSession(2)
    @Browsers({"chrome"})
    @Device({"desktop"})
    void transferWithoutConfirm() {
        int amount = generateValidAmount();

        AccountResponse senderAccount = createAccountForUser(1);
        AccountResponse recipientAccount = createAccountForUser(2);

        depositToSender(senderAccount, amount);

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

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

        assertBalances(
                senderAccount,
                recipientAccount,
                BigDecimal.valueOf(amount),
                BigDecimal.ZERO
        );
    }

    // Негативный тест: перевод суммы больше доступного баланса - ответ от бека
    @Test
    @UserSession(2)
    @Browsers({"chrome"})
    @Device({"desktop"})
    void transferAmountGreaterThanBalanceError() {
        int initialAmount = generateValidAmount();
        int transferAmount = initialAmount + ThreadLocalRandom.current().nextInt(1, initialAmount + 1);

        AccountResponse senderAccount = createAccountForUser(1);
        AccountResponse recipientAccount = createAccountForUser(2);

        depositToSender(senderAccount, initialAmount);

        String senderAccountNumber = senderAccount.getAccountNumber();
        String recipientAccountNumber = recipientAccount.getAccountNumber();

        new MoneyTransferPage()
                .open()
                .transfer(senderAccountNumber, recipientAccountNumber, String.valueOf(transferAmount))
                .checkAlertMessageAndAccept(
                        BankAlert.TRANSFER_INVALID_INSUFFICIENT_FUNDS.getMessage()
                );

        assertBalances(
                senderAccount,
                recipientAccount,
                BigDecimal.valueOf(initialAmount),
                BigDecimal.ZERO
        );
    }

    // Позитивный тест: повторный перевод денег одному и тому же пользователю с той же суммой
    @Test
    @UserSession(2)
    @Browsers({"chrome"})
    @Device({"desktop"})
    void repeatTransferToSameUserSuccess() {
        int transferAmount = generateAmountForTwoTransfersWithinDepositLimit();
        int initialAmount = transferAmount * 2;

        AccountResponse senderAccount = createAccountForUser(1);
        AccountResponse recipientAccount = createAccountForUser(2);

        MoneyTransferPage moneyTransferPage = prepareRepeatTransferScenario(
                initialAmount,
                transferAmount,
                senderAccount,
                recipientAccount
        );

        moneyTransferPage
                .openRepeatTransferModal(transferAmount)
                .fillRepeatTransferFormAndSubmit(String.valueOf(senderAccount.getId()), transferAmount)
                .checkRepeatSuccessAlert(
                        transferAmount,
                        senderAccount.getId(),
                        BankAlert.TRANSFER_REPEAT_SUCCESS_PREFIX.getMessage()
                );

        assertBalances(
                senderAccount,
                recipientAccount,
                BigDecimal.ZERO,
                BigDecimal.valueOf(transferAmount * 2L)
        );
    }

    // Позитивный тест: повторный перевод денег одному и тому же пользователю с изменением суммы
    @Test
    @UserSession(2)
    @Browsers({"chrome"})
    @Device({"desktop"})
    void repeatTransferToSameUserChangedAmountSuccess() {
        int firstTransferAmount = generateFirstTransferAmountWithinDepositLimit();
        int secondTransferAmount = generateSecondTransferAmountWithinDepositLimit(firstTransferAmount);
        int initialAmount = firstTransferAmount + secondTransferAmount;

        AccountResponse senderAccount = createAccountForUser(1);
        AccountResponse recipientAccount = createAccountForUser(2);

        MoneyTransferPage moneyTransferPage = prepareRepeatTransferScenario(
                initialAmount,
                firstTransferAmount,
                senderAccount,
                recipientAccount
        );

        moneyTransferPage
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

        assertBalances(
                senderAccount,
                recipientAccount,
                BigDecimal.ZERO,
                BigDecimal.valueOf(firstTransferAmount + secondTransferAmount)
        );
    }

    // Позитивный тест: отмена повторного перевода
    @Test
    @UserSession(2)
    @Browsers({"chrome"})
    @Device({"desktop"})
    void cancelRepeatTransfer() {
        int amount = generateAmountForTwoTransfersWithinDepositLimit();
        int initialAmount = amount * 2;

        AccountResponse senderAccount = createAccountForUser(1);
        AccountResponse recipientAccount = createAccountForUser(2);

        MoneyTransferPage moneyTransferPage = prepareRepeatTransferScenario(
                initialAmount,
                amount,
                senderAccount,
                recipientAccount
        );

        moneyTransferPage
                .openRepeatTransferModal(amount)
                .cancelRepeatTransfer(String.valueOf(senderAccount.getId()));

        BigDecimal expected = BigDecimal.valueOf(amount);

        assertBalances(
                senderAccount,
                recipientAccount,
                expected,
                expected
        );
    }

    // Негативный тест: повторный перевод без выбора исходного счёта
    @Test
    @UserSession(2)
    @Browsers({"chrome"})
    @Device({"desktop"})
    void repeatTransferWithoutSourceAccount() {
        int amount = generateAmountForTwoTransfersWithinDepositLimit();
        int initialAmount = amount * 2;

        AccountResponse senderAccount = createAccountForUser(1);
        AccountResponse recipientAccount = createAccountForUser(2);

        MoneyTransferPage moneyTransferPage = prepareRepeatTransferScenario(
                initialAmount,
                amount,
                senderAccount,
                recipientAccount
        );

        moneyTransferPage
                .openRepeatTransferModal(amount)
                .submitRepeatWithoutSourceAccount();

        moneyTransferPage.closeRepeatTransferModal();

        BigDecimal expected = BigDecimal.valueOf(amount);

        assertBalances(
                senderAccount,
                recipientAccount,
                expected,
                expected
        );
    }

    // Негативный тест: повторный перевод с пустым полем "Сумма"
    @Test
    @UserSession(2)
    @Browsers({"chrome"})
    @Device({"desktop"})
    void repeatTransferWithEmptyAmount() {
        int amount = generateAmountForTwoTransfersWithinDepositLimit();
        int initialAmount = amount * 2;

        AccountResponse senderAccount = createAccountForUser(1);
        AccountResponse recipientAccount = createAccountForUser(2);

        MoneyTransferPage moneyTransferPage = prepareRepeatTransferScenario(
                initialAmount,
                amount,
                senderAccount,
                recipientAccount
        );

        moneyTransferPage
                .openRepeatTransferModal(amount)
                .submitRepeatWithEmptyAmount(String.valueOf(senderAccount.getId()))
                .checkAlertMessageAndAccept(BankAlert.TRANSFER_FAILED_RETRY.getMessage());

        BigDecimal expected = BigDecimal.valueOf(amount);

        assertBalances(
                senderAccount,
                recipientAccount,
                expected,
                expected
        );
    }

    // Негативный тест: повторный перевод денег без активированного чек-бокса "Подтвердите правильность данных"
    @Test
    @UserSession(2)
    @Browsers({"chrome"})
    @Device({"desktop"})
    void repeatTransferWithoutConfirm() {
        int amount = generateAmountForTwoTransfersWithinDepositLimit();
        int initialAmount = amount * 2;

        AccountResponse senderAccount = createAccountForUser(1);
        AccountResponse recipientAccount = createAccountForUser(2);

        MoneyTransferPage moneyTransferPage = prepareRepeatTransferScenario(
                initialAmount,
                amount,
                senderAccount,
                recipientAccount
        );

        moneyTransferPage
                .openRepeatTransferModal(amount)
                .submitRepeatWithoutConfirm(String.valueOf(senderAccount.getId()), amount);

        moneyTransferPage.closeRepeatTransferModal();

        BigDecimal expected = BigDecimal.valueOf(amount);

        assertBalances(
                senderAccount,
                recipientAccount,
                expected,
                expected
        );
    }

    // Негативный тест: повторный перевод суммы больше доступного баланса
    @Test
    @UserSession(2)
    @Browsers({"chrome"})
    @Device({"desktop"})
    void repeatTransferOverBalance() {
        int amount = generateAmountForTwoTransfersWithinDepositLimit();
        int initialAmount = amount * 2;

        AccountResponse senderAccount = createAccountForUser(1);
        AccountResponse recipientAccount = createAccountForUser(2);

        MoneyTransferPage moneyTransferPage = prepareRepeatTransferScenario(
                initialAmount,
                amount,
                senderAccount,
                recipientAccount
        );

        int overAmount = amount + ThreadLocalRandom.current().nextInt(1, amount + 1);

        moneyTransferPage
                .openRepeatTransferModal(amount)
                .fillRepeatTransferFormWithNewAmountAndSubmit(
                        String.valueOf(senderAccount.getId()),
                        overAmount
                )
                .checkAlertMessageAndAccept(BankAlert.TRANSFER_FAILED_RETRY.getMessage());

        BigDecimal expected = BigDecimal.valueOf(amount);

        assertBalances(
                senderAccount,
                recipientAccount,
                expected,
                expected
        );
    }
}