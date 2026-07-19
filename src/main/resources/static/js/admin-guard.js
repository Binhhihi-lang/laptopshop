(function () {
    const token = localStorage.getItem('accessToken');
    const currentPath = window.location.pathname;

    //  HÀM GIẢI MÃ TOKEN
    function getPayload(tokenStr) {
        try {
            const base64Url = tokenStr.split('.')[1];
            const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
            return JSON.parse(decodeURIComponent(window.atob(base64).split('').map(function(c) {
                return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
            }).join('')));
        } catch (e) { return null; }
    }

    // Nếu người dùng cố tình truy cập trang LOGIN
    if (currentPath.includes("login.html")) {
        // Nếu họ vào trang login mà trong máy đang dính token USER cũ, 
        // xóa token cũ đó đi để họ sẵn sàng đăng nhập acc ADMIN mới.
        if (token) {
            const payload = getPayload(token);
            if (payload && payload.scope && !payload.scope.includes("ADMIN")) {
                localStorage.removeItem('accessToken'); // Xóa sạch dấu vết tài khoản USER cũ
                console.log("Đã xóa token USER cũ để chuẩn bị đăng nhập ADMIN.");
            }
        }
        return; // Dừng lại ở đây, cho phép tải trang login bình thường!
    }

    // Nếu truy cập các trang quản trị khác (dashboard, list, show...)
    if (!token) {
        window.location.href = "/admin/dashboard/login.html";
        return;
    }

    const payload = getPayload(token);
    if (!payload || !payload.scope || !payload.scope.includes("ADMIN")) {
        alert("Tài khoản của bạn không có quyền Admin!");
        // Thay vì dùng window.location.href, ta dùng .replace() 
        // để ghi đè lịch sử, tránh lỗi nhấn nút Back bị kẹt vòng lặp
        window.location.replace("/client/index.html"); 
    }
})();