package com.example.laptopshop.repository;

import org.springframework.data.repository.CrudRepository;

import com.example.laptopshop.domain.RevokeTicket;

// Redis Repository cho vé tạm "đăng xuất thiết bị cũ nhất". Vé được xóa ngay
// sau lần dùng đầu tiên (one-time-use) nên không cần thêm method query.
public interface RevokeTicketRepository extends CrudRepository<RevokeTicket, String> {
}
