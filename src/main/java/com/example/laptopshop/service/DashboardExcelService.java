package com.example.laptopshop.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.dto.request.Dashboard.DashboardRange;
import com.example.laptopshop.dto.response.DashboardStats;

/**
 * Xuất báo cáo Bảng điều khiển ra file Excel (.xlsx) bằng Apache POI.
 *
 * <p>
 * Báo cáo là TỔNG HỢP THEO KỲ (không phải danh sách chi tiết đơn) — 4 sheet:
 * Tổng quan · Doanh thu theo ngày · Top sản phẩm · Khuyến mại.
 *
 * <p>
 * Số liệu lấy từ CÙNG hàm {@link DashboardService#getStats} mà màn hình dùng, nên
 * file xuất ra luôn khớp đúng thứ admin đang nhìn thấy.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DashboardExcelService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String VND_FORMAT = "#,##0 \"₫\"";

    DashboardService dashboardService;

    @Transactional(readOnly = true)
    public byte[] export(DashboardRange range) {
        DashboardStats stats = dashboardService.getStats(range);

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Styles styles = new Styles(workbook);
            writeOverview(workbook, styles, stats, range);
            writeRevenueByDay(workbook, styles, stats);
            writeTopProducts(workbook, styles, stats);
            writePromotion(workbook, styles, stats);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được file báo cáo Excel", e);
        }
    }

    // ===== Sheet 1: Tổng quan =====

    private void writeOverview(Workbook wb, Styles s, DashboardStats stats, DashboardRange range) {
        Sheet sheet = wb.createSheet("Tổng quan");
        int r = 0;

        r = title(sheet, s, r, "BÁO CÁO BẢNG ĐIỀU KHIỂN — " + rangeLabel(range));
        if (!stats.getRevenueSeries().isEmpty()) {
            Row periodRow = sheet.createRow(r++);
            label(periodRow, 0, "Khoảng:", s.bold);
            periodRow.createCell(1).setCellValue(
                    stats.getRevenueSeries().get(0).getDate().format(DATE_FMT)
                            + " → "
                            + stats.getRevenueSeries().get(stats.getRevenueSeries().size() - 1).getDate()
                                    .format(DATE_FMT));
        }
        r++;

        r = sectionHeader(sheet, s, r, "DOANH THU");
        r = kv(sheet, s, r, "Doanh thu kỳ này", stats.getRevenueInRange(), true);
        r = kv(sheet, s, r, "Doanh thu kỳ trước", stats.getRevenuePrevRange(), true);
        r = kv(sheet, s, r, "Thay đổi (%)",
                stats.getRevenueChangePercent() == null ? null : stats.getRevenueChangePercent(), false);
        r = kv(sheet, s, r, "Doanh thu hôm nay", stats.getRevenueToday(), true);
        r = kv(sheet, s, r, "Doanh thu hôm qua", stats.getRevenueYesterday(), true);
        r++;

        r = sectionHeader(sheet, s, r, "ĐƠN HÀNG");
        r = kv(sheet, s, r, "Tổng đơn", stats.getTotalOrderCount(), false);
        r = kv(sheet, s, r, "Chờ xác nhận (cần xử lý)", stats.getNeedsActionCount(), false);
        r = kv(sheet, s, r, "Đơn hôm nay", stats.getOrdersTodayCount(), false);
        for (var entry : stats.getOrdersByStatus().entrySet()) {
            r = kv(sheet, s, r, "  · " + statusLabel(entry.getKey()), entry.getValue(), false);
        }
        r++;

        r = sectionHeader(sheet, s, r, "SẢN PHẨM & DANH MỤC");
        r = kv(sheet, s, r, "Tổng sản phẩm", stats.getProductCount(), false);
        r = kv(sheet, s, r, "Đang bán", stats.getActiveProductCount(), false);
        r = kv(sheet, s, r, "Tạm ẩn", stats.getInactiveProductCount(), false);
        r = kv(sheet, s, r, "Sắp hết hàng (<5)", stats.getLowStockCount(), false);
        r = kv(sheet, s, r, "Tồn nguy hiểm (<2)", stats.getCriticalStockCount(), false);
        r = kv(sheet, s, r, "Tổng danh mục", stats.getCategoryCount(), false);
        r = kv(sheet, s, r, "Danh mục hoạt động", stats.getActiveCategoryCount(), false);
        r++;

        r = sectionHeader(sheet, s, r, "VOUCHER");
        r = kv(sheet, s, r, "Tổng voucher", stats.getVoucherCount(), false);
        r = kv(sheet, s, r, "Đang chạy", stats.getActiveVoucherCount(), false);
        r = kv(sheet, s, r, "Sắp hết hạn (7 ngày)", stats.getVoucherExpiringSoonCount(), false);

        // Người dùng: chỉ có khi người xuất có quyền READ_USER
        if (stats.getUserCount() != null) {
            r++;
            r = sectionHeader(sheet, s, r, "NGƯỜI DÙNG");
            r = kv(sheet, s, r, "Tổng người dùng", stats.getUserCount(), false);
            r = kv(sheet, s, r, "Đang hoạt động", stats.getActiveUserCount(), false);
            r = kv(sheet, s, r, "Khách mới (7 ngày)", stats.getNewCustomerCount(), false);
        }

        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 22 * 256);
    }

    // ===== Sheet 2: Doanh thu theo ngày =====

    private void writeRevenueByDay(Workbook wb, Styles s, DashboardStats stats) {
        Sheet sheet = wb.createSheet("Doanh thu theo ngày");
        String[] headers = { "Ngày", "Doanh thu", "Số đơn" };
        headerRow(sheet, s, headers);

        int r = 1;
        for (DashboardStats.DailyPoint p : stats.getRevenueSeries()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(p.getDate().format(DATE_FMT));
            money(row, 1, p.getRevenue(), s.money);
            row.createCell(2).setCellValue(p.getOrderCount());
        }
        widths(sheet, 16, 20, 12);
    }

    // ===== Sheet 3: Top sản phẩm =====

    private void writeTopProducts(Workbook wb, Styles s, DashboardStats stats) {
        Sheet sheet = wb.createSheet("Top sản phẩm");
        String[] headers = { "Hạng", "Mã", "Tên sản phẩm", "Đã bán", "Doanh thu" };
        headerRow(sheet, s, headers);

        int r = 1;
        int rank = 1;
        for (DashboardStats.TopProduct p : stats.getTopSellingProducts()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(rank++);
            row.createCell(1).setCellValue(p.getCode() == null ? "" : p.getCode());
            row.createCell(2).setCellValue(p.getName() == null ? "" : p.getName());
            row.createCell(3).setCellValue(p.getQuantitySold());
            money(row, 4, p.getRevenue(), s.money);
        }
        widths(sheet, 8, 16, 40, 12, 20);
    }

    // ===== Sheet 4: Khuyến mại =====

    private void writePromotion(Workbook wb, Styles s, DashboardStats stats) {
        Sheet sheet = wb.createSheet("Khuyến mại");
        DashboardStats.PromotionEffect pe = stats.getPromotionEffect();
        int r = 0;

        r = title(sheet, s, r, "HIỆU QUẢ KHUYẾN MẠI (đơn không huỷ)");
        r++;
        r = kv(sheet, s, r, "Tổng chi phí khuyến mại", pe.getTotalCost(), true);
        r = kv(sheet, s, r, "  · Promotion", pe.getPromotionCost(), true);
        r = kv(sheet, s, r, "  · Voucher", pe.getVoucherCost(), true);
        r = kv(sheet, s, r, "  · Flash Sale", pe.getFlashSaleCost(), true);
        r++;
        r = kv(sheet, s, r, "Tổng đơn trong kỳ", pe.getTotalOrders(), false);
        r = kv(sheet, s, r, "Đơn có khuyến mại", pe.getDiscountedOrders(), false);
        r = kv(sheet, s, r, "Tỉ lệ đơn có khuyến mại (%)", pe.getDiscountedRate(), false);
        r++;

        r = sectionHeader(sheet, s, r, "TOP CHƯƠNG TRÌNH KHUYẾN MẠI");
        headerRowAt(sheet, s, r++, new String[] { "Chương trình", "Số đơn", "Tiền đã giảm" });
        for (DashboardStats.ProgramEffect p : pe.getTopPromotions()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(p.getName() == null ? "" : p.getName());
            row.createCell(1).setCellValue(p.getOrderCount());
            money(row, 2, p.getDiscountAmount(), s.money);
        }
        if (pe.getTopPromotions().isEmpty()) {
            sheet.createRow(r++).createCell(0).setCellValue("(Không có chương trình nào áp trong kỳ)");
        }
        r++;

        r = sectionHeader(sheet, s, r, "TOP VOUCHER");
        headerRowAt(sheet, s, r++, new String[] { "Mã voucher", "Số đơn", "Tiền đã giảm" });
        for (DashboardStats.ProgramEffect v : pe.getTopVouchers()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(v.getName() == null ? "" : v.getName());
            row.createCell(1).setCellValue(v.getOrderCount());
            money(row, 2, v.getDiscountAmount(), s.money);
        }
        if (pe.getTopVouchers().isEmpty()) {
            sheet.createRow(r++).createCell(0).setCellValue("(Không có voucher nào dùng trong kỳ)");
        }
        r++;

        r = sectionHeader(sheet, s, r, "FLASH SALE");
        r = kv(sheet, s, r, "Số đơn có hàng flash", pe.getFlashSale().getOrderCount(), false);
        r = kv(sheet, s, r, "Số máy đã bán (flash)", pe.getFlashSale().getQuantitySold(), false);
        r = kv(sheet, s, r, "Tiền đã giảm (flash)", pe.getFlashSale().getDiscountAmount(), true);

        widths(sheet, 34, 14, 20);
    }

    // ===== Tiện ích ghi ô =====

    private int title(Sheet sheet, Styles s, int rowIdx, String text) {
        Row row = sheet.createRow(rowIdx);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(s.title);
        return rowIdx + 1;
    }

    private int sectionHeader(Sheet sheet, Styles s, int rowIdx, String text) {
        Row row = sheet.createRow(rowIdx);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(s.section);
        return rowIdx + 1;
    }

    private void label(Row row, int col, String text, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(text);
        cell.setCellStyle(style);
    }

    private int kv(Sheet sheet, Styles s, int rowIdx, String key, Number value, boolean asMoney) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(key);
        Cell cell = row.createCell(1);
        if (value == null) {
            cell.setCellValue("—");
        } else if (asMoney) {
            cell.setCellValue(value.doubleValue());
            cell.setCellStyle(s.money);
        } else {
            cell.setCellValue(value.doubleValue());
        }
        return rowIdx + 1;
    }

    private void money(Row row, int col, Long value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? 0 : value.doubleValue());
        cell.setCellStyle(style);
    }

    private void headerRow(Sheet sheet, Styles s, String[] headers) {
        headerRowAt(sheet, s, 0, headers);
    }

    private void headerRowAt(Sheet sheet, Styles s, int rowIdx, String[] headers) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(s.header);
        }
    }

    private void widths(Sheet sheet, int... cols) {
        for (int i = 0; i < cols.length; i++) {
            sheet.setColumnWidth(i, cols[i] * 256);
        }
    }

    private String rangeLabel(DashboardRange range) {
        return switch (range) {
            case TODAY -> "HÔM NAY";
            case LAST_7_DAYS -> "7 NGÀY";
            case LAST_30_DAYS -> "30 NGÀY";
            case THIS_MONTH -> "THÁNG NÀY";
        };
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "Chờ xác nhận";
            case "CONFIRMED" -> "Đã xác nhận";
            case "SHIPPING" -> "Đang giao";
            case "COMPLETED" -> "Hoàn thành";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }

    /** Bộ style dùng chung cho mọi sheet — tạo 1 lần cho cả workbook. */
    private static final class Styles {
        final CellStyle title;
        final CellStyle section;
        final CellStyle header;
        final CellStyle bold;
        final CellStyle money;

        Styles(Workbook wb) {
            title = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title.setFont(titleFont);

            section = wb.createCellStyle();
            Font sectionFont = wb.createFont();
            sectionFont.setBold(true);
            sectionFont.setColor(IndexedColors.WHITE.getIndex());
            section.setFont(sectionFont);
            section.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            section.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            header = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            header.setAlignment(HorizontalAlignment.LEFT);

            bold = wb.createCellStyle();
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            bold.setFont(boldFont);

            money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat(VND_FORMAT));
        }
    }
}
