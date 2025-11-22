package ui.elements;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Condition.*;

public class TransactionItem extends BaseElement {

    public TransactionItem(SelenideElement element) {
        super(element);
    }

    public String getRawText() {
        return element.getText();
    }

    public boolean matches(String type, int amount) {
        String amtToken = "$" + amount;
        String text = getRawText();
        return text.contains(type) && text.contains(amtToken);
    }

    public void clickRepeat() {
        element.$("button")
                .shouldHave(text("Repeat"))
                .shouldBe(visible, enabled)
                .click();
    }
}
