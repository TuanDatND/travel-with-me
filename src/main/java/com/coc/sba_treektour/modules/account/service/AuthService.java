package com.coc.sba_treektour.modules.account.service;

import com.coc.sba_treektour.modules.account.dto.request.LoginRequest;
import com.coc.sba_treektour.modules.account.dto.request.RegisterRequest;
import com.coc.sba_treektour.modules.account.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
