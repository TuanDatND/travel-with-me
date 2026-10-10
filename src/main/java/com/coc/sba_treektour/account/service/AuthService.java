package com.coc.sba_treektour.account.service;

import com.coc.sba_treektour.account.dto.AuthResponse;
import com.coc.sba_treektour.account.dto.LoginRequest;
import com.coc.sba_treektour.account.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
