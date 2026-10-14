package com.coc.sba_treektour.modules.account.service;

import com.coc.sba_treektour.modules.account.dto.CreateInternalUserRequest;
import com.coc.sba_treektour.modules.account.dto.InternalUserResponse;
import com.coc.sba_treektour.modules.account.dto.UserProfileRequest;
import com.coc.sba_treektour.modules.account.dto.UserResponse;
import com.coc.sba_treektour.modules.account.entity.User;

import java.util.List;

public interface UserService {
    InternalUserResponse createInternalUser(CreateInternalUserRequest request);
    List<UserResponse> getAllUsers();
    UserResponse getUserById(Long id);
    UserResponse getUserByEmail(String email);
    UserResponse updateProfile(String email, UserProfileRequest request);
    UserResponse mapToUserResponse(User user);
}
