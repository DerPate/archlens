package com.example.constructor;

public class AccountController {
    private final IAccountService accountService;

    public AccountController(IAccountService accountService) {
        this.accountService = accountService;
    }

    public Account get(long id) {
        return accountService.getById(id);
    }
}
