package iteration2test.ui;

import lombok.Getter;

@Getter
public enum BankAlert {
    // Профиль
    PROFILE_UPDATED_SUCCESSFULLY("Name updated successfully!"),
    INVALID_NAME_SINGLE_WORD("Name must contain two words with letters only"),
    INVALID_NAME_GENERIC("Please enter a valid name"),
    NAME_SAME_AS_CURRENT("New name is the same as the current one"),

    // Депозит
    DEPOSIT_SUCCESS_PREFIX("Successfully deposited $"),
    DEPOSIT_ACCOUNT_NOT_SELECTED("Please select an account"),
    DEPOSIT_TOO_MUCH("Please deposit less or equal to 5000"),
    DEPOSIT_INVALID_AMOUNT("Please enter a valid amount"),

    // Перевод
    TRANSFER_SUCCESS_PREFIX("Successfully transferred $"),
    TRANSFER_INVALID_INSUFFICIENT_FUNDS("Invalid transfer: insufficient funds or invalid accounts"),
    TRANSFER_FIELDS_NOT_CONFIRMED("Please fill all fields and confirm"),
    TRANSFER_ACCOUNT_NOT_FOUND("No user found with this account number"),
    TRANSFER_REPEAT_SUCCESS_PREFIX("Transfer of $"),
    TRANSFER_FAILED_RETRY("Transfer failed: Please try again");

    private final String message;

    BankAlert(String massage) {
        this.message = massage;
    }
}
