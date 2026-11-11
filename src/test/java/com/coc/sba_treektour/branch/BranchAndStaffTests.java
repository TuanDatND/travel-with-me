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
import com.coc.sba_treektour.modules.branch.exception.BranchNotFoundException;
import com.coc.sba_treektour.modules.branch.exception.InvalidBranchOperationException;
import com.coc.sba_treektour.modules.branch.repository.BranchRepository;
import com.coc.sba_treektour.modules.branch.repository.StaffRepository;
import com.coc.sba_treektour.modules.branch.service.BranchService;
import com.coc.sba_treektour.modules.branch.service.StaffService;
import com.coc.sba_treektour.modules.branch.service.impl.BranchServiceImpl;
import com.coc.sba_treektour.modules.branch.service.impl.StaffServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                .name("Hanoi Branch")
                .phone("0987654321")
                .address("123 Cau Giay")
                .build();

        Branch savedBranch = Branch.builder()
                .id(1L)
                .name("Hanoi Branch")
                .phone("0987654321")
                .address("123 Cau Giay")
                .status("ACTIVE")
                .build();

        when(branchRepository.save(any(Branch.class))).thenReturn(savedBranch);

        BranchResponse response = branchService.createBranch(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Hanoi Branch", response.getName());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void testGetBranchById_NotFound() {
        when(branchRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BranchNotFoundException.class, () -> branchService.getBranchById(999L));
    }

    @Test
    void testDeleteBranch_WithExistingStaff_ThrowsException() {
        Branch branch = Branch.builder().id(1L).name("Da Nang Branch").status("ACTIVE").build();
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(staffRepository.existsByBranchId(1L)).thenReturn(true);

        InvalidBranchOperationException ex = assertThrows(InvalidBranchOperationException.class,
                () -> branchService.deleteBranch(1L));

        assertTrue(ex.getMessage().contains("staff members are still assigned"));
    }

    @Test
    void testDeleteBranch_SoftDeletesToInactive() {
        Branch branch = Branch.builder().id(1L).name("Da Nang Branch").status("ACTIVE").build();
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(staffRepository.existsByBranchId(1L)).thenReturn(false);

        branchService.deleteBranch(1L);

        assertEquals("INACTIVE", branch.getStatus());
        verify(branchRepository).save(branch);
    }

    @Test
    void testAssignStaff_RoleNotStaff_ThrowsException() {
        StaffRequest request = StaffRequest.builder()
                .branchId(1L)
                .userId(10L)
                .position("Manager")
                .build();

        Branch branch = Branch.builder().id(1L).name("HCM Branch").status("ACTIVE").build();
        Role customerRole = Role.builder().id(4L).name("CUSTOMER").build();
        User user = User.builder().id(10L).role(customerRole).status("ACTIVE").build();

        when(staffRepository.existsByUserId(10L)).thenReturn(false);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        InvalidBranchOperationException ex = assertThrows(InvalidBranchOperationException.class,
                () -> staffService.assignStaff(request));

        assertTrue(ex.getMessage().contains("STAFF role"));
    }

    @Test
    void testAssignStaff_InactiveBranchOrBlockedUser_ThrowsException() {
        StaffRequest request = StaffRequest.builder()
                .branchId(1L)
                .userId(10L)
                .position("Warehouse Manager")
                .build();

        Branch inactiveBranch = Branch.builder().id(1L).name("HCM Branch").status("INACTIVE").build();
        when(staffRepository.existsByUserId(10L)).thenReturn(false);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(inactiveBranch));

        assertThrows(InvalidBranchOperationException.class, () -> staffService.assignStaff(request));

        Branch activeBranch = Branch.builder().id(1L).name("HCM Branch").status("ACTIVE").build();
        Role staffRole = Role.builder().id(2L).name("STAFF").build();
        User blockedUser = User.builder().id(10L).role(staffRole).status("BLOCKED").build();

        when(branchRepository.findById(1L)).thenReturn(Optional.of(activeBranch));
        when(userRepository.findById(10L)).thenReturn(Optional.of(blockedUser));

        assertThrows(InvalidBranchOperationException.class, () -> staffService.assignStaff(request));
    }

    @Test
    void testAssignStaff_Success() {
        StaffRequest request = StaffRequest.builder()
                .branchId(1L)
                .userId(10L)
                .position("Warehouse Manager")
                .build();

        Branch branch = Branch.builder().id(1L).name("HCM Branch").status("ACTIVE").build();
        Role staffRole = Role.builder().id(2L).name("STAFF").build();
        User user = User.builder().id(10L).fullName("John Doe").role(staffRole).status("ACTIVE").build();

        Staff savedStaff = Staff.builder()
                .id(100L)
                .branch(branch)
                .user(user)
                .position("Warehouse Manager")
                .status("ACTIVE")
                .build();

        when(staffRepository.existsByUserId(10L)).thenReturn(false);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.hasActiveGuideProfile(10L)).thenReturn(false);
        when(staffRepository.save(any(Staff.class))).thenReturn(savedStaff);

        StaffResponse response = staffService.assignStaff(request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("HCM Branch", response.getBranchName());
        assertEquals("John Doe", response.getUserName());
        assertEquals("Warehouse Manager", response.getPosition());
    }

    @Test
    void testAssignStaff_FailsWhenActiveGuideProfileExists() {
        StaffRequest request = StaffRequest.builder()
                .branchId(1L)
                .userId(10L)
                .position("Warehouse Manager")
                .build();

        Branch branch = Branch.builder().id(1L).name("HCM Branch").status("ACTIVE").build();
        Role staffRole = Role.builder().id(2L).name("STAFF").build();
        User user = User.builder().id(10L).fullName("John Doe").role(staffRole).status("ACTIVE").build();

        when(staffRepository.existsByUserId(10L)).thenReturn(false);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.hasActiveGuideProfile(10L)).thenReturn(true);

        InvalidBranchOperationException ex = assertThrows(
                InvalidBranchOperationException.class,
                () -> staffService.assignStaff(request)
        );
        assertTrue(ex.getMessage().contains("active Tour Guide"));
    }

    @Test
    void testUpdateStaffStatus_FailsWhenActiveGuideProfileExists() {
        Branch branch = Branch.builder().id(1L).name("HCM Branch").status("ACTIVE").build();
        Role staffRole = Role.builder().id(2L).name("STAFF").build();
        User user = User.builder().id(10L).fullName("John Doe").role(staffRole).status("ACTIVE").build();
        Staff staff = Staff.builder().id(100L).branch(branch).user(user).status("INACTIVE").build();

        when(staffRepository.findById(100L)).thenReturn(Optional.of(staff));
        when(userRepository.hasActiveGuideProfile(10L)).thenReturn(true);

        InvalidBranchOperationException ex = assertThrows(
                InvalidBranchOperationException.class,
                () -> staffService.updateStaffStatus(100L, "ACTIVE")
        );
        assertTrue(ex.getMessage().contains("active Tour Guide"));
    }

    @Test
    void testDeleteStaff_Success() {
        Staff staff = Staff.builder().id(50L).build();
        when(staffRepository.findById(50L)).thenReturn(Optional.of(staff));

        staffService.deleteStaff(50L);

        verify(staffRepository).delete(staff);
    }
}
