package com.example.laptopshop.service;

import java.time.LocalDate;
import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.Voucher;
import com.example.laptopshop.domain.VoucherScope;
import com.example.laptopshop.domain.VoucherType;
import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.ScopeType;
import com.example.laptopshop.dto.request.Voucher.VoucherCreationRequest;
import com.example.laptopshop.dto.request.Voucher.VoucherUpdateRequest;
import com.example.laptopshop.dto.response.Voucher.VoucherResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.mapper.VoucherMapper;
import com.example.laptopshop.repository.VoucherRepository;
import com.example.laptopshop.repository.OrderRepository;
import com.example.laptopshop.repository.UserVoucherRepository;
import com.example.laptopshop.service.UploadService;
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
public class VoucherService {

    VoucherRepository voucherRepository;
    VoucherMapper voucherMapper;
    UploadService uploadService;
    UserVoucherRepository userVoucherRepository;
    OrderRepository orderRepository;


    @Transactional(readOnly = true)
    public List<VoucherResponse> getAllVouchers() {
        List<Voucher> couList = this.voucherRepository.findAll();
        return this.voucherMapper.toResponseList(couList);
    }

    @Transactional(readOnly = true)
    public VoucherResponse getVoucherResponseById(String id) {
        Voucher voucher = getVoucherById(id);
        return this.voucherMapper.toResponse(voucher);
    }

    public Voucher getVoucherById(String id) {
        return this.voucherRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
    }

    // Nhận DTO từ Controller, validate dữ liệu thô, map sang Entity, xử lý ảnh,
    // lưu DB rồi map sang Response. Controller gửi dạng form-data (@ModelAttribute).
    @Transactional
    public VoucherResponse createVoucher(VoucherCreationRequest request) {
        validateCode(request.getCode(), null);
        validateDiscountValue(request.getDiscountPercent(), request.getDiscountAmount());
        validateNumericBounds(request);

        // Map các field thuần (discountPercent, discountAmount, expiryDate) từ DTO
        // sang Entity qua MapStruct. code/usageLimit/usedCount/image KHÔNG được map
        // ở đây (đã ignore trong VoucherMapper) vì cần xử lý riêng bên dưới.
        Voucher voucher = this.voucherMapper.toEntity(request);
        voucher.setCode(request.getCode().trim().toUpperCase());
        voucher.setUsageLimit(
                request.getUsageLimit() == null || request.getUsageLimit() < 0 ? 0 : request.getUsageLimit());
        // Mapper ignore `active` nên phải set tay, nếu không toggle "Trạng thái"
        // lúc tạo bị nuốt im lặng (voucher luôn bật dù admin đã tắt).
        voucher.setActive(request.isActive());

        // Voucher mới tạo luôn bắt đầu từ 0 lượt đã dùng, không cho client tự set
        voucher.setUsedCount(0);
        applyScopes(voucher, request.getScopeType(), request.getScopeValues());

        // Xử lý upload ảnh voucher nếu có (ưu tiên file mới, rồi tới URL online)
        MultipartFile file = request.getInputFile();
        if (file != null && !file.isEmpty()) {
            String image = this.uploadService.handleSaveUploadFile(file, "voucher");
            voucher.setImage(image);
        } else {
            String imageFromUrl = this.uploadService.handleSaveUploadUrl(request.getImageUrl(), "voucher");
            if (imageFromUrl != null) {
                voucher.setImage(imageFromUrl);
            }
        }

        Voucher voucherSaved = this.voucherRepository.save(voucher);
        return this.voucherMapper.toResponse(voucherSaved);
    }

    // Cập nhật thông tin voucher theo id
    @Transactional
    public VoucherResponse updateVoucher(String id, VoucherUpdateRequest request) {
        Voucher voucher = getVoucherById(id);

        validateCode(request.getCode(), id);
        validateDiscountValue(request.getDiscountPercent(), request.getDiscountAmount());
        validateNumericBounds(request);

        // Đổ các field thuần (discountPercent, discountAmount, expiryDate, active)
        // từ DTO đè lên Entity cũ qua MapStruct (@MappingTarget), rồi set riêng
        // code/usageLimit bên dưới
        this.voucherMapper.updateEntity(request, voucher);
        voucher.setCode(request.getCode().trim().toUpperCase());
        voucher.setUsageLimit(
                request.getUsageLimit() == null || request.getUsageLimit() < 0 ? 0 : request.getUsageLimit());
        applyScopes(voucher, request.getScopeType(), request.getScopeValues());

        // Xử lý ảnh: ưu tiên file mới > URL online > cờ xóa; còn lại giữ nguyên ảnh hiện tại
        MultipartFile file = request.getInputFile();
        boolean hasNewFile = file != null && !file.isEmpty();
        boolean hasImageUrl = request.getImageUrl() != null && !request.getImageUrl().isBlank();
        if (hasNewFile || hasImageUrl) {
            if (voucher.getImage() != null) {
                this.uploadService.handleDeleteFile(voucher.getImage());
            }
            String newImage = hasNewFile
                    ? this.uploadService.handleSaveUploadFile(file, "voucher")
                    : this.uploadService.handleSaveUploadUrl(request.getImageUrl(), "voucher");
            voucher.setImage(newImage);
        } else if (request.isRemoveImage() && voucher.getImage() != null) {
            this.uploadService.handleDeleteFile(voucher.getImage());
            voucher.setImage(null);
        }

        // usedCount KHÔNG cho cập nhật thủ công qua form update, chỉ hệ thống tự tăng
        // khi voucher được áp dụng vào đơn hàng
        Voucher voucherUpdated = this.voucherRepository.save(voucher);
        return this.voucherMapper.toResponse(voucherUpdated);
    }

