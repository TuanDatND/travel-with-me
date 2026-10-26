package com.coc.sba_treektour.branch;

import com.coc.sba_treektour.modules.account.entity.Role;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.branch.dto.request.BranchRequest;
import com.coc.sba_treektour.modules.branch.dto.request.StaffRequest;
import com.coc.sba_treektour.modules.branch.dto.response.BranchResponse;
import com.coc.sba_treektour.modules.branch.dto.response.StaffResponse;
import com.coc.sba_treektour.modules.branch.entity.Branch;
import com.coc.sba_treektour.modules.branch.entity.Staff;
import com.coc.sba_treektour.modules.branch.enums.BranchStatus;
import com.coc.sba_treektour.modules.branch.enums.StaffStatus;
import com.coc.sba_treektour.modules.branch.exception.BranchNotFoundException;
import com.coc.sba_treektour.modules.branch.exception.InvalidBranchOperationException;
import com.coc.sba_treektour.modules.branch.exception.StaffNotFoundException;
import com.coc.sba_treektour.modules.branch.repository.BranchRepository;
import com.coc.sba_treektour.modules.branch.repository.StaffRepository;
import com.coc.sba_treektour.modules.branch.service.BranchService;
import com.coc.sba_treektour.modules.branch.service.StaffService;
import com.coc.sba_treektour.modules.branch.service.impl.BranchServiceImpl;
import com.coc.sba_treektour.modules.branch.service.impl.StaffServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BranchAndStaffTests {

    private BranchRepository branchRepository;
    private StaffRepository staffRepository;
    private UserRepository userRepository;
    private BranchService branchService;
    private StaffService staffService;

    @BeforeEach
    void setUp() {
        branchRepository = mock(BranchRepository.class);
        staffRepository = mock(StaffRepository.class);
        userRepository = mock(UserRepository.class);
        branchService = new BranchServiceImpl(branchRepository, staffRepository);
        staffService = new StaffServiceImpl(staffRepository, branchRepository, userRepository);
    }

    @Test
    void testCreateBranch_Success() {
        BranchRequest request = BranchRequest.builder()
                .name("Chi nhánh Hà Nội")
                .phone("0987654321")
                .address("123 Cầu Giấy")
                .build();

        Branch savedBranch = Branch.builder()
                .id(1L)
                .name("Chi nhánh Hà Nội")
                .phone("0987654321")
                .address("123 Cầu Giấy")
                .status("ACTIVE")
                .build();

        when(branchRepository.save(any(Branch.class))).thenReturn(savedBranch);

        BranchResponse response = branchService.createBranch(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Chi nhánh Hà Nội", response.getName());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void testGetBranchById_NotFound() {
        when(branchRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BranchNotFoundException.class, () -> branchService.getBranchById(999L));
    }

    @Test
    void testDeleteBranch_WithExistingStaff_ThrowsException() {
        Branch branch = Branch.builder().id(1L).name("Chi nhánh Đà Nẵng").status("ACTIVE").build();
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(staffRepository.existsByBranchId(1L)).thenReturn(true);

        InvalidBranchOperationException ex = assertThrows(InvalidBranchOperationException.class,
                () -> branchService.deleteBranch(1L));

        assertTrue(ex.getMessage().contains("vẫn còn nhân viên"));
    }

    @Test
    void testAssignStaff_RoleNotStaff_ThrowsException() {
        StaffRequest request = StaffRequest.builder()
                .branchId(1L)
                .userId(10L)
                .position("Quản lý")
                .build();

        Branch branch = Branch.builder().id(1L).name("Chi nhánh HCM").build();
        Role customerRole = Role.builder().id(4L).name("CUSTOMER").build();
        User user = User.builder().id(10L).role(customerRole).build();

        when(staffRepository.existsByUserId(10L)).thenReturn(false);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        InvalidBranchOperationException ex = assertThrows(InvalidBranchOperationException.class,
                () -> staffService.assignStaff(request));

        assertTrue(ex.getMessage().contains("vai trò STAFF"));
    }

    @Test
    void testAssignStaff_Success() {
        StaffRequest request = StaffRequest.builder()
                .branchId(1L)
                .userId(10L)
                .position("Quản lý kho")
                .build();

        Branch branch = Branch.builder().id(1L).name("Chi nhánh HCM").build();
        Role staffRole = Role.builder().id(2L).name("STAFF").build();
        User user = User.builder().id(10L).fullName("Nguyễn Văn A").role(staffRole).build();

        Staff savedStaff = Staff.builder()
                .id(100L)
                .branch(branch)
                .user(user)
                .position("Quản lý kho")
                .status("ACTIVE")
                .build();

        when(staffRepository.existsByUserId(10L)).thenReturn(false);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(staffRepository.save(any(Staff.class))).thenReturn(savedStaff);

        StaffResponse response = staffService.assignStaff(request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Chi nhánh HCM", response.getBranchName());
        assertEquals("Nguyễn Văn A", response.getUserName());
        assertEquals("Quản lý kho", response.getPosition());
    }

    @Test
    void testDeleteStaff_Success() {
        Staff staff = Staff.builder().id(50L).build();
        when(staffRepository.findById(50L)).thenReturn(Optional.of(staff));

        staffService.deleteStaff(50L);

        verify(staffRepository).delete(staff);
    }
}
