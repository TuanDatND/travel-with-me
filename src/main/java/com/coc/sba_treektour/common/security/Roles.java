package com.coc.sba_treektour.common.security;

/**
 * Tên các role dùng chung trong hệ thống (khớp cột roles.role_name, cũng là authority trong JWT).
 *
 * Thứ bậc quyền: ADMIN (tổng) > BRANCH_ADMIN (quản lý 1 chi nhánh) > STAFF (nhân viên chi nhánh).
 * GUIDE và CUSTOMER nằm ngoài cây quản lý chi nhánh.
 */
public final class Roles {

    /** Admin tổng: toàn quyền trên mọi chi nhánh. */
    public static final String ADMIN = "ADMIN";

    /** Admin chi nhánh: quản lý chi nhánh và nhân viên của chính chi nhánh mình. */
    public static final String BRANCH_ADMIN = "BRANCH_ADMIN";

    /** Nhân viên chi nhánh: chỉ thao tác trong phạm vi chi nhánh mình. */
    public static final String STAFF = "STAFF";

    public static final String GUIDE = "GUIDE";
    public static final String CUSTOMER = "CUSTOMER";

    private Roles() {
    }
}
