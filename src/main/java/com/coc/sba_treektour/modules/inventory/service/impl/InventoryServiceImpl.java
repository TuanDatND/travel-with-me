package com.coc.sba_treektour.modules.inventory.service.impl;

import com.coc.sba_treektour.common.exception.CatalogException;
import com.coc.sba_treektour.modules.branch.repository.BranchRepository;
import com.coc.sba_treektour.modules.inventory.dto.request.AdjustRequest;
import com.coc.sba_treektour.modules.inventory.dto.response.AvailabilityResponse;
import com.coc.sba_treektour.modules.inventory.dto.response.InventoryPageResponse;
import com.coc.sba_treektour.modules.inventory.dto.response.InventoryResponse;
import com.coc.sba_treektour.modules.inventory.dto.response.SaleAvailabilityResponse;
import com.coc.sba_treektour.modules.inventory.entity.Inventory;
import com.coc.sba_treektour.modules.inventory.enums.InventoryStatus;
import com.coc.sba_treektour.modules.inventory.repository.InventoryRepository;
import com.coc.sba_treektour.modules.inventory.service.InventoryService;
import com.coc.sba_treektour.modules.product.entity.Product;
import com.coc.sba_treektour.modules.product.enums.ProductStatus;
import com.coc.sba_treektour.modules.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.TreeMap;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {
    private final InventoryRepository inventory;
    private final ProductRepository products;
    private final BranchRepository branches;
    private final InventoryAccess access;

    @Override
    public InventoryPageResponse<InventoryResponse> list(Long branchId, int page, int size) {
        access.requireBranch(branchId);
        if (page < 0 || size < 1 || size > 100) throw CatalogException.invalid("page >= 0; size từ 1 đến 100");
        var result = inventory.findByBranchId(branchId, PageRequest.of(page, size, Sort.by("productId")));
        // Lấy tên theo một query để không phát sinh N+1 khi liệt kê kho.
        var names = new java.util.HashMap<Long, String>();
        products.findAllById(result.getContent().stream().map(Inventory::getProductId).toList())
                .forEach(p -> names.put(p.getId(), p.getName()));
        return new InventoryPageResponse<>(result.getContent().stream().map(i -> response(i, names.get(i.getProductId()))).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    @Override
    public InventoryResponse importStock(Long branchId, Long productId, int quantity) {
        access.requireBranch(branchId);
        positive(quantity);
        activeBranch(branchId);
        // Khóa product trước inventory, kể cả khi dòng kho chưa có: hai lượt nhập đầu không tạo trùng.
        Product product = lockProduct(productId, true);
        Inventory row = inventory.lock(branchId, productId).orElseGet(() -> {
            var created = new Inventory(); created.setBranchId(branchId); created.setProductId(productId); return created;
        });
        requireActive(row);
        quantities(row, (long) row.getQuantityTotal() + quantity, (long) row.getQuantityAvailable() + quantity, row.getQuantityRented());
        return response(inventory.saveAndFlush(row), product.getName());
    }

    @Transactional
    @Override
    public InventoryResponse adjust(Long branchId, Long productId, AdjustRequest request) {
        access.requireBranch(branchId);
        activeBranch(branchId);
        Product product = lockProduct(productId, false);
        Inventory row = locked(branchId, productId);
        quantities(row, request.quantityTotal(), request.quantityAvailable(), row.getQuantityRented());
        return response(row, product.getName());
    }

    @Transactional
    @Override
    public InventoryResponse status(Long branchId, Long productId, InventoryStatus status) {
        access.requireBranch(branchId);
        Product product = lockProduct(productId, false);
        Inventory row = locked(branchId, productId);
        row.setStatus(status.name());
        return response(row, product.getName());
    }

    @Override
    public SaleAvailabilityResponse saleAvailability(Long branchId, Long productId) {
        activeBranch(branchId);
        Product product = products.findById(productId).filter(p -> p.getStatus().equals("ACTIVE"))
                .orElseThrow(() -> CatalogException.missing("Không tìm thấy thiết bị"));
        if (product.getSalePrice() == null) throw CatalogException.conflict("Thiết bị không bán");
        int available = inventory.findByBranchIdAndProductId(branchId, productId)
                .filter(i -> i.getStatus().equals("ACTIVE")).map(Inventory::getQuantityAvailable).orElse(0);
        return new SaleAvailabilityResponse(branchId, productId, available);
    }

    @Override
    public AvailabilityResponse availability(Long branchId, Long productId, LocalDate start, LocalDate end) {
        activeBranch(branchId);
        Product product = products.findById(productId).filter(p -> p.getStatus().equals("ACTIVE"))
                .orElseThrow(() -> CatalogException.missing("Không tìm thấy thiết bị"));
        if (product.getRentalPricePerDay() == null) throw CatalogException.conflict("Thiết bị không cho thuê");
        if (start == null || end == null || end.isBefore(start) || start.isBefore(LocalDate.now()))
            throw CatalogException.invalid("Ngày thuê phải từ hôm nay; ngày kết thúc không trước ngày bắt đầu");
        int available = inventory.findByBranchIdAndProductId(branchId, productId)
                .filter(i -> i.getStatus().equals("ACTIVE")).map(Inventory::getQuantityAvailable).orElse(0);
        // Chỉ đọc module Order, giữ hợp đồng productId/branchId và schema nguyên trạng.
        // RENTED đã trừ khỏi available nên không trừ lần hai. PENDING cũng giữ chỗ bảo thủ.
        var events = new TreeMap<LocalDate, Long>();
        inventory.findRentalReservations(branchId, productId, start, end).forEach(reservation -> {
            LocalDate from = reservation.getRentalStartDate(), to = reservation.getRentalEndDate();
            long quantity = reservation.getQuantity();
            events.merge(from.isBefore(start) ? start : from, quantity, Long::sum);
            if (to.isBefore(end)) events.merge(to.plusDays(1), -quantity, Long::sum);
        });
        long current = 0, peak = 0;
        for (long change : events.values()) { current += change; peak = Math.max(peak, current); }
        return new AvailabilityResponse(branchId, productId, start, end, available, peak, (int) Math.max(0, available - peak));
    }

    /** Đầu nối nội bộ cho Order: gọi trong transaction chuyển trạng thái và chỉ một lần mỗi lần giao. */
    @Transactional
    @Override
    public void fulfillSale(Long branchId, Long productId, int quantity) {
        positive(quantity); activeBranch(branchId);
        Product product = lockProduct(productId, true);
        if (product.getSalePrice() == null) throw CatalogException.conflict("Thiết bị không bán");
        Inventory row = locked(branchId, productId); requireActive(row); sufficient(row, quantity);
        quantities(row, (long) row.getQuantityTotal() - quantity, (long) row.getQuantityAvailable() - quantity, row.getQuantityRented());
    }

    /** Xuất thiết bị thuê: chuyển từ còn sẵn sang đang thuê, tổng không đổi. */
    @Transactional
    @Override
    public void handOutRental(Long branchId, Long productId, int quantity) {
        positive(quantity); activeBranch(branchId);
        Product product = lockProduct(productId, true);
        if (product.getRentalPricePerDay() == null) throw CatalogException.conflict("Thiết bị không cho thuê");
        Inventory row = locked(branchId, productId); requireActive(row); sufficient(row, quantity);
        quantities(row, row.getQuantityTotal(), (long) row.getQuantityAvailable() - quantity, (long) row.getQuantityRented() + quantity);
    }

    /** Nhận trả vẫn được phép khi sản phẩm/chi nhánh/kho ngừng hoạt động. */
    @Transactional
    @Override
    public void receiveRental(Long branchId, Long productId, int quantity) {
        positive(quantity); lockProduct(productId, false);
        Inventory row = locked(branchId, productId);
        if (quantity > row.getQuantityRented()) throw CatalogException.conflict("Số trả vượt số đang thuê");
        quantities(row, row.getQuantityTotal(), (long) row.getQuantityAvailable() + quantity, (long) row.getQuantityRented() - quantity);
    }

    private Product lockProduct(Long id, boolean active) {
        Product product = products.lockById(id).orElseThrow(() -> CatalogException.missing("Không tìm thấy thiết bị"));
        if (active && !product.getStatus().equals("ACTIVE")) throw CatalogException.conflict("Thiết bị ngừng hoạt động");
        return product;
    }
    private Inventory locked(Long branch, Long product) {
        return inventory.lock(branch, product).orElseThrow(() -> CatalogException.missing("Chưa có kho cho thiết bị tại chi nhánh"));
    }
    private void activeBranch(Long id) {
        var branch = branches.findById(id).orElseThrow(() -> CatalogException.missing("Không tìm thấy chi nhánh"));
        if (!branch.getStatus().equals("ACTIVE")) throw CatalogException.conflict("Chi nhánh ngừng hoạt động");
    }
    private void positive(int quantity) { if (quantity <= 0) throw CatalogException.invalid("Số lượng phải lớn hơn 0"); }
    private void sufficient(Inventory row, int quantity) {
        if (quantity > row.getQuantityAvailable()) throw CatalogException.conflict("Không đủ tồn kho");
    }
    private void requireActive(Inventory row) {
        if (!row.getStatus().equals("ACTIVE")) throw CatalogException.conflict("Kho thiết bị ngừng hoạt động");
    }
    private void quantities(Inventory row, long total, long available, long rented) {
        if (total < 0 || available < 0 || rented < 0 || total > Integer.MAX_VALUE || available + rented > total)
            throw CatalogException.conflict("Tồn kho không hợp lệ: còn sẵn + đang thuê phải <= tổng");
        row.setQuantityTotal((int) total); row.setQuantityAvailable((int) available); row.setQuantityRented((int) rented);
    }
    private InventoryResponse response(Inventory row, String name) {
        return new InventoryResponse(row.getId(), row.getBranchId(), row.getProductId(), name,
                row.getQuantityTotal(), row.getQuantityAvailable(), row.getQuantityRented(), row.getStatus());
    }
}
