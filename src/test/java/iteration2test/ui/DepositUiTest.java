package iteration2test.ui;

import api.models.AccountResponse;
import api.models.CreateUserRequest;
import api.requests.steps.AccountSteps;
import api.specs.RequestSpecs;
import api.specs.ResponseSpecs;
import common.annotations.Browsers;
import common.annotations.Device;
import common.annotations.UserSession;
import common.storage.SessionStorage;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ui.pages.DepositPage;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class DepositUiTest extends BaseUiTest {

    // генерация валидной суммы для депозита
    private int generateValidAmount() {
        return ThreadLocalRandom.current().nextInt(1, 5000);
    }

    private RequestSpecification getUserSpec() {
        CreateUserRequest user = SessionStorage.getUser();
        return RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
    }

    private AccountResponse createAccount() {
        return AccountSteps.createAccount(
                getUserSpec(),
                ResponseSpecs.entityWasCreated()
        );
    }

    private AccountResponse getAccount(String accountNumber) {
        return AccountSteps.getAccountByNumber(
                getUserSpec(),
                ResponseSpecs.requestReturnsOK(),
                accountNumber
        );
    }

    private void assertBalanceEquals(String accountNumber, long expectedAmount) {
        AccountResponse updated = getAccount(accountNumber);
        assertThat(updated.getBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(expectedAmount));
    }

    @Test
    @UserSession
    @Browsers({"chrome"})
    @Device({"desktop"})
        // Позитивный тест: пополнение счёта валидной суммой
    void depositWithValidAmount() {
        int amount = generateValidAmount();

        AccountResponse account = createAccount();
        String accountNumber = account.getAccountNumber();

        new DepositPage()
                .open()
                .deposit(accountNumber, String.valueOf(amount))
                .checkSuccessAlert(
                        amount,
                        accountNumber,
                        BankAlert.DEPOSIT_SUCCESS_PREFIX.getMessage()
                );

        assertBalanceEquals(accountNumber, amount);
    }

    @Test
    @UserSession
    @Browsers({"chrome"})
    @Device({"desktop"})
        // Негативный тест: пополнение без выбора счёта
    void depositWithoutAccount() {
        int amount = generateValidAmount();

        AccountResponse account = createAccount();
        String accountNumber = account.getAccountNumber();

        new DepositPage()
                .open()
                .depositWithoutAccount(String.valueOf(amount))
                .checkAlertMessageAndAccept(
                        BankAlert.DEPOSIT_ACCOUNT_NOT_SELECTED.getMessage()
                );

        assertBalanceEquals(accountNumber, 0);
    }

    @Test
    @UserSession
    @Browsers({"chrome"})
    @Device({"desktop"})
        // Негативный тест: сумма больше допустимого значения
    void depositAmountOverLimit() {
        int amount = 5001;

        AccountResponse account = createAccount();
        String accountNumber = account.getAccountNumber();

        new DepositPage()
                .open()
                .deposit(accountNumber, String.valueOf(amount))
                .checkAlertMessageAndAccept(
                        BankAlert.DEPOSIT_TOO_MUCH.getMessage()
                );

        assertBalanceEquals(accountNumber, 0);
    }

    @ParameterizedTest
    @UserSession
    @Browsers({"chrome"})
    @Device({"desktop"})
    @ValueSource(strings = {"0", "", "-101"})
        // Негативный тест: невалидные значения суммы (0, пусто, отрицательное)
    void depositWithInvalidAmount(String invalidAmount) {
        AccountResponse account = createAccount();
        String accountNumber = account.getAccountNumber();

        new DepositPage()
                .open()
                .deposit(accountNumber, invalidAmount)
                .checkAlertMessageAndAccept(
                        BankAlert.DEPOSIT_INVALID_AMOUNT.getMessage()
                );

        assertBalanceEquals(accountNumber, 0);
    }
}