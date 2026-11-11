package com.coc.sba_treektour.modules.account.exception;

public class AccountConflictException extends InvalidAccountException {
    public AccountConflictException(String message) {
        super(message);
    }
}

