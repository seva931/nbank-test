package iteration2test.ui;

import api.models.AccountResponse;
import api.models.CreateUserRequest;
import api.requests.steps.AccountSteps;
import api.requests.steps.AdminSteps;
import api.specs.RequestSpecs;
import api.specs.ResponseSpecs;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ui.pages.DepositPage;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class DepositUiTest extends BaseUiTest {
    private CreateUserRequest user;
    private RequestSpecification userSpec;
    private AccountResponse account;
    private String accountNumber;

    // генерация валидной суммы для депозита
    private int generateValidAmount() {
        return ThreadLocalRandom.current().nextInt(1, 5000);
    }

    @BeforeEach
    void setUpUserAccountAndAuth() {
        // 1) пользователь (API)
        user = AdminSteps.createUser();
        // 2) авторизация для API
        userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        // 3) создание счёта (API)
        account = AccountSteps.createAccount(userSpec, ResponseSpecs.entityWasCreated());
        accountNumber = account.getAccountNumber();
        // 4) авторизация в UI через токен
        authAsUser(user);
    }

    @Test
// Позитивный тест: пополнение счёта валидной суммой
    void depositWithValidAmount() {
        int amount = generateValidAmount();
        // 1) пополнение счета через UI + проверка алерта
        new DepositPage()
                .open()
                .deposit(accountNumber, String.valueOf(amount))
                .checkSuccessAlert(
                        amount,
                        accountNumber,
                        BankAlert.DEPOSIT_SUCCESS_PREFIX.getMessage()
                );

        // 2) API: баланс счёта стал равен amount
        AccountResponse updated = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                accountNumber
        );
        assertThat(updated.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(amount));

    }

    @Test
        // Негативный тест: пополнение без выбора счёта
    void depositWithoutAccount() {
        int amount = generateValidAmount();
        // 1) ввести сумму без выбора счёта и нажать Deposit
        new DepositPage()
                .open()
                // отдельный метод в DepositPage, который не трогает селект счёта
                .depositWithoutAccount(String.valueOf(amount))
                .checkAlertMessageAndAccept(
                        BankAlert.DEPOSIT_ACCOUNT_NOT_SELECTED.getMessage()
                );

        // 2) API: баланс счёта не изменился (остался 0)
        AccountResponse updated = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                accountNumber
        );
        assertThat(updated.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
// Негативный тест: сумма больше допустимого значения
    void depositAmountOverLimit() {
        int amount = 5001;
        // 1) попытка пополнить счёт на сумму больше лимита + ожидаем алерт с ошибкой
        new DepositPage()
                .open()
                .deposit(accountNumber, String.valueOf(amount))
                .checkAlertMessageAndAccept(
                        BankAlert.DEPOSIT_TOO_MUCH.getMessage()
                );

        // 2) API: баланс счёта не изменился (остался 0)
        AccountResponse updated = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                accountNumber
        );
        assertThat(updated.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "", "-101"})
// Негативный тест: невалидные значения суммы (0, пусто, отрицательное)
    void depositWithInvalidAmount(String invalidAmount) {
        // 1) попытка пополнения с невалидной суммой
        new DepositPage()
                .open()
                .deposit(accountNumber, invalidAmount)
                .checkAlertMessageAndAccept(
                        BankAlert.DEPOSIT_INVALID_AMOUNT.getMessage()
                );

        // 2) API: баланс счёта не изменился (остался 0)
        AccountResponse updated = AccountSteps.getAccountByNumber(
                userSpec,
                ResponseSpecs.requestReturnsOK(),
                accountNumber
        );
        assertThat(updated.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
