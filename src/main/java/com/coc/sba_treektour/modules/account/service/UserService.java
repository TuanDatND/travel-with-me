package com.coc.sba_treektour.modules.account.service;

import com.coc.sba_treektour.modules.account.dto.request.AdminUpdateUserRequest;
import com.coc.sba_treektour.modules.account.dto.request.ChangePasswordRequest;
import com.coc.sba_treektour.modules.account.dto.request.CreateInternalUserRequest;
import com.coc.sba_treektour.modules.account.dto.request.UserProfileRequest;
import com.coc.sba_treektour.modules.account.dto.response.InternalUserResponse;
import com.coc.sba_treektour.modules.account.dto.response.UserResponse;
import com.coc.sba_treektour.modules.account.entity.User;

import java.util.List;

public interface UserService {
    InternalUserResponse createInternalUser(CreateInternalUserRequest request);
    List<UserResponse> getAllUsers();
    UserResponse getUserById(Long id);
    UserResponse getUserByEmail(String email);
    UserResponse updateProfile(String email, UserProfileRequest request);
    void changePassword(String email, ChangePasswordRequest request);
    UserResponse updateUserByAdmin(Long id, AdminUpdateUserRequest request);
    UserResponse updateUserStatus(Long id, String status);
    void deleteUser(Long id);
    UserResponse mapToUserResponse(User user);
}