    /**
     * Dựng lại danh sách phạm vi của voucher từ DTO.
     *
     * <p>
     * Thay toàn bộ thay vì cập nhật từng dòng: form luôn gửi lên danh sách đầy
     * đủ, nên cách này vừa đúng vừa tránh phải so khớp id. {@code orphanRemoval}
     * trên quan hệ sẽ tự xoá những dòng không còn trong danh sách mới.
     */
    private void applyScopes(Voucher voucher, ScopeType scopeType, List<String> scopeValues) {
        voucher.getScopes().clear();
        if (scopeType == null || scopeType == ScopeType.ALL) {
            return; // không phạm vi = áp cả đơn
        }
        if (scopeValues == null) {
            return;
        }
        for (String raw : scopeValues) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            VoucherScope scope = new VoucherScope();
            scope.setVoucher(voucher);
            scope.setTargetType(scopeType);
            scope.setTargetValue(VoucherScope.normalizeTargetValue(scopeType, raw));
            voucher.getScopes().add(scope);
        }
    }

    // Xóa mềm voucher theo id (nhờ @SQLDelete ở Voucher.java). Xóa ảnh Cloudinary trước.
    public void deleteVoucher(String id) {
        Voucher voucher = getVoucherById(id);
        if (voucher.getImage() != null) {
            this.uploadService.handleDeleteFile(voucher.getImage());
        }
        this.voucherRepository.delete(voucher);
    }

    // Xóa hàng loạt voucher theo danh sách id: xóa ảnh vật lý từng voucher trước khi
    // xóa record (giống deleteVoucher đơn), wrap trong 1 transaction để nhất quán.
    // Nhờ @SQLDelete, deleteAll() tự động đổi thành xóa MỀM (UPDATE deleted_at).
    @Transactional
    public void deleteVouchersByIds(List<String> ids) {
        List<Voucher> vouchers = this.voucherRepository.findAllById(ids);
        if (vouchers.size() != ids.size()) {
            throw new AppException(ErrorCode.VOUCHER_NOT_FOUND);
        }
        for (Voucher voucher : vouchers) {
            if (voucher.getImage() != null) {
                this.uploadService.handleDeleteFile(voucher.getImage());
            }
        }
        this.voucherRepository.deleteAll(vouchers);
    }

    // Kích hoạt/khóa hàng loạt voucher theo danh sách id
    @Transactional
    public void updateVouchersActive(List<String> ids, boolean active) {
        List<Voucher> vouchers = this.voucherRepository.findAllById(ids);
        if (vouchers.size() != ids.size()) {
            throw new AppException(ErrorCode.VOUCHER_NOT_FOUND);
        }
        vouchers.forEach(voucher -> voucher.setActive(active));
        this.voucherRepository.saveAll(vouchers);
    }

    /**
     * Kiểm tra 1 voucher còn dùng được không: đang active, chưa hết hạn, chưa vượt
     * usageLimit.
     * Có thể gọi hàm này sau này khi xử lý Order để áp voucher.
     */
    public boolean isVoucherUsable(Voucher voucher) {
        if (voucher == null || !voucher.isActive())
            return false;
        if (voucher.getExpiryDate() != null && voucher.getExpiryDate().toLocalDate().isBefore(LocalDate.now()))
            return false;
        if (voucher.getUsageLimit() != null && voucher.getUsageLimit() > 0
                && voucher.getUsedCount() != null && voucher.getUsedCount() >= voucher.getUsageLimit())
            return false;
        return true;
    }

    /**
     * Tính số tiền thực tế được giảm dựa trên tổng tiền đơn hàng.
     * Ưu tiên discountAmount (giảm trực tiếp) nếu có, ngược lại tính theo
     * discountPercent. Số tiền giảm không bao giờ vượt quá tổng tiền đơn hàng.
     *
     * <p>
     * {@code maxDiscountAmount} là trần cho kiểu phần trăm — không có nó thì
     * voucher 10% trên đơn 90 triệu giảm thẳng 9 triệu. Kiểu số tiền cố định
     * không cần trần vì bản thân nó đã là con số chặn sẵn.
     */
    public long calculateDiscount(Voucher voucher, long orderTotal) {
        if (voucher == null || orderTotal <= 0)
            return 0;

        // kiểu cố định
        if (voucher.getDiscountAmount() != null && voucher.getDiscountAmount() > 0) {
            return Math.min(voucher.getDiscountAmount(), orderTotal);  // lấy min giữa 500k vs 90k tiền hàng sau khi giảm thì lấy = 90 sau đó thì tiền hàng = 0
        }

        // kiểu phần trăm
        if (voucher.getDiscountPercent() != null && voucher.getDiscountPercent() > 0) {
            long amount = orderTotal * voucher.getDiscountPercent() / 100; // tổng số tiền hàng cần giảm giá : 20tr * 20/100
            Long cap = voucher.getMaxDiscountAmount();
            if (cap != null && cap > 0) {
                amount = Math.min(amount, cap); // lấy cái nhỏ nhất giữa tổng số tiền hàng cần giảm giá & Trần giảm
            }
            return Math.min(amount, orderTotal);
        }

        return 0;
    }

    /**
     * Mệnh giá voucher nếu đơn đủ lớn — dùng để đo phần khách MẤT khi đơn nhỏ
     * hơn mệnh giá (BR-V14): {@code forfeited = nominal - calculateDiscount(...)}.
     */
    public long calculateNominalDiscount(Voucher voucher, long orderTotal) {
        if (voucher == null || orderTotal <= 0) {
            return 0;
        }
        if (voucher.getDiscountAmount() != null && voucher.getDiscountAmount() > 0) {
            // Số tiền cố định: mệnh giá KHÔNG kẹp theo đơn — chính phần chênh này
            // là thứ cần đo.
            return voucher.getDiscountAmount();
        }
        return calculateDiscount(voucher, orderTotal);
    }

    /**
     * D16: voucher khách còn CLAIM được — kho voucher ở trang ví.
     *
     * <p>
     * Lọc PUBLIC (ASSIGNED là admin phát đích danh, không cho tự nhận),
     * đang bật, chưa hết hạn, chưa hết lượt phát, và chưa đến thời gian dùng thì
     * vẫn hiện (khách lưu trước, tới ngày mới dùng được).
     */
    @Transactional(readOnly = true)
    public List<VoucherResponse> getClaimableVouchers() {
        return this.voucherRepository.findAll().stream()
                .filter(c -> c.getVoucherType() == null || c.getVoucherType() == VoucherType.PUBLIC)
                .filter(this::isVoucherUsable)
                .map(this.voucherMapper::toResponse)
                .toList();
    }

    /**
     * Khách đã chạm trần {@code perUserLimit} của voucher này chưa? (D15)
     *
     * <p>
     * Voucher có HAI đường vào đơn nên phải đếm ở hai nguồn: voucher lấy từ ví
     * ({@code user_vouchers}) và mã gõ tay ({@code orders}). Đếm riêng từng nguồn
     * sẽ hở: khách gõ mã 1 lần rồi claim voucher cùng voucher đó để dùng lần 2.
     *
     * <p>
     * Đơn {@code CANCELLED} bị loại — hủy đơn không tính là đã dùng.
     */
    @Transactional(readOnly = true)
    public boolean hasReachedPerUserLimit(String userId, Voucher voucher) {
        if (userId == null || voucher == null || voucher.getPerUserLimit() == null
                || voucher.getPerUserLimit() <= 0) {
            return false; // null/<=0 = không giới hạn (P3)
        }
        long fromWallet = this.userVoucherRepository.countByUserIdAndVoucherId(userId, voucher.getId());
        long typed = this.orderRepository.countByUserIdAndVoucherIdAndStatusNot(
                userId, voucher.getId(), OrderStatus.CANCELLED);
        return fromWallet + typed >= voucher.getPerUserLimit();
    }

    /**
     * Tiền hàng ĐỦ ĐIỀU KIỆN của voucher
     */
    public long calculateEligibleAmount(Voucher voucher, List<EligibleLine> lines) {
        if (voucher == null || lines == null || lines.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (EligibleLine line : lines) {
            if (matchesScope(voucher, line)) {
                total += Math.max(0L, line.lineTotal() - line.linePromotionDiscount());
            }
        }
        return total;
    }

    /**
     * Dòng hàng ở dạng đủ để xét scope
     *
     * @param linePromotionDiscount tiền promotion đã giảm riêng dòng này (D2)
     */
    public record EligibleLine(String productId, String categoryId, String factory,
            long lineTotal, long linePromotionDiscount) {
    }

    /**
     * Dòng này có thuộc phạm vi voucher áp dụng? Cùng luật khớp với PromotionEngine.
     *
     * <p>
     * Voucher có thể khai nhiều phạm vi — khớp BẤT KỲ dòng nào là được. Không
     * khai phạm vi nào (hoặc chỉ ALL) = áp cho toàn bộ đơn.
     */
    private boolean matchesScope(Voucher voucher, EligibleLine line) {
        ScopeType declared = voucher.getScopeType();
        if (declared == null || declared == ScopeType.ALL) {
            return true; // không khai phạm vi -> áp cả đơn
        }
        List<VoucherScope> scopes = voucher.getScopes();
        if (scopes == null || scopes.isEmpty()) {
            // Khai phạm vi nhưng chưa có giá trị nào -> không khớp gì. Trả true ở
            // đây sẽ biến voucher "chỉ áp danh mục X" thành áp cả đơn.
            return false;
        }
        for (VoucherScope scope : scopes) {
            ScopeType type = scope.getTargetType();
            if (type == null || type == ScopeType.ALL) {
                return true;
            }
            String target = scope.getTargetValue();
            if (target == null || target.isBlank()) {
                continue; // khai báo scope mà thiếu giá trị -> bỏ qua dòng này
            }
            boolean hit = switch (type) {
                case CATEGORY -> target.equals(line.categoryId());
                case BRAND -> line.factory() != null && target.equalsIgnoreCase(line.factory());
                case PRODUCT -> target.equals(line.productId());
                case ALL -> true;
            };
            if (hit) {
                return true;
            }
        }
        return false;
    }

    // Validate code + kiểm tra trùng lặp, dùng chung cho cả create và update    // (currentId = id hiện tại, loại trừ chính nó khỏi kiểm tra
    // trùng).
    private void validateCode(String code, String currentId) {

        String normalized = code.trim();
        boolean exists = currentId == null
                ? this.voucherRepository.existsByCodeIgnoreCase(normalized)
                : this.voucherRepository.existsByCodeIgnoreCaseAndIdNot(normalized, currentId);

        if (exists) {
            throw new AppException(ErrorCode.VOUCHER_ALREADY_EXISTS);
        }
    }

    // Voucher chỉ được chọn đúng 1 trong 2 hình thức giảm giá: theo % hoặc theo
    // số tiền cố định. Không được để trống cả 2, cũng không được điền cả 2.
    private void validateDiscountValue(Integer discountPercent, Long discountAmount) {
        boolean hasPercent = discountPercent != null;
        boolean hasAmount = discountAmount != null;

        if (hasPercent == hasAmount) {
            throw new AppException(ErrorCode.INVALID_VOUCHER_CONFIG);
        }

        if (hasPercent && (discountPercent < 1 || discountPercent > 100)) {
            throw new AppException(ErrorCode.INVALID_DISCOUNT_PERCENT);
        }

        if (hasAmount && discountAmount <= 0) {
            throw new AppException(ErrorCode.INVALID_DISCOUNT_AMOUNT);
        }
    }

    /**
     * BR-A05 — chặn trường số nhận giá trị vô nghĩa (âm) lúc tạo Voucher.
     * {@code null} = không giới hạn (P3). {@code minOrderValue = 0} được phép.
     */
    private void validateNumericBounds(VoucherCreationRequest request) {
        validateNumericBounds(request.getUsageLimit(), request.getPerUserLimit(),
                request.getMinOrderValue(), request.getMaxDiscountAmount());
    }

    // phía cập nhật
    private void validateNumericBounds(VoucherUpdateRequest request) {
        validateNumericBounds(request.getUsageLimit(), request.getPerUserLimit(),
                request.getMinOrderValue(), request.getMaxDiscountAmount());
    }

    private void validateNumericBounds(Integer usageLimit, Integer perUserLimit,
            Long minOrderValue, Long maxDiscountAmount) {
        if (usageLimit != null && usageLimit < 0) {
            throw new AppException(ErrorCode.INVALID_VOUCHER_CONFIG);
        }
        if (perUserLimit != null && perUserLimit < 0) {
            throw new AppException(ErrorCode.INVALID_VOUCHER_CONFIG);
        }
        if (minOrderValue != null && minOrderValue < 0) {
            throw new AppException(ErrorCode.INVALID_VOUCHER_CONFIG);
        }
        if (maxDiscountAmount != null && maxDiscountAmount < 0) {
            throw new AppException(ErrorCode.INVALID_VOUCHER_CONFIG);
        }
    }
}
