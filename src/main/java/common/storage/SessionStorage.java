package common.storage;

import api.models.CreateUserRequest;

import java.util.ArrayList;
import java.util.List;

public class SessionStorage {

    private static final ThreadLocal <SessionStorage> INSTANCE = ThreadLocal.withInitial(SessionStorage :: new);

    // просто список созданных пользователей в порядке добавления
    private final List<CreateUserRequest> users = new ArrayList<>();

    private SessionStorage() {
    }

    // Добавляем список пользователей в стор
    public static void addUsers(List<CreateUserRequest> users) {
        INSTANCE.get().users.addAll(users);
    }

    // Возвращаем пользователя по порядковому номеру (нумерация с 1)
    public static CreateUserRequest getUser(int number) {
        return INSTANCE.get().users.get(number - 1);
    }

    // Удобный хелпер: первый пользователь
    public static CreateUserRequest getUser() {
        return getUser(1);
    }

    // Очистка сессии
    public static void clear() {
        INSTANCE.get().users.clear();
    }
}
