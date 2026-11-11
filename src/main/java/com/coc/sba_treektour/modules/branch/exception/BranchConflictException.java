package com.coc.sba_treektour.modules.branch.exception;

public class BranchConflictException extends InvalidBranchOperationException {
    public BranchConflictException(String message) {
        super(message);
    }
}

