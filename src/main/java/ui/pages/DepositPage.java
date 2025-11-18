package ui.pages;

import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.Alert;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selenide.*;
import static org.assertj.core.api.Assertions.assertThat;

public class DepositPage extends BasePage<DepositPage> {

    @Override
    public String url() {
        return "/deposit";
    }

    // поле выбора счёта
    private final SelenideElement accountSelect =
            $("select.account-selector");

    // поле ввода суммы
    private final SelenideElement amountInput =
            $("input.deposit-input[placeholder='Enter amount'][type='number']");

    // кнопка Deposit
    private final SelenideElement depositButton =
            $$("button").findBy(text("Deposit"));

    // Общий шаг: выбрать счёт и ввести сумму, затем нажать Deposit
    public DepositPage deposit(String accountNumber, String amount) {
        accountSelect.shouldBe(visible, enabled)
                .selectOptionContainingText(accountNumber);

        amountInput.shouldBe(visible, enabled)
                .setValue(amount);

        depositButton.shouldBe(visible, enabled)
                .click();

        return this;
    }

    // Проверка успешного алерта (сообщение + номер счёта)
    public DepositPage checkSuccessAlert(int amount,
                                         String accountNumber,
                                         String successPrefix) {
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText)
                .contains(successPrefix + amount)
                .contains("to account " + accountNumber);

        alert.accept();
        return this;
    }

    // депозит без выбора счёта: вводим только сумму и жмём Deposit
    public DepositPage depositWithoutAccount(String amount) {
        amountInput.shouldBe(visible, enabled)
                .setValue(amount);

        depositButton.shouldBe(visible, enabled)
                .click();

        return this;
    }
}
