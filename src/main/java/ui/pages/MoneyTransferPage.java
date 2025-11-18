package ui.pages;

import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.Alert;
import org.openqa.selenium.Keys;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selenide.*;
import static org.assertj.core.api.Assertions.assertThat;

public class MoneyTransferPage extends BasePage<MoneyTransferPage> {

    @Override
    public String url() {
        return "/transfer";
    }

    // поле выбора счёта отправителя
    private final SelenideElement accountSelect =
            $("select.account-selector");

    // поле для ввода счёта получателя
    private final SelenideElement accountNumberInput =
            $("input[placeholder='Enter recipient account number']");

    // поле для ввода суммы
    private final SelenideElement amountInput =
            $("input[placeholder='Enter amount'][type='number']");

    // чекбокс "подтверждения данных"
    private final SelenideElement confirmCheck =
            $("#confirmCheck");

    // кнопка отправить перевод
    private final SelenideElement sendTransferButton =
            $$("button").findBy(text("Send Transfer"));

    // элементы для повторного перевода

    // кнопка/вкладка "Transfer Again"
    private final SelenideElement transferAgainButton =
            $$("button").findBy(text("Transfer Again"));

    // модальное окно повторного перевода
    private final SelenideElement repeatTransferModal =
            $(".modal.show");

    // заголовок модального окна
    private final SelenideElement repeatTransferModalTitle =
            $(".modal.show .modal-title");

    // селект счёта в модальном окне
    private final SelenideElement repeatTransferAccountSelect =
            $(".modal.show select.form-control");

    // поле суммы в модальном окне
    private final SelenideElement repeatTransferAmountInput =
            $(".modal.show input.form-control[type='number']");

    // чекбокс подтверждения в модальном окне
    private final SelenideElement repeatTransferConfirmCheck =
            $(".modal.show #confirmCheck");

    // кнопка "Send Transfer" в модальном окне
    private final SelenideElement repeatTransferSendButton =
            $(".modal.show button.btn-success");

    // кнопка "Cancel" в модальном окне
    private final SelenideElement repeatTransferCancelButton =
            $(".modal.show button.btn-secondary");

    // общий шаг: выбор счёта отправителя, ввод счёта получателя, суммы, подтверждение и отправка
    public MoneyTransferPage transfer(String senderAccountNumber, String recipientAccountNumber, String amount) {
        accountSelect.shouldBe(visible, enabled)
                .selectOptionContainingText(senderAccountNumber);

        accountNumberInput.shouldBe(visible, enabled)
                .setValue(recipientAccountNumber)
                .shouldHave(value(recipientAccountNumber));

        amountInput.shouldBe(visible, enabled)
                .setValue(amount);

        confirmCheck.shouldBe(visible, enabled)
                .setSelected(true)
                .shouldBe(checked);

        sendTransferButton.shouldBe(visible, enabled)
                .click();

        return this;
    }

    public MoneyTransferPage checkSuccessAlert(int amount,
                                               String recipientAccountNumber,
                                               String successPrefix) {
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText)
                .contains(successPrefix + amount)
                .contains("to account " + recipientAccountNumber);

