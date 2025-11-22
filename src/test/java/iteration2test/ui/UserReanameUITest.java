package iteration2test.ui;

import api.generators.RandomModelGenerator;
import api.models.CreateUserRequest;
import api.models.UpdateProfileRequest;
import api.requests.steps.AdminSteps;
import api.requests.steps.ProfileSteps;
import api.specs.RequestSpecs;
import api.specs.ResponseSpecs;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ui.pages.EditProfilePage;

import static org.assertj.core.api.Assertions.assertThat;

public class UserReanameUITest extends BaseUiTest {

    private CreateUserRequest user;
    private RequestSpecification userSpec;

    private String generateValidFullName() {
        // генерация имени
        UpdateProfileRequest payload = RandomModelGenerator.generate(UpdateProfileRequest.class);
        return payload.getName();
    }

    @BeforeEach
    void setUpUserAndAuth() {
        // 1) пользователь (API)
        user = AdminSteps.createUser();
        // 2) авторизация для API
        userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        // 3) авторизация в UI через токен
        authAsUser(user);
    }

    @Test
        // Позитивный тест: первичная установка имени
    void setNameFirstTime() {
        String expectedName = generateValidFullName();
        // 1) действие на UI: изменение имени + проверка алерта
        new EditProfilePage()
                .open()
                .edit(expectedName)
                .checkAlertMessageAndAccept(BankAlert.PROFILE_UPDATED_SUCCESSFULLY.getMessage());

        // 2) проверка результата через API
        var customer = ProfileSteps.getProfile(userSpec, ResponseSpecs.requestReturnsOK());
        assertThat(customer.getName()).isEqualTo(expectedName);
    }

    @Test
// Позитивный тест: повторное изменение имени на другое,
// при уже установленном имени в профиле (через API)
    void updateNameSecondTime() {
        String firstName = generateValidFullName();
        String secondName;
        do {
            secondName = generateValidFullName();
        } while (secondName.equals(firstName));

        // 1) первичная установка имени через API
        UpdateProfileRequest apiPayload = new UpdateProfileRequest(firstName);
        ProfileSteps.updateProfile(userSpec, ResponseSpecs.requestReturnsOK(), apiPayload);

        // 2) изменение имени через UI + проверка алерта
        new EditProfilePage()
                .open()
                .edit(secondName)
                .checkAlertMessageAndAccept(BankAlert.PROFILE_UPDATED_SUCCESSFULLY.getMessage());

        // 3) проверка итогового имени через API
        var customer = ProfileSteps.getProfile(userSpec, ResponseSpecs.requestReturnsOK());
        assertThat(customer.getName()).isEqualTo(secondName);
    }

    @Test
// Негативный тест: одно слово в имени
    void setNameSingleWord() {
        String invalidName = "Jjdjslsd";
        // 1) ввести невалидное имя и сохранить → ожидаем алерт с ошибкой
        new EditProfilePage()
                .open()
                .edit(invalidName)
                .checkAlertMessageAndAccept(BankAlert.INVALID_NAME_SINGLE_WORD.getMessage());

        // 2) проверка имени в API — оно не должно установиться
        var customer = ProfileSteps.getProfile(userSpec, ResponseSpecs.requestReturnsOK());
        assertThat(customer.getName()).isEqualTo(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "     "})
// Негативный тест: пустая строка и только пробелы
    void setNameEmptyOrSpaces(String invalidName) {
        // 1) ввести невалидное имя и сохранить → ожидаем алерт с ошибкой
        new EditProfilePage()
                .open()
                .edit(invalidName)
                .checkAlertMessageAndAccept(BankAlert.INVALID_NAME_GENERIC.getMessage());

        // 2) проверка имени в API — оно не должно установиться
        var customer = ProfileSteps.getProfile(userSpec, ResponseSpecs.requestReturnsOK());
        assertThat(customer.getName()).isEqualTo(null);
    }

    @Test
// Негативный тест: новое имя совпадает с текущим (текущее задано через API)
    void setNameSameAsCurrent() {
        String currentName = generateValidFullName();
        // 1) первичная установка имени через API
        UpdateProfileRequest apiPayload = new UpdateProfileRequest(currentName);
        ProfileSteps.updateProfile(userSpec, ResponseSpecs.requestReturnsOK(), apiPayload);

        // 2) попытка установить то же самое имя через UI → ожидаем алерт об ошибке
        new EditProfilePage()
                .open()
                .edit(currentName)
                .checkAlertMessageAndAccept(BankAlert.NAME_SAME_AS_CURRENT.getMessage());

        // 3) проверка имени в API — оно осталось прежним
        var customer = ProfileSteps.getProfile(userSpec, ResponseSpecs.requestReturnsOK());
        assertThat(customer.getName()).isEqualTo(currentName);
    }

    @Test
// Негативный тест: повторное изменение на пробелы
    void updateNameSecondTimeToSpaces() {
        String firstName = generateValidFullName();
        String invalidName = "    ";

        // 1) первичная установка имени через API
        UpdateProfileRequest apiPayload = new UpdateProfileRequest(firstName);
        ProfileSteps.updateProfile(userSpec, ResponseSpecs.requestReturnsOK(), apiPayload);

        // 2) попытка изменить имя на пробелы через UI → ожидаем алерт об ошибке
        new EditProfilePage()
                .open()
                .edit(invalidName)
                .checkAlertMessageAndAccept(BankAlert.INVALID_NAME_GENERIC.getMessage());

        // 3) проверка имени в API — оно осталось прежним
        var customer = ProfileSteps.getProfile(userSpec, ResponseSpecs.requestReturnsOK());
        assertThat(customer.getName()).isEqualTo(firstName);
    }

}
