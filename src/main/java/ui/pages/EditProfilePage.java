package ui.pages;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;

public class EditProfilePage extends BasePage<EditProfilePage> {
    @Override
    public String url() {
        return "/edit-profile";
    }

    // 1) выбрать поле новое имя
    private SelenideElement newNameInput = $("input[placeholder='Enter new name']");

    // 2) нажать на save changes
    private SelenideElement saveChangesButton = $$("button").findBy(text("Save Changes"));

    public EditProfilePage edit(String newName) {
        newNameInput.shouldBe(visible, enabled).setValue(newName);
        saveChangesButton.shouldBe(visible, enabled).click();
        return this;
    }
}