        alert.accept();
        return this;
    }

    // общий шаг: заполнить форму перевода БЕЗ подтверждения и отправить
    public MoneyTransferPage fillFormAndSendWithoutConfirm(String fromAccountNumber,
                                                           String recipientAccountNumber,
                                                           String amount) {
        accountSelect.shouldBe(visible, enabled)
                .selectOptionContainingText(fromAccountNumber);

        accountNumberInput.shouldBe(visible, enabled)
                .setValue(recipientAccountNumber);

        amountInput.shouldBe(visible, enabled)
                .setValue(amount);

        // чекбокс не трогаем
        sendTransferButton.shouldBe(visible, enabled).click();

        return this;
    }

    public MoneyTransferPage submitWithoutSourceAccount(String recipientAccountNumber, String amount) {
        // не трогаем accountSelect — исходный счёт специально не выбираем

        accountNumberInput.shouldBe(visible, enabled)
                .setValue(recipientAccountNumber)
                .shouldHave(value(recipientAccountNumber));

        amountInput.shouldBe(visible, enabled)
                .setValue(amount);

        confirmCheck.shouldBe(visible, enabled)
                .setSelected(true)
                .shouldBe(checked);

        sendTransferButton.shouldBe(visible, enabled).click();

        return this;
    }

    // попытка перевода без ввода счёта получателя
    public MoneyTransferPage submitWithoutRecipientAccount(String senderAccountNumber,
                                                           String amount) {
        accountSelect.shouldBe(visible, enabled)
                .selectOptionContainingText(senderAccountNumber);

        amountInput.shouldBe(visible, enabled)
                .setValue(amount);

        confirmCheck.shouldBe(visible, enabled)
                .setSelected(true)
                .shouldBe(checked);

        sendTransferButton.shouldBe(visible, enabled)
                .click();

        return this;
    }

    public MoneyTransferPage submitWithEmptyAmount(String senderAccountNumber,
                                                   String recipientAccountNumber) {
        accountSelect.shouldBe(visible, enabled)
                .selectOptionContainingText(senderAccountNumber);

        accountNumberInput.shouldBe(visible, enabled)
                .setValue(recipientAccountNumber)
                .shouldHave(value(recipientAccountNumber));

        // amountInput намеренно не заполняем

        confirmCheck.shouldBe(visible, enabled)
                .setSelected(true)
                .shouldBe(checked);

        sendTransferButton.shouldBe(visible, enabled)
                .click();

        return this;
    }

    // ===== шаги для повторного перевода =====

    // открыть вкладку "Transfer Again"
    public MoneyTransferPage openRepeatTransferTab() {
        transferAgainButton.shouldBe(visible, enabled).click();
        return this;
    }

    // открыть модальное окно повторного перевода по операции с нужной суммой
    public MoneyTransferPage openRepeatTransferModal(int amount) {
        String txType = "TRANSFER_OUT";
        String amtToken = "$" + amount;

        SelenideElement txItem = $$("li.list-group-item")
                .filterBy(text(txType))
                .filterBy(text(amtToken))
                .first()
                .shouldBe(visible);

        txItem.$("button")
                .shouldHave(text("Repeat"))
                .shouldBe(visible, enabled)
                .click();

        repeatTransferModal.shouldBe(visible);
        repeatTransferModalTitle.shouldBe(visible).shouldHave(text("Repeat Transfer"));

        return this;
    }

    // заполнить и подтвердить форму повторного перевода
    public MoneyTransferPage fillRepeatTransferFormAndSubmit(String senderAccountId,
                                                             int amount) {
        String expectedAmount = String.valueOf(amount);

        repeatTransferAccountSelect.shouldBe(visible, enabled)
                .selectOptionByValue(senderAccountId);

        repeatTransferAccountSelect.$("option:checked")
                .shouldHave(value(senderAccountId)); // ОР: выбран нужный счёт

        repeatTransferAmountInput.shouldBe(visible, enabled)
                .shouldHave(value(expectedAmount));

        repeatTransferConfirmCheck.shouldBe(visible, enabled)
                .setSelected(true)
                .shouldBe(checked);

        repeatTransferSendButton.shouldBe(visible, enabled)
                .shouldHave(text("Send Transfer"))
                .click();

        return this;
    }

    // проверка alert повторного перевода (префикс передаём из теста)
    public MoneyTransferPage checkRepeatSuccessAlert(int amount,
                                                     Long accountId,
                                                     String successPrefix) {
        Alert alert = switchTo().alert();
        String alertText = alert.getText();
        String fromId = String.valueOf(accountId);

        assertThat(alertText)
                .contains(successPrefix + amount)
                .contains("from Account " + fromId)
                .contains(" to " + fromId);

        alert.accept();
        return this;
    }

    // заполнить форму повторного перевода с изменением суммы и подтвердить
    public MoneyTransferPage fillRepeatTransferFormWithNewAmountAndSubmit(String senderAccountId,
                                                                          int newAmount) {
        String newAmountStr = String.valueOf(newAmount);

        repeatTransferAccountSelect.shouldBe(visible, enabled)
                .selectOptionByValue(senderAccountId);

        repeatTransferAccountSelect.$("option:checked")
                .shouldHave(value(senderAccountId)); // ОР: выбран нужный счёт

        repeatTransferAmountInput.shouldBe(visible, enabled);
        repeatTransferAmountInput.clear();
        repeatTransferAmountInput.setValue(newAmountStr)
                .shouldHave(value(newAmountStr));

        repeatTransferConfirmCheck.shouldBe(visible, enabled)
                .setSelected(true)
                .shouldBe(checked);

        repeatTransferSendButton.shouldBe(visible, enabled)
                .shouldHave(text("Send Transfer"))
                .click();

        return this;
    }

    // отменить повторный перевод (выбрать счёт, отметить чекбокс и нажать Cancel)
    public MoneyTransferPage cancelRepeatTransfer(String senderAccountId) {
        repeatTransferAccountSelect.shouldBe(visible, enabled)
                .selectOptionByValue(senderAccountId);

        repeatTransferAccountSelect.$("option:checked")
                .shouldHave(value(senderAccountId)); // ОР: выбран нужный счёт

        repeatTransferConfirmCheck.shouldBe(visible, enabled)
                .setSelected(true)
                .shouldBe(checked);

        repeatTransferCancelButton.shouldBe(visible, enabled)
                .shouldHave(text("Cancel"))
                .click();

        repeatTransferModal.should(disappear); // ОР: окно закрыто

        return this;
    }

    public MoneyTransferPage submitRepeatWithoutSourceAccount() {
        repeatTransferConfirmCheck.shouldBe(visible, enabled)
                .setSelected(true)
                .shouldBe(checked);

        repeatTransferSendButton.shouldBe(visible)
                .shouldHave(text("Send Transfer"))
                .shouldBe(disabled);

        return this;
    }

    // закрыть модальное окно повторного перевода
    public MoneyTransferPage closeRepeatTransferModal() {
        repeatTransferCancelButton.shouldBe(visible, enabled)
                .shouldHave(text("Cancel"))
                .click();

        repeatTransferModal.should(disappear);
        return this;
    }

    public MoneyTransferPage submitRepeatWithEmptyAmount(String senderAccountId) {
        repeatTransferAccountSelect.shouldBe(visible, enabled)
                .selectOptionByValue(senderAccountId);

        repeatTransferAccountSelect.$("option:checked")
                .shouldHave(value(senderAccountId)); // ОР: выбран нужный счёт

        repeatTransferAmountInput.shouldBe(visible, enabled);
        repeatTransferAmountInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        repeatTransferAmountInput.sendKeys(Keys.DELETE);
        repeatTransferAmountInput.shouldHave(value(""));

        repeatTransferConfirmCheck.shouldBe(visible, enabled)
                .setSelected(true)
                .shouldBe(checked);

        repeatTransferSendButton.shouldBe(visible, enabled)
                .shouldHave(text("Send Transfer"))
                .click();

        return this;
    }

    // попытка повторного перевода без активации чекбокса "Подтвердите правильность данных"
    public MoneyTransferPage submitRepeatWithoutConfirm(String senderAccountId, int amount) {
        String expectedAmount = String.valueOf(amount);

        repeatTransferAccountSelect.shouldBe(visible, enabled)
                .selectOptionByValue(senderAccountId);

        repeatTransferAccountSelect.$("option:checked")
                .shouldHave(value(senderAccountId)); // ОР: выбран нужный счёт

        repeatTransferAmountInput.shouldBe(visible, enabled)
                .shouldHave(value(expectedAmount));  // сумма как в первой операции

        // чекбокс подтверждения намеренно не трогаем

        repeatTransferSendButton.shouldBe(visible)
                .shouldHave(text("Send Transfer"))
                .shouldBe(disabled);

        return this;
    }

}
