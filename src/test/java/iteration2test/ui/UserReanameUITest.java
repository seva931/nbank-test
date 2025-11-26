package iteration2test.ui;

import api.generators.RandomModelGenerator;
import api.models.CreateUserRequest;
import api.models.UpdateProfileRequest;
import api.requests.steps.ProfileSteps;
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
import ui.pages.EditProfilePage;

import static org.assertj.core.api.Assertions.assertThat;

public class UserReanameUITest extends BaseUiTest {

    private String generateValidFullName() {
        UpdateProfileRequest payload = RandomModelGenerator.generate(UpdateProfileRequest.class);
        return payload.getName();
    }

    private RequestSpecification getUserSpec() {
        CreateUserRequest user = SessionStorage.getUser();
        return RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
    }

    private String getProfileName() {
        return ProfileSteps
                .getProfile(getUserSpec(), ResponseSpecs.requestReturnsOK())
                .getName();
    }

    private void updateProfileNameViaApi(String name) {
        UpdateProfileRequest apiPayload = new UpdateProfileRequest(name);
        ProfileSteps.updateProfile(getUserSpec(), ResponseSpecs.requestReturnsOK(), apiPayload);
    }

    @Test
    @UserSession
    @Browsers({"chrome"})
    @Device({"desktop"})
        // Позитивный тест: первичная установка имени
    void setNameFirstTime() {
        String expectedName = generateValidFullName();

        new EditProfilePage()
                .open()
                .edit(expectedName)
                .checkAlertMessageAndAccept(BankAlert.PROFILE_UPDATED_SUCCESSFULLY.getMessage());

        assertThat(getProfileName()).isEqualTo(expectedName);
    }

    @Test
    @UserSession
    @Browsers({"chrome"})
    @Device({"desktop"})
        // Позитивный тест: повторное изменение имени на другое,
        // при уже установленном имени в профиле (через API)
    void updateNameSecondTime() {
        String firstName = generateValidFullName();
        String secondName;
        do {
            secondName = generateValidFullName();
        } while (secondName.equals(firstName));

        updateProfileNameViaApi(firstName);

        new EditProfilePage()
                .open()
                .edit(secondName)
                .checkAlertMessageAndAccept(BankAlert.PROFILE_UPDATED_SUCCESSFULLY.getMessage());

        assertThat(getProfileName()).isEqualTo(secondName);
    }

    @Test
    @UserSession
    @Browsers({"chrome"})
    @Device({"desktop"})
        // Негативный тест: одно слово в имени
    void setNameSingleWord() {
        String invalidName = "Jjdjslsd";

        new EditProfilePage()
                .open()
                .edit(invalidName)
                .checkAlertMessageAndAccept(BankAlert.INVALID_NAME_SINGLE_WORD.getMessage());

        assertThat(getProfileName()).isNull();
    }

    @ParameterizedTest
    @UserSession
    @Browsers({"chrome"})
    @Device({"desktop"})
    @ValueSource(strings = {"", "     "})
        // Негативный тест: пустая строка и только пробелы
    void setNameEmptyOrSpaces(String invalidName) {

        new EditProfilePage()
                .open()
                .edit(invalidName)
                .checkAlertMessageAndAccept(BankAlert.INVALID_NAME_GENERIC.getMessage());

        assertThat(getProfileName()).isNull();
    }

    @Test
    @UserSession
    @Browsers({"chrome"})
    @Device({"desktop"})
        // Негативный тест: новое имя совпадает с текущим (текущее задано через API)
    void setNameSameAsCurrent() {
        String currentName = generateValidFullName();

        updateProfileNameViaApi(currentName);

        new EditProfilePage()
                .open()
                .edit(currentName)
                .checkAlertMessageAndAccept(BankAlert.NAME_SAME_AS_CURRENT.getMessage());

        assertThat(getProfileName()).isEqualTo(currentName);
    }

    @Test
    @UserSession
    @Browsers({"chrome"})
    @Device({"desktop"})
        // Негативный тест: повторное изменение на пробелы
    void updateNameSecondTimeToSpaces() {
        String firstName = generateValidFullName();
        String invalidName = "    ";

        updateProfileNameViaApi(firstName);

        new EditProfilePage()
                .open()
                .edit(invalidName)
                .checkAlertMessageAndAccept(BankAlert.INVALID_NAME_GENERIC.getMessage());

        assertThat(getProfileName()).isEqualTo(firstName);
    }
}