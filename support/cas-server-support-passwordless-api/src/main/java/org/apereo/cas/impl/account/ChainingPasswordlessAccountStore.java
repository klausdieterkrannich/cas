package org.apereo.cas.impl.account;

import org.apereo.cas.api.PasswordlessUserAccount;
import org.apereo.cas.api.PasswordlessUserAccountStore;
import lombok.RequiredArgsConstructor;
import lombok.val;
import java.util.List;
import java.util.Optional;

/**
 * This is {@link ChainingPasswordlessAccountStore}.
 *
 * @author Misagh Moayyed
 * @since 7.1.0
 */
@RequiredArgsConstructor
public class ChainingPasswordlessAccountStore implements PasswordlessUserAccountStore {
    private final List<PasswordlessUserAccountStore> stores;

    @Override
    public Optional<PasswordlessUserAccount> findUser(final String username) throws Throwable {
        for (val store : stores) {
            val user = store.findUser(username);
            if (user.isPresent()) {
                return user;
            }
        }
        return Optional.empty();
    }

    @Override
    public void reload() {
        stores.forEach(PasswordlessUserAccountStore::reload);
    }
}