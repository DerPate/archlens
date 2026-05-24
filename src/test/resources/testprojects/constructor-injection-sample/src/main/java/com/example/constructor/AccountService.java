package com.example.constructor;

public class AccountService implements IAccountService {
    @Override
    public Account getById(long id) {
        return new Account(id);
    }
}
