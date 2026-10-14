package com.coc.sba_treektour.modules.account.service;

import com.coc.sba_treektour.modules.account.dto.AuthResponse;
import com.coc.sba_treektour.modules.account.dto.LoginRequest;
import com.coc.sba_treektour.modules.account.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
